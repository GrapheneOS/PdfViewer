import "./polyfill.js";
import {
    GlobalWorkerOptions,
    PasswordResponses,
    TextLayer,
    getDocument,
} from "pdfjs-dist";
import { getSimplifiedOutline } from "./outline.js";

GlobalWorkerOptions.workerSrc = "/viewer/js/worker.js";

// Continuous vertical paging.
//
// One wrapper per page is laid out in a vertical stack so the document scrolls
// naturally. Pages are rendered lazily (IntersectionObserver) only when near
// the viewport and cleared when far away, so memory stays bounded on large
// documents.
//
// CSP note (style-src 'self', no unsafe-inline): every dynamic style is set
// via an IDL property write (el.style.x = ...) or a CSS custom property
// (setProperty). We never set the style attribute directly, never assign cssText,
// never create a style element, and never use an inline style attribute in HTML
// — those are exactly what CSP blocks here. See check-csp.mjs which enforces it.

let pdfDoc = null;
let outlineAbort = new AbortController();

// pages[i] describes page (i+1):
//   { wrapper, canvas, textLayer, pdfPage, viewport, rendered, rendering, task }
const pages = [];

let zoomRatio = 0;            // free-zoom ratio (0 = derive from fit mode)
let orientationDegrees = 0;
let lastReportedPage = 0;     // last page pushed back to the ViewModel
let renderObserver = null;
let scrollTimer = null;
let pageBuildGeneration = 0;
let pendingScrollPage = 0;

const container = document.getElementById("container");
const pagesEl = document.getElementById("pages");

function readLayout() {
    const i = insets();
    return {
        insets: i,
        available: {
            width: document.body.clientWidth - i.left - i.right,
            height: document.body.clientHeight - i.top - i.bottom,
        },
        mode: fitMode(),
        minZoom: channel.getMinZoomRatio(),
        maxZoom: channel.getMaxZoomRatio(),
    };
}

function clampZoom(value, layout = null) {
    const min = layout ? layout.minZoom : channel.getMinZoomRatio();
    const max = layout ? layout.maxZoom : channel.getMaxZoomRatio();
    return Math.max(Math.min(value, max), min);
}

function fitMode() {
    // 0 = free zoom, 1 = fit page, 2 = fit width
    return channel.getPageFitMode();
}

function continuousMode() {
    return channel.getContinuousMode();
}

// In single-page mode only the current page's wrapper is displayed, so the
// document is exactly one page tall (no continuous flow) and navigation is via
// next/previous/jump/fling — the original behaviour. In continuous mode every
// wrapper is displayed and the document scrolls vertically through all pages.
function applyContinuousMode() {
    const cont = continuousMode();
    const current = channel.getPage();
    for (const p of pages) {
        if (!p) continue;
        const num = Number(p.wrapper.dataset.page);
        p.wrapper.style.display = (cont || num === current) ? "" : "none";
    }
}

function insets() {
    const ratio = globalThis.devicePixelRatio;
    return {
        ratio,
        left: (channel.getInsetLeft() / ratio) || 0,
        right: (channel.getInsetRight() / ratio) || 0,
        top: (channel.getInsetTop() / ratio) || 0,
        bottom: (channel.getInsetBottom() / ratio) || 0,
    };
}

function availSize(layout = null) {
    if (layout) return layout.available;
    const i = insets();
    return {
        width: document.body.clientWidth - i.left - i.right,
        height: document.body.clientHeight - i.top - i.bottom,
    };
}

function totalRotation(pdfPage) {
    return (orientationDegrees + pdfPage.rotate) % 360;
}

// Zoom ratio for a page under the active fit mode, or the free-zoom ratio.
function pageZoom(pdfPage, layout = null) {
    const m = layout ? layout.mode : fitMode();
    if (m !== 0 || zoomRatio === 0) {
        const vp1 = pdfPage.getViewport({scale: 1, rotation: totalRotation(pdfPage)});
        const a = availSize(layout);
        const wZoom = a.width / vp1.width;
        const hZoom = a.height / vp1.height;
        // fit width (2) fills width; fit page (1) and free-default both fit page.
        const z = (m === 2) ? wZoom : Math.min(wZoom, hZoom);
        return clampZoom(z, layout);
    }
    return clampZoom(zoomRatio, layout);
}

