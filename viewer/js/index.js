import "./polyfill.js";
import {
    GlobalWorkerOptions,
    PasswordResponses,
    TextLayer,
    getDocument,
} from "pdfjs-dist";
import { getSimplifiedOutline } from "./outline.js";
import { pageTextItems } from "./search.js";

GlobalWorkerOptions.workerSrc = "/viewer/js/worker.js";

let pdfDoc = null;
let outlineAbort = new AbortController();
let pageRendering = false;
let renderPending = false;
let renderPendingZoom = 0;
const canvas = document.getElementById("content");
const container = document.getElementById("container");
let orientationDegrees = 0;
let zoomRatio = 1;
let textLayerDiv = document.getElementById("text");
let task = null;

let newPageNumber = 0;
let newZoomRatio = 1;
let useRender;

const cache = [];
const maxCached = 6;

let isTextLayerVisible = false;
let userZoomed = false;

// The page whose text layer is actually in the DOM, and its text item divs. Not the same as
// channel.getPage(), which is the page that has been *requested*: jumpToPage updates the model
// before asking for a render, so painting against it would put one page's offsets on another
// page's divs.
let displayedPage = 0;
let displayedDivs = [];
// { page, groups, active } as handed over by setSearchHighlights, or null.
let searchState = null;
let pendingScroll = false;
let extractEpoch = 0;
// An extraction asked for before the document finished loading, replayed once it has. A
// configuration change or a WebView crash recreates the WebView while the native side still
// believes the document is loaded, so the request arrives before pdfDoc exists.
let pendingExtract = null;

const findHighlight = new Highlight();
const activeHighlight = new Highlight();
activeHighlight.priority = 1;
CSS.highlights.set("pdf-find", findHighlight);
CSS.highlights.set("pdf-find-active", activeHighlight);

function scrollToRange(range) {
    const rect = range.getBoundingClientRect();
    // A collapsed range sits at the page origin and is not worth scrolling to.
    if (rect.width === 0 && rect.height === 0) {
        return;
    }
    const ratio = globalThis.devicePixelRatio;
    const top = channel.getInsetTop() / ratio;
    const width = globalThis.innerWidth;
    // Edge to edge means the WebView keeps its full height when the keyboard opens, so the
    // usable band has to be narrowed by hand or matches scroll behind the IME.
    const height = globalThis.innerHeight - channel.getInsetIme() / ratio;
    // Already fully visible in that band: leave the viewport alone, otherwise stepping between
    // two matches on the same line re-centres the page on every tap.
    if (rect.top >= top && rect.bottom <= height && rect.left >= 0 && rect.right <= width) {
        return;
    }
    scrollBy({
        left: rect.left - width / 2,
        top: rect.top - (top + height) / 2,
        behavior: "instant"
    });
}

// The only place highlights are painted. Called whenever either half of the pair
// (search results, displayed page) changes.
function applyHighlights() {
    findHighlight.clear();
    activeHighlight.clear();
    // Always clear before bailing out: the page cache re-attaches the *same* nodes, so ranges
    // left in a Highlight would light up again when a cached text layer is swapped back in.
    if (searchState === null || searchState.page !== displayedPage) {
        return;
    }
    let activeRange = null;
    for (let match = 0; match < searchState.groups.length; match++) {
        for (const piece of searchState.groups[match]) {
            // Items with an empty string get a div that is never appended, so it has no text
            // node; likewise anything past pdf.js's MAX_TEXT_DIVS_TO_RENDER cutoff.
            const node = displayedDivs[piece[0]]?.firstChild;
            if (!node) {
                continue;
            }
            const start = Math.min(piece[1], node.length);
            const end = Math.min(piece[1] + piece[2], node.length);
            // A match that begins on a synthetic end-of-line separator clamps to an empty range,
            // which paints nothing and whose rect is at the page origin.
            if (start >= end) {
                continue;
            }
            const range = document.createRange();
            range.setStart(node, start);
            range.setEnd(node, end);
            findHighlight.add(range);
            if (match === searchState.active) {
                activeHighlight.add(range);
                activeRange ??= range;
            }
        }
    }
    if (activeRange !== null && pendingScroll) {
        pendingScroll = false;
        scrollToRange(activeRange);
    }
}

function maybeRenderNextPage() {
    if (renderPending) {
        pageRendering = false;
        renderPending = false;
        renderPage(channel.getPage(), renderPendingZoom, false);
        return true;
    }
    return false;
}

function handleRenderingError(error) {
    console.log("rendering error: " + error);

    pageRendering = false;
    maybeRenderNextPage();
}

function doPrerender(pageNumber, prerenderTrigger) {
    if (useRender) {
        if (pageNumber + 1 <= pdfDoc.numPages) {
            renderPage(pageNumber + 1, false, true, pageNumber);
        } else if (pageNumber - 1 > 0) {
            renderPage(pageNumber - 1, false, true, pageNumber);
        }
    } else if (pageNumber === prerenderTrigger + 1) {
        if (prerenderTrigger - 1 > 0) {
            renderPage(prerenderTrigger - 1, false, true, prerenderTrigger);
        }
    }
}

function display(newCanvas, zoom) {
    canvas.height = newCanvas.height;
    canvas.width = newCanvas.width;
    canvas.style.height = newCanvas.style.height;
    canvas.style.width = newCanvas.style.width;
    canvas.getContext("2d", { alpha: false }).drawImage(newCanvas, 0, 0);
    if (!zoom) {
        scrollTo(0, 0);
    }
}

function setLayerTransform(pageWidth, pageHeight, layerDiv) {
    const cs = globalThis.getComputedStyle(canvas);
    const insetLeft = parseFloat(cs.paddingLeft) || 0;
    const insetTop = parseFloat(cs.paddingTop) || 0;
    const insetRight = parseFloat(cs.paddingRight) || 0;
    const insetBottom = parseFloat(cs.paddingBottom) || 0;

    const isOverflownY = canvas.clientHeight > document.body.clientHeight;
    const isOverflownX = canvas.clientWidth > document.body.clientWidth;
    // Translate the text layer to stay aligned with the rendered page including canvas insets and
    // grid centering effects.
    const translate = {
        X: isOverflownX
            ? insetLeft - (document.body.clientWidth - pageWidth) / 2
            : (insetLeft - insetRight) / 2,
        Y: isOverflownY
            ? insetTop - (document.body.clientHeight - pageHeight) / 2
            : (insetTop - insetBottom) / 2
    };
    layerDiv.style.translate = `${translate.X}px ${translate.Y}px`;
}

function getDefaultZoomRatio(page, degrees) {
    const totalRotation = (degrees + page.rotate) % 360;
    const viewport = page.getViewport({scale: 1, rotation: totalRotation});
    const widthZoomRatio = document.body.clientWidth / viewport.width;
    const heightZoomRatio = document.body.clientHeight / viewport.height;
    return Math.max(Math.min(widthZoomRatio, heightZoomRatio, channel.getMaxZoomRatio()), channel.getMinZoomRatio());
}