function pageViewport(pdfPage, layout = null) {
    return pdfPage.getViewport({
        scale: pageZoom(pdfPage, layout),
        rotation: totalRotation(pdfPage),
    });
}

// Keep content out from under the system bars by padding the scroll host.
function applyContainerInsets(layout = null) {
    const i = layout ? layout.insets : insets();
    container.style.paddingLeft = i.left + "px";
    container.style.paddingRight = i.right + "px";
    container.style.paddingTop = i.top + "px";
    container.style.paddingBottom = i.bottom + "px";
}

// (Re)size a wrapper to its viewport. CSP-safe: property writes only.
function sizeWrapper(p, layout = null) {
    const vp = pageViewport(p.pdfPage, layout);
    p.viewport = vp;
    const a = availSize(layout);
    p.wrapper.style.width = a.width + "px";
    p.wrapper.style.height = vp.height + "px";
    p.canvas.style.width = vp.width + "px";
    p.canvas.style.height = vp.height + "px";
}

// Overlay the text layer exactly on the (horizontally-centered) canvas.
function alignTextLayer(p, layout = null) {
    if (!p.viewport) return;
    const a = availSize(layout);
    const offsetX = (a.width - p.viewport.width) / 2;
    p.textLayer.style.translate = offsetX + "px 0px";
    p.textLayer.style.width = p.viewport.width + "px";
    p.textLayer.style.height = p.viewport.height + "px";
}

function clearPage(p) {
    if (p.task) {
        try {
            p.task.cancel();
        } catch {
            // cancellation may throw if the task already completed
        }
        p.task = null;
    }
    p.rendering = false;
    p.rendered = false;
    p.canvas.width = 0;     // free the backing store
    p.canvas.height = 0;
    p.textLayer.replaceChildren();
}

function renderPageContent(p, layout = null) {
    if (p.rendered) return;
    // Cancel any in-flight render so we restart at the current viewport: during
    // a multi-event pinch the viewport changes on every event, and an unchecked
    // in-flight task would otherwise complete with a stale viewport (stretched
    // bitmap, misaligned text) — see review P1.
    if (p.task) {
        try {
            p.task.cancel();
        } catch {
            // task already settled
        }
        p.task = null;
    }
    p.rendering = true;
    p.rendered = false;
    // Sync wrapper/canvas layout to the viewport we render at, so a rotation or
    // zoom change can never leave a stale size.
    sizeWrapper(p, layout);
    // Generation tag: callbacks from a cancelled/superseded render no-op.
    const gen = (p.renderGen = (p.renderGen || 0) + 1);

    const ratio = layout ? layout.insets.ratio : insets().ratio;
    const vp = p.viewport;
    const renderedZoom = pageZoom(p.pdfPage, layout);
    const renderPixels = (vp.width * ratio) * (vp.height * ratio);

    let renderVp = vp;
    const maxRenderPixels = channel.getMaxRenderPixels();
    if (renderPixels > maxRenderPixels) {
        const adjusted = Math.sqrt(maxRenderPixels / renderPixels);
        renderVp = p.pdfPage.getViewport({
            scale: renderedZoom * adjusted,
            rotation: totalRotation(p.pdfPage),
        });
    }

    p.canvas.width = Math.floor(renderVp.width * ratio);
    p.canvas.height = Math.floor(renderVp.height * ratio);
    const ctx = p.canvas.getContext("2d", {alpha: false});
    ctx.scale(ratio, ratio);

    const renderTask = p.pdfPage.render({canvasContext: ctx, viewport: renderVp});
    p.task = renderTask;
    renderTask.promise.then(() => {
        if (gen !== p.renderGen) return;
        p.zoom = renderedZoom;
        // pdf.js TextLayer reads --scale-factor from its container; set it before
        // construction so each page's text layer uses its own zoom.
        p.textLayer.style.setProperty("--scale-factor", renderedZoom.toString());
        const textLayer = new TextLayer({
            textContentSource: p.pdfPage.streamTextContent(),
            container: p.textLayer,
            viewport: vp,
        });
        p.task = {promise: textLayer.render(), cancel: () => textLayer.cancel()};
        return p.task.promise;
    }).then(() => {
        if (gen !== p.renderGen) return;
        p.rendered = true;
        p.rendering = false;
        alignTextLayer(p);
        p.textLayer.hidden = false;
    }).catch((err) => {
        if (gen !== p.renderGen) return;   // expected when cancelled by a newer render
        p.rendering = false;
        console.log("render error: " + err);
    });
}

// Render pages near the viewport, clear far ones.
function setupObserver() {
    if (renderObserver) renderObserver.disconnect();
    renderObserver = new IntersectionObserver((entries) => {
        for (const entry of entries) {
            const p = pages[Number(entry.target.dataset.page) - 1];
            if (!p) continue;
            if (entry.isIntersecting && p.wrapper.style.display !== "none") {
                renderPageContent(p);
            } else {
                clearPage(p);
            }
        }
    }, {root: null, rootMargin: "150% 0px", threshold: 0});
    for (const p of pages) {
        if (p) renderObserver.observe(p.wrapper);
    }
}

function relayoutAll() {
    const layout = readLayout();
    applyContainerInsets(layout);
    for (const p of pages) {
        if (!p) continue;
        sizeWrapper(p, layout);
        alignTextLayer(p, layout);
    }
    return layout;
}

// Re-render every currently-visible page (e.g. after zoom / rotation / resize).
function rerenderVisible(layout = null) {
    const currentLayout = layout || readLayout();
    for (const p of pages) {
        if (!p) continue;
        if (p.wrapper.style.display === "none") {
            clearPage(p);
            continue;
        }
        const rect = p.wrapper.getBoundingClientRect();
        const near = rect.bottom > -window.innerHeight * 1.5 &&
                     rect.top < window.innerHeight * 2.5;
        if (near) {
            if (p.rendered) clearPage(p);
            sizeWrapper(p, currentLayout);
            renderPageContent(p, currentLayout);
        } else {
            clearPage(p);
        }
    }
}

function mostVisiblePage() {
    let best = null;
    let bestArea = 0;
    const vh = window.innerHeight;
    for (const p of pages) {
        if (!p || p.wrapper.style.display === "none") continue;
        const rect = p.wrapper.getBoundingClientRect();
        const top = Math.max(rect.top, 0);
        const bottom = Math.min(rect.bottom, vh);
        const area = Math.max(0, bottom - top);
        if (area > bestArea) {
            bestArea = area;
            best = p;
        }
    }
    return best;
}

// Exposed for instrumentation tests: the canvas / text layer of the page
// currently most in view (continuous scroll has one per page).
globalThis.currentPageCanvas = function () {
    const p = mostVisiblePage();
    return p ? p.canvas : null;
};

globalThis.currentPageTextLayer = function () {
    const p = mostVisiblePage();
    return p ? p.textLayer : null;
};

// Report the most-visible page back to the ViewModel (drives the page indicator
// and next/previous enablement).
function updateCurrentPage() {
    const best = mostVisiblePage();
    if (!best) return;
    const num = Number(best.wrapper.dataset.page);
    if (pendingScrollPage !== 0) {
        if (!pages[pendingScrollPage - 1] || num !== pendingScrollPage) return;
        pendingScrollPage = 0;
    }
    const layout = readLayout();
    const m = layout.mode;
    if (m === 0 && zoomRatio !== 0) {
        // Free zoom: the ViewModel zoom (driven by the pinch handler) is
        // authoritative — reflect it on the container, never overwrite it.
        container.style.setProperty("--scale-factor", zoomRatio.toString());
    } else {
        // Fit mode: each page's fit zoom is authoritative — push it to the VM
        // so the page indicator / tests read the right value.
        // p.zoom records the last completed render and may still describe the
        // previous fit mode or rotation. Publish the current layout ratio.
        const z = pageZoom(best.pdfPage, layout);
        container.style.setProperty("--scale-factor", z.toString());
        channel.setZoomRatio(z);
    }
    if (num !== lastReportedPage) {
        lastReportedPage = num;
        channel.setCurrentPage(num);
    }
}