function renderPage(pageNumber, zoom, prerender, prerenderTrigger = 0) {
    pageRendering = true;
    useRender = !prerender;

    newPageNumber = pageNumber;
    newZoomRatio = channel.getZoomRatio();
    orientationDegrees = channel.getDocumentOrientationDegrees();
    console.log("page: " + pageNumber + ", zoom: " + newZoomRatio +
                ", orientationDegrees: " + orientationDegrees + ", prerender: " + prerender);
    for (let i = 0; !zoom && i < cache.length; i++) {
        const cached = cache[i];
        if (cached.pageNumber === pageNumber && cached.zoomRatio === newZoomRatio &&
                cached.orientationDegrees === orientationDegrees) {
            if (useRender) {
                cache.splice(i, 1);
                cache.push(cached);

                display(cached.canvas, zoom);
                zoomRatio = newZoomRatio;

                textLayerDiv.replaceWith(cached.textLayerDiv);
                textLayerDiv = cached.textLayerDiv;
                setLayerTransform(cached.pageWidth, cached.pageHeight, textLayerDiv);
                container.style.setProperty("--scale-factor", newZoomRatio.toString());
                textLayerDiv.hidden = false;

                displayedPage = pageNumber;
                displayedDivs = cached.textDivs;
                applyHighlights();
            }

            pageRendering = false;
            doPrerender(pageNumber, prerenderTrigger);
            return;
        }
    }

    pdfDoc.getPage(pageNumber).then(function(page) {
        if (maybeRenderNextPage()) {
            return;
        }

        const defaultZoomRatio = getDefaultZoomRatio(page, orientationDegrees);

        if (newZoomRatio === 0) {
            zoomRatio = defaultZoomRatio;
            newZoomRatio = defaultZoomRatio;
            channel.setZoomRatio(defaultZoomRatio);
        }

        const totalRotation = (orientationDegrees + page.rotate) % 360;
        const viewport = page.getViewport({scale: newZoomRatio, rotation: totalRotation});

        const scaleFactor = newZoomRatio / zoomRatio;
        const ratio = globalThis.devicePixelRatio;

        if (useRender) {
            if (newZoomRatio !== zoomRatio) {
                canvas.style.height = viewport.height + "px";
                canvas.style.width = viewport.width + "px";
            }
            zoomRatio = newZoomRatio;
        }

        if (zoom === 1 || zoom === 2) {
            // Focus point in CSS px, in viewport coordinates.
            const focusX = zoom === 2
                ? channel.getZoomFocusX() / ratio
                : globalThis.innerWidth / 2;
            const focusY = zoom === 2
                ? channel.getZoomFocusY() / ratio
                : globalThis.innerHeight / 2;

            // focus relative to page origin, rather than screen origin
            const globalFocusX = focusX + globalThis.scrollX;
            const globalFocusY = focusY + globalThis.scrollY;

            const translationFactor = scaleFactor - 1;
            scrollBy(globalFocusX * translationFactor, globalFocusY * translationFactor);

            if (zoom === 2) {
                textLayerDiv.hidden = true;
                pageRendering = false;
                return;
            }
        }

        const resolutionY = viewport.height * ratio;
        const resolutionX = viewport.width * ratio;
        const renderPixels = resolutionY * resolutionX;

        let newViewport = viewport;
        const maxRenderPixels = channel.getMaxRenderPixels();
        if (renderPixels > maxRenderPixels) {
            console.log(`resolution ${renderPixels} exceeds maximum allowed ${maxRenderPixels}`);
            const adjustedScale = Math.sqrt(maxRenderPixels / renderPixels);
            newViewport = page.getViewport({
                scale: newZoomRatio * adjustedScale,
                rotation: totalRotation
            });
        }

        const newCanvas = document.createElement("canvas");
        newCanvas.height = newViewport.height * ratio;
        newCanvas.width = newViewport.width * ratio;
        // use original viewport height for CSS zoom
        newCanvas.style.height = viewport.height + "px";
        newCanvas.style.width = viewport.width + "px";
        const newContext = newCanvas.getContext("2d", { alpha: false });
        newContext.scale(ratio, ratio);

        // Add padding to the canvas to allow the page to be scrolled bellow/above any
        // system/app ui that might be visible.
        canvas.style.paddingLeft = (channel.getInsetLeft() / ratio) + "px";
        canvas.style.paddingTop = (channel.getInsetTop() / ratio) + "px";
        canvas.style.paddingRight = (channel.getInsetRight() / ratio) + "px";
        canvas.style.paddingBottom = (channel.getInsetBottom() / ratio) + "px";

        task = page.render({
            canvasContext: newContext,
            viewport: newViewport
        });

        task.promise.then(function() {
            task = null;

            let rendered = false;
            function render() {
                if (!useRender || rendered) {
                    return;
                }
                display(newCanvas, zoom);
                rendered = true;
            }
            render();

            const newTextLayerDiv = textLayerDiv.cloneNode();
            const textLayer = new TextLayer({
                textContentSource: page.streamTextContent(),
                container: newTextLayerDiv,
                viewport: viewport
            });
            task = {
                promise: textLayer.render(),
                cancel: () => textLayer.cancel()
            };
            task.promise.then(function() {
                task = null;

                render();

                setLayerTransform(viewport.width, viewport.height, newTextLayerDiv);
                if (useRender) {
                    textLayerDiv.replaceWith(newTextLayerDiv);
                    textLayerDiv = newTextLayerDiv;
                    container.style.setProperty("--scale-factor", newZoomRatio.toString());
                    textLayerDiv.hidden = false;

                    displayedPage = pageNumber;
                    displayedDivs = textLayer.textDivs;
                    applyHighlights();
                }

                if (cache.length === maxCached) {
                    cache.shift();
                }
                cache.push({
                    pageNumber: pageNumber,
                    zoomRatio: newZoomRatio,
                    orientationDegrees: orientationDegrees,
                    canvas: newCanvas,
                    textLayerDiv: newTextLayerDiv,
                    textDivs: textLayer.textDivs,
                    pageWidth: viewport.width,
                    pageHeight: viewport.height
                });

                pageRendering = false;
                doPrerender(pageNumber, prerenderTrigger);
            }).catch(handleRenderingError);
        }).catch(handleRenderingError);
    });
}

globalThis.onRenderPage = function (zoom) {
    if (zoom === 1 || zoom === 2) {
        userZoomed = true;
    }

    if (pageRendering) {
        if (newPageNumber === channel.getPage() && newZoomRatio === channel.getZoomRatio() &&
                orientationDegrees === channel.getDocumentOrientationDegrees()) {
            useRender = true;
            return;
        }

        renderPending = true;
        renderPendingZoom = zoom;
        if (task !== null) {
            task.cancel();
            task = null;
        }
    } else {
        renderPage(channel.getPage(), zoom, false);
    }
};