globalThis.scrollToPage = function (pageNumber) {
    if (!Number.isInteger(pageNumber) || pageNumber < 1 ||
            (pdfDoc && pageNumber > pdfDoc.numPages)) return;

    // Publish the requested page immediately, even if progressive page setup
    // has not reached it yet. Scroll tracking must not replace that request
    // while its wrapper is still being built.
    pendingScrollPage = pageNumber;
    lastReportedPage = pageNumber;
    channel.setCurrentPage(pageNumber);

    const p = pages[pageNumber - 1];
    if (!p) return;
    const layout = readLayout();
    sizeWrapper(p, layout);
    alignTextLayer(p, layout);
    if (!continuousMode()) {
        // single-page mode: show only the target page
        for (const q of pages) {
            if (!q) continue;
            q.wrapper.style.display = (q === p) ? "" : "none";
        }
        p.wrapper.scrollIntoView({block: "start"});
    } else {
        // continuous mode: centre the page in the visible band (below the app bar,
        // above the nav bar) so next/previous lands squarely on the page rather
        // than top-aligning it under the floating toolbar.
        const dpr = globalThis.devicePixelRatio;
        const visibleTop = channel.getInsetTop() / dpr;
        const visibleH = window.innerHeight - visibleTop - channel.getInsetBottom() / dpr;
        const rect = p.wrapper.getBoundingClientRect();
        const desiredTop = visibleTop + Math.max(0, (visibleH - rect.height) / 2);
        globalThis.scrollBy(0, rect.top - desiredTop);
    }
    const best = mostVisiblePage();
    if (best && Number(best.wrapper.dataset.page) === pageNumber) {
        pendingScrollPage = 0;
    }
};

// Driven from the Java side (former single-page render entry point).
//   zoom: 0 = full re-layout (fit/orientation/page jump), 1 = zoom end, 2 = zooming
globalThis.onRenderPage = function (zoom) {
    orientationDegrees = channel.getDocumentOrientationDegrees();

    if (zoom === 2 || zoom === 1) {
        // pinch: adopt the new free-zoom ratio and re-render visible pages while
        // keeping the focal point under the user's fingers (review P2 focal).
        const dpr = globalThis.devicePixelRatio;
        const best = mostVisiblePage();
        // Rendering is asynchronous and is commonly cancelled by the next
        // pinch event, so p.zoom can lag behind the ratio already requested.
        const prevZoom = zoomRatio || (best ? (best.zoom || pageZoom(best.pdfPage)) : 1);
        const newZoom = channel.getZoomRatio();
        zoomRatio = newZoom;
        container.style.setProperty("--scale-factor", newZoom.toString());

        // Focal point in document coordinates, captured before re-layout.
        const focusX = channel.getZoomFocusX() / dpr + globalThis.scrollX;
        const focusY = channel.getZoomFocusY() / dpr + globalThis.scrollY;

        // Placeholder geometry belongs to the requested zoom even when its
        // canvas is far enough away to remain unrendered.
        const layout = relayoutAll();
        rerenderVisible(layout);

        const translationFactor = (newZoom / prevZoom) - 1;
        globalThis.scrollBy(focusX * translationFactor, focusY * translationFactor);
        return;
    }

    // zoom === 0: a fit-mode / orientation / page change.
    if (fitMode() !== 0) {
        // a fit mode owns the zoom now; drop stale free-zoom state so that
        // re-entering Free zoom re-derives instead of reusing it (review P2).
        zoomRatio = 0;
    }
    // Read the Java-side target before geometry changes can make the scroll
    // handler report and overwrite a different most-visible page.
    const target = channel.getPage();
    const targetPage = pages[target - 1];
    const anchorTop = targetPage && targetPage.wrapper.style.display !== "none"
        ? targetPage.wrapper.getBoundingClientRect().top
        : null;
    const isPageNavigation = target !== lastReportedPage;

    if (!targetPage && isPageNavigation) {
        pendingScrollPage = target;
        lastReportedPage = target;
    }

    const layout = relayoutAll();
    if (targetPage && isPageNavigation) {
        // next/prev/jump-to-page from the menu — scroll the target into view.
        globalThis.scrollToPage(target);
    } else if (targetPage && anchorTop !== null) {
        // Preserve the target wrapper's viewport anchor when the heights of
        // earlier pages change due to fitting or rotation.
        const newTop = targetPage.wrapper.getBoundingClientRect().top;
        globalThis.scrollBy(0, newTop - anchorTop);
    }
    updateCurrentPage();
    rerenderVisible(layout);
};

globalThis.isTextSelected = function () {
    return globalThis.getSelection().toString() !== "";
};

globalThis.getDocumentOutline = function () {
    pdfDoc.getOutline().then(function (outline) {
        getSimplifiedOutline(outline, outlineAbort, pdfDoc).then(function (entries) {
            if (entries !== null) {
                channel.setDocumentOutline(JSON.stringify(entries));
            } else {
                channel.setDocumentOutline(null);
            }
        }).catch(function (error) {
            console.log("getSimplifiedOutline error: " + error);
        });
    }).catch(function (error) {
        console.log("pdfDoc.getOutline error: " + error);
    });
};

globalThis.abortDocumentOutline = function () {
    outlineAbort.abort();
    outlineAbort = new AbortController();
};

let isTextLayerVisible = false;
globalThis.toggleTextLayerVisibility = function () {
    const foreground = isTextLayerVisible ? "transparent" : "red";
    document.documentElement.style.setProperty("--text-layer-foreground", foreground);
    isTextLayerVisible = !isTextLayerVisible;
};

globalThis.getPageFitMode = function () {
    return channel.getPageFitMode();
};

globalThis.setContinuousMode = function () {
    // The ViewModel was already updated by the Java caller; reflect it in the DOM.
    const target = channel.getPage();
    applyContinuousMode();
    relayoutAll();
    // Showing or hiding preceding wrappers changes this page's document offset.
    globalThis.scrollToPage(target);
    rerenderVisible();
};

globalThis.loadDocument = function () {
    const pdfPassword = channel.getPassword();
    const loadingTask = getDocument({
        url: "https://localhost/placeholder.pdf",
        cMapUrl: "https://localhost/viewer/cmaps/",
        cMapPacked: true,
        password: pdfPassword,
        iccUrl: "https://localhost/viewer/iccs/",
        // This flag controls jpx/icc and PostScript Calculator function compiler at the same time.
        // See https://github.com/GrapheneOS/PdfViewer/issues/634#issuecomment-4356820142
        // for security justifications.
        //
        // Note that CSP is only applied to index.html, not workers where WASM runs
        useWasm: true,
        // If a font isn't embedded, the viewer falls back to default system fonts. On Android,
        // there often isn't a good substitution provided by the OS, so we need to bundle standard
        // fonts to improve the rendering of certain PDFs:
        //
        // https://github.com/mozilla/pdf.js/pull/18465
        // https://bugzilla.mozilla.org/show_bug.cgi?id=1882613
        useSystemFonts: false,
        standardFontDataUrl: "https://localhost/viewer/standard_fonts/",
        wasmUrl: "https://localhost/viewer/wasm/"
    });
    loadingTask.onPassword = (_, error) => {
        if (error === PasswordResponses.NEED_PASSWORD) {
            channel.showPasswordPrompt();
        } else if (error === PasswordResponses.INCORRECT_PASSWORD) {
            channel.invalidPassword();
        }
    };

    loadingTask.promise.then(function (newDoc) {
        channel.onLoaded();
        pdfDoc = newDoc;
        channel.setNumPages(pdfDoc.numPages);
        pdfDoc.getMetadata().then(function (data) {
            channel.setDocumentProperties(JSON.stringify(data.info));
        }).catch(function (error) {
            console.log("getMetadata error: " + error);
        });
        pdfDoc.getOutline().then(function (outline) {
            channel.setHasDocumentOutline(outline && outline.length > 0);
        }).catch(function (error) {
            console.log("getOutline error: " + error);
        });

        // Apply the saved document rotation before sizing any page, otherwise
        // every wrapper is built at rotation 0 and only nearby pages get
        // corrected later (review P2 rotation).
        orientationDegrees = channel.getDocumentOrientationDegrees();

        // Reset continuous-scroll state — loadDocument runs again when opening a
        // second document or re-entering a password, so the old pages must go.
        for (const old of pages) {
            if (old) clearPage(old);
        }
        if (renderObserver) {
            renderObserver.disconnect();
            renderObserver = null;
        }
        pages.length = 0;
        pagesEl.replaceChildren();
        zoomRatio = 0;
        lastReportedPage = 0;
        pendingScrollPage = 0;
        const buildGeneration = ++pageBuildGeneration;
        const startPage = channel.getPage() || 1;

        buildPages(startPage, buildGeneration).catch((error) => {
            console.error("buildPages error: " + error);
        });
    }, function (reason) {
        console.error(reason.name + ": " + reason.message);
        channel.onLoadError();
    });
};