globalThis.isTextSelected = function () {
    return globalThis.getSelection().toString() !== "";
};

globalThis.getDocumentOutline = function () {
    pdfDoc.getOutline().then(function(outline) {
        getSimplifiedOutline(outline, outlineAbort, pdfDoc).then(function(outlineEntries) {
            if (outlineEntries !== null) {
                channel.setDocumentOutline(JSON.stringify(outlineEntries));
            } else {
                channel.setDocumentOutline(null);
            }
        }).catch(function(error) {
            console.log("getSimplifiedOutline error: " + error);
        });
    }).catch(function(error) {
        console.log("pdfDoc.getOutline error: " + error);
    });
};

globalThis.abortDocumentOutline = function () {
    outlineAbort.abort();
    outlineAbort = new AbortController();
};

globalThis.toggleTextLayerVisibility = function () {
    let textLayerForeground = "red";
    if (isTextLayerVisible) {
        textLayerForeground = "transparent";
    }
    document.documentElement.style.setProperty("--text-layer-foreground", textLayerForeground);
    isTextLayerVisible = !isTextLayerVisible;
};

// page 0 clears. May arrive before, during or after the target page's render: if the page is
// not displayed yet applyHighlights() clears and returns, and the render that follows repaints
// and performs the deferred scroll.
globalThis.setSearchHighlights = function (page, groups, active) {
    searchState = page === 0 ? null : { page: page, groups: groups, active: active };
    pendingScroll = active >= 0;
    applyHighlights();
};

// Streams every page's text to the native side, starting at the page being viewed so the first
// results arrive immediately, then wrapping. Runs once per document; the native side caches.
globalThis.extractText = async function (startPage, generation) {
    if (pdfDoc === null) {
        pendingExtract = { startPage: startPage, generation: generation };
        return;
    }
    const epoch = ++extractEpoch;
    const total = pdfDoc.numPages;
    for (let i = 0; i < total; i++) {
        const pageNumber = ((startPage - 1 + i) % total) + 1;
        // Page turns beat the scan: the pdf.js worker is single threaded. Bounded so a stuck
        // flag cannot wedge extraction.
        for (let wait = 0; pageRendering && wait < 64; wait++) {
            await new Promise((resolve) => setTimeout(resolve, 16));
        }
        if (epoch !== extractEpoch) {
            return;
        }
        let items = [];
        try {
            const page = await pdfDoc.getPage(pageNumber);
            ({ items } = await page.getTextContent());
            if (pageNumber !== displayedPage) {
                page.cleanup();
            }
        } catch (error) {
            console.log("getTextContent error: " + error);
        }
        // A false return means the native index is full and there is nothing left to send.
        if (epoch !== extractEpoch ||
                !channel.setPageText(
                    pageNumber, JSON.stringify(pageTextItems(items)), generation)) {
            return;
        }
    }
};

globalThis.loadDocument = function () {
    extractEpoch++;
    userZoomed = false;
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
        if (pendingExtract !== null) {
            const resume = pendingExtract;
            pendingExtract = null;
            // A stale generation is rejected by the first setPageText, which ends the loop.
            globalThis.extractText(resume.startPage, resume.generation);
        }
        pdfDoc.getMetadata().then(function (data) {
            channel.setDocumentProperties(JSON.stringify(data.info));
        }).catch(function (error) {
            console.log("getMetadata error: " + error);
        });
        pdfDoc.getOutline().then(function(outline) {
            channel.setHasDocumentOutline(outline && outline.length > 0);
        }).catch(function(error) {
            console.log("getOutline error: " + error);
        });
        renderPage(channel.getPage(), false, false);
    }, function (reason) {
        console.error(reason.name + ": " + reason.message);
        channel.onLoadError();
    });
};

globalThis.onresize = () => {
    setLayerTransform(canvas.clientWidth, canvas.clientHeight, textLayerDiv);
    if (pdfDoc !== null && !userZoomed) {
        const pageNumber = channel.getPage();
        pdfDoc.getPage(pageNumber).then(function(page) {
            const degrees = channel.getDocumentOrientationDegrees();
            const newDefaultZoom = getDefaultZoomRatio(page, degrees);
            channel.setZoomRatio(newDefaultZoom);
            globalThis.onRenderPage(0);
        }).catch(function(err) {
            console.log("onresize error: " + err);
        });
    }
};