function createPageEntry(pdfPage, pageNumber) {
    const wrapper = document.createElement("div");
    wrapper.className = "page-wrapper";
    wrapper.dataset.page = String(pageNumber);

    const canvas = document.createElement("canvas");
    const textLayer = document.createElement("div");
    textLayer.className = "textLayer";
    textLayer.hidden = true;

    wrapper.appendChild(canvas);
    wrapper.appendChild(textLayer);

    return {
        wrapper, canvas, textLayer, pdfPage,
        viewport: null, rendered: false, rendering: false, task: null,
    };
}

// Fetch page metadata in document order with concurrency bounded to one. The
// first readable page can be displayed immediately, and a failed later page
// does not reject initialization of the rest of the document.
async function buildPages(startPage, generation) {
    const documentToBuild = pdfDoc;
    const total = pdfDoc.numPages;
    let viewerReady = false;

    for (let i = 1; i <= total; i++) {
        if (generation !== pageBuildGeneration || documentToBuild !== pdfDoc) return;
        let pdfPage;
        try {
            pdfPage = await documentToBuild.getPage(i);
        } catch (error) {
            if (generation !== pageBuildGeneration || documentToBuild !== pdfDoc) return;
            console.error(`getPage(${i}) error: ${error}`);
            continue;
        }
        if (generation !== pageBuildGeneration || documentToBuild !== pdfDoc) return;

        const entry = createPageEntry(pdfPage, i);
        sizeWrapper(entry);
        pages[i - 1] = entry;
        pagesEl.appendChild(entry.wrapper);

        const requestedPage = channel.getPage() || startPage;
        entry.wrapper.style.display = (continuousMode() || i === requestedPage) ? "" : "none";

        if (!viewerReady) {
            applyContainerInsets();
            setupObserver();
            viewerReady = true;
            // Continuous mode can show useful content while a later restored
            // target is still being initialized.
            if (i !== requestedPage) rerenderVisible();
        } else if (renderObserver) {
            renderObserver.observe(entry.wrapper);
        }

        if (i === requestedPage) {
            globalThis.scrollToPage(requestedPage);
            updateCurrentPage();
            rerenderVisible();
        }
    }

    const requestedPage = channel.getPage() || startPage;
    if (viewerReady && !pages[requestedPage - 1]) {
        const fallback = pages.find((page) => page);
        if (fallback) {
            globalThis.scrollToPage(Number(fallback.wrapper.dataset.page));
            updateCurrentPage();
            rerenderVisible();
        }
    }
}

// Scroll → track current page (throttled); resize → relayout.
globalThis.onscroll = function () {
    if (scrollTimer) return;
    scrollTimer = setTimeout(() => {
        scrollTimer = null;
        updateCurrentPage();
    }, 150);
};

globalThis.onresize = function () {
    relayoutAll();
    rerenderVisible();
};
