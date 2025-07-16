import {
    GlobalWorkerOptions,
    PasswordResponses,
    TextLayer,
    getDocument,
} from "pdfjs-dist";

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

// Vertical scrolling mode variables
let isVerticalScrollMode = false;
let allPagesContainer = null;
let pageElements = [];
let currentPageInView = 1;
let scrollThrottledHandler = null;

function maybeRenderNextPage() {
    if (renderPending) {
        pageRendering = false;
        renderPending = false;
        if (isVerticalScrollMode) {
            renderAllPages(renderPendingZoom);
        } else {
            renderPage(channel.getPage(), renderPendingZoom, false);
        }
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
    const translate = {
        X: Math.max(0, pageWidth - document.body.clientWidth) / 2,
        Y: Math.max(0, pageHeight - document.body.clientHeight) / 2
    };
    layerDiv.style.translate = `${translate.X}px ${translate.Y}px`;
}

function getDefaultZoomRatio(page, orientationDegrees) {
    const totalRotation = (orientationDegrees + page.rotate) % 360;
    const viewport = page.getViewport({scale: 1, rotation: totalRotation});
    const widthZoomRatio = document.body.clientWidth / viewport.width;
    const heightZoomRatio = document.body.clientHeight / viewport.height;
    return Math.max(Math.min(widthZoomRatio, heightZoomRatio, channel.getMaxZoomRatio()), channel.getMinZoomRatio());
}

// Vertical scrolling mode functions
function createAllPagesContainer() {
    if (allPagesContainer) {
        allPagesContainer.remove();
    }
    
    allPagesContainer = document.createElement("div");
    allPagesContainer.id = "all-pages-container";
    allPagesContainer.style.cssText = `
        display: flex;
        flex-direction: column;
        align-items: center;
        gap: 20px;
        padding: 20px;
        width: 100%;
        min-height: 100vh;
    `;
    
    container.appendChild(allPagesContainer);
    
    // Hide the single page canvas and text layer
    canvas.style.display = "none";
    textLayerDiv.style.display = "none";
}

function createPageElement(pageNumber) {
    const pageContainer = document.createElement("div");
    pageContainer.className = "page-container";
    pageContainer.dataset.pageNumber = pageNumber;
    pageContainer.style.cssText = `
        position: relative;
        margin-bottom: 20px;
        box-shadow: 0 4px 8px rgba(0,0,0,0.3);
        background: white;
    `;
    
    const pageCanvas = document.createElement("canvas");
    pageCanvas.className = "page-canvas";
    pageCanvas.style.cssText = `
        display: block;
        max-width: 100%;
        height: auto;
    `;
    
    const pageTextLayer = document.createElement("div");
    pageTextLayer.className = "textLayer page-text-layer";
    pageTextLayer.style.cssText = `
        position: absolute;
        top: 0;
        left: 0;
        right: 0;
        bottom: 0;
        overflow: hidden;
        opacity: 0.2;
        line-height: 1.0;
        --scale-factor: 1;
        --user-unit: 1;
        --total-scale-factor: calc(var(--scale-factor) * var(--user-unit));
        --scale-round-x: 1px;
        --scale-round-y: 1px;
    `;
    
    pageContainer.appendChild(pageCanvas);
    pageContainer.appendChild(pageTextLayer);
    
    return { pageContainer, pageCanvas, pageTextLayer };
}

async function renderAllPages(zoom = false) {
    if (!pdfDoc || !isVerticalScrollMode) return;
    
    pageRendering = true;
    
    createAllPagesContainer();
    pageElements = [];
    
    const numPages = pdfDoc.numPages;
    const containerWidth = container.clientWidth - 40; // Account for padding
    
    // Calculate appropriate zoom for vertical layout
    const firstPage = await pdfDoc.getPage(1);
    const defaultZoom = getDefaultZoomRatio(firstPage, orientationDegrees);
    const verticalZoom = Math.min(defaultZoom * 0.8, containerWidth / firstPage.getViewport({scale: 1}).width);
    
    if (!zoom) {
        zoomRatio = verticalZoom;
        newZoomRatio = verticalZoom;
        channel.setZoomRatio(verticalZoom);
    } else {
        zoomRatio = channel.getZoomRatio();
        newZoomRatio = zoomRatio;
    }
    
    for (let pageNum = 1; pageNum <= numPages; pageNum++) {
        try {
            const page = await pdfDoc.getPage(pageNum);
            const { pageContainer, pageCanvas, pageTextLayer } = createPageElement(pageNum);
            
            allPagesContainer.appendChild(pageContainer);
            pageElements.push({ pageContainer, pageCanvas, pageTextLayer, pageNum });
            
            await renderSinglePageInVerticalMode(page, pageCanvas, pageTextLayer);
            
        } catch (error) {
            console.error(`Error rendering page ${pageNum}:`, error);
        }
    }
    
    pageRendering = false;
    setupScrollListener();
    updateCurrentPageFromScroll();
}

async function renderSinglePageInVerticalMode(page, pageCanvas, pageTextLayer) {
    const totalRotation = (orientationDegrees + page.rotate) % 360;
    const viewport = page.getViewport({scale: newZoomRatio, rotation: totalRotation});
    
    const ratio = globalThis.devicePixelRatio;
    
    pageCanvas.height = viewport.height * ratio;
    pageCanvas.width = viewport.width * ratio;
    pageCanvas.style.height = viewport.height + "px";
    pageCanvas.style.width = viewport.width + "px";
    
    const context = pageCanvas.getContext("2d", { alpha: false });
    context.scale(ratio, ratio);
    
    // Render the page
    await page.render({
        canvasContext: context,
        viewport: viewport
    }).promise;
    
    // Render text layer
    const textLayer = new TextLayer({
        textContentSource: page.streamTextContent(),
        container: pageTextLayer,
        viewport: viewport
    });
    
    await textLayer.render();
    
    // Set text layer transform
    pageTextLayer.style.transform = `scale(${newZoomRatio})`;
    pageTextLayer.style.transformOrigin = "0 0";
}

function setupScrollListener() {
    if (!isVerticalScrollMode) return;
    
    // Remove existing listener first
    if (scrollThrottledHandler) {
        window.removeEventListener("scroll", scrollThrottledHandler);
    }
    
    scrollThrottledHandler = throttle(() => {
        updateCurrentPageFromScroll();
    }, 100);
    
    window.addEventListener("scroll", scrollThrottledHandler);
}

function updateCurrentPageFromScroll() {
    if (!isVerticalScrollMode || pageElements.length === 0) return;
    
    const scrollY = window.scrollY;
    const windowHeight = window.innerHeight;
    const scrollCenter = scrollY + windowHeight / 2;
    
    let closestPage = 1;
    let closestDistance = Infinity;
    
    pageElements.forEach(({ pageContainer, pageNum }) => {
        const rect = pageContainer.getBoundingClientRect();
        const pageCenter = rect.top + scrollY + rect.height / 2;
        const distance = Math.abs(scrollCenter - pageCenter);
        
        if (distance < closestDistance) {
            closestDistance = distance;
            closestPage = pageNum;
        }
    });
    
    if (closestPage !== currentPageInView) {
        currentPageInView = closestPage;
        // Notify Android about page change
        if (typeof channel !== "undefined" && channel.onPageChanged) {
            try {
                channel.onPageChanged(currentPageInView);
            } catch (e) {
                console.log("Error calling onPageChanged:", e);
            }
        }
    }
}

function scrollToPage(pageNumber) {
    if (!isVerticalScrollMode || !pageElements.length) return;
    
    const pageElement = pageElements.find(p => p.pageNum === pageNumber);
    if (pageElement) {
        pageElement.pageContainer.scrollIntoView({
            behavior: "smooth",
            block: "center"
        });
    }
}

function switchToVerticalMode() {
    isVerticalScrollMode = true;
    renderAllPages();
}

function switchToPageMode() {
    isVerticalScrollMode = false;
    
    // Clean up vertical mode elements
    if (allPagesContainer) {
        allPagesContainer.remove();
        allPagesContainer = null;
    }
    
    pageElements = [];
    
    // Show single page elements
    canvas.style.display = "inline-block";
    textLayerDiv.style.display = "block";
    
    // Remove scroll listener
    if (scrollThrottledHandler) {
        window.removeEventListener("scroll", scrollThrottledHandler);
        scrollThrottledHandler = null;
    }
    
    // Render current page
    renderPage(channel.getPage(), false, false);
}

function throttle(func, limit) {
    let inThrottle;
    return function() {
        const args = arguments;
        const context = this;
        if (!inThrottle) {
            func.apply(context, args);
            inThrottle = true;
            setTimeout(() => inThrottle = false, limit);
        }
    };
}

/**
 * Does BFS traversal of all of the nodes in the outline tree to convert the tree so that the
 * nodes are of a simpler form. The simple outline nodes have the following structure:
 *
 * ```
 *  {
 *      t: String, // title
 *      p: int (-1 means unknown), // pageNumber
 *      c: Array of simple outline nodes, // children
 *  }
 * ```
 *
 * @param {Array} pdfJsOutline The root node of the outline tree as obtained by
 * pdfDoc.getOutline. This is assumed to be an ordered tree.
 *
 * @return {Promise} A promise that is resolved with an {Array} that contains
 * all the top-level nodes of the outline in simplified form
 */
async function getSimplifiedOutline(pdfJsOutline, abortController) {
    if (pdfJsOutline === undefined || pdfJsOutline === null || pdfJsOutline.length === 0) {
        return null;
    }

    const pageNumberPromises = [];
    const topLevelEntries = [];

    // Each item in this queue represents a PDF.js outline node with a
    // reference to an array of its children in the simplified node form.
    const outlineQueue = [{
        pdfJsChildren: pdfJsOutline,
        // No parents for at top/root, so it starts out as null for them.
        parentSimpleChildrenArray: null,
    }];

    while (outlineQueue.length > 0) {
        abortController.signal.throwIfAborted();

        const currentOutlinePayload = outlineQueue.shift();
        const parentChildrenArray = currentOutlinePayload.parentSimpleChildrenArray;
        const currentPdfJsChildren = currentOutlinePayload.pdfJsChildren;
        for (const pdfJsChild of currentPdfJsChildren) {
            abortController.signal.throwIfAborted();

            const simpleChild = {
                t: pdfJsChild.title,
                // The pageNumber is resolved later.
                p: -1,
                c: [],
            };

            if (parentChildrenArray !== null) {
                parentChildrenArray.push(simpleChild);
            } else {
                topLevelEntries.push(simpleChild);
            }

            if (pdfJsChild.items.length > 0) {
                outlineQueue.push({
                    pdfJsChildren: pdfJsChild.items,
                    parentSimpleChildrenArray: simpleChild.c,
                });
            }

            // Resolve the page number. Note that dest options can be a string
            // or an object according to the the PDF spec.
            const dest = (typeof pdfJsChild.dest === "string")
                ? await pdfDoc.getDestination(pdfJsChild.dest) : pdfJsChild.dest;
            if (Array.isArray(dest)) {
                const destRef = dest[0];
                if (typeof destRef === "object") {
                    pageNumberPromises.push(
                        pdfDoc.getPageIndex(destRef).then(function(index) {
                            simpleChild.p = parseInt(index) + 1;
                        }).catch(function(error) {
                            console.log("pdfDoc.getPageIndex error: " + error);
                            simpleChild.p = -1;
                        })
                    );
                } else {
                    simpleChild.p = Number.isInteger(destRef) ? destRef + 1 : -1;
                }
            }
        }
    }

    await Promise.all(pageNumberPromises);

    return topLevelEntries;
}

function renderPage(pageNumber, zoom, prerender, prerenderTrigger = 0) {
    pageRendering = true;
    useRender = !prerender;

    newPageNumber = pageNumber;
    newZoomRatio = channel.getZoomRatio();
    orientationDegrees = channel.getDocumentOrientationDegrees();
    console.log("page: " + pageNumber + ", zoom: " + newZoomRatio +
                ", orientationDegrees: " + orientationDegrees + ", prerender: " + prerender);
    for (let i = 0; i < cache.length; i++) {
        const cached = cache[i];
        if (cached.pageNumber === pageNumber && cached.zoomRatio === newZoomRatio &&
                cached.orientationDegrees === orientationDegrees) {
            if (useRender) {
                cache.splice(i, 1);
                cache.push(cached);

                display(cached.canvas, zoom);

                textLayerDiv.replaceWith(cached.textLayerDiv);
                textLayerDiv = cached.textLayerDiv;
                setLayerTransform(cached.pageWidth, cached.pageHeight, textLayerDiv);
                container.style.setProperty("--scale-factor", newZoomRatio.toString());
                textLayerDiv.hidden = false;
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

        if (cache.length === 0) {
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

        if (zoom === 2) {
            textLayerDiv.hidden = true;
            pageRendering = false;

            // zoom focus relative to page origin, rather than screen origin
            const globalFocusX = channel.getZoomFocusX() / ratio + globalThis.scrollX;
            const globalFocusY = channel.getZoomFocusY() / ratio + globalThis.scrollY;

            const translationFactor = scaleFactor - 1;
            const scrollX = globalFocusX * translationFactor;
            const scrollY = globalFocusY * translationFactor;
            scrollBy(scrollX, scrollY);

            return;
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
    if (isVerticalScrollMode) {
        if (pageRendering) {
            renderPending = true;
            renderPendingZoom = zoom;
        } else {
            renderAllPages(zoom);
        }
        return;
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
        getSimplifiedOutline(outline, outlineAbort).then(function(outlineEntries) {
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

// Vertical scrolling mode global functions
globalThis.setVerticalScrollMode = function(enabled) {
    if (enabled) {
        switchToVerticalMode();
    } else {
        switchToPageMode();
    }
};

globalThis.isVerticalScrollMode = function() {
    return isVerticalScrollMode;
};

globalThis.getCurrentPageInView = function() {
    return isVerticalScrollMode ? currentPageInView : channel.getPage();
};

globalThis.scrollToPageInDocument = function(pageNumber) {
    if (isVerticalScrollMode) {
        scrollToPage(pageNumber);
    }
};

globalThis.loadDocument = function () {
    const pdfPassword = channel.getPassword();
    const loadingTask = getDocument({
        url: "https://localhost/placeholder.pdf",
        cMapUrl: "https://localhost/viewer/cmaps/",
        cMapPacked: true,
        password: pdfPassword,
        iccUrl: "https://localhost/viewer/iccs/",
        isEvalSupported: false,
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
        pdfDoc.getOutline().then(function(outline) {
            channel.setHasDocumentOutline(outline && outline.length > 0);
        }).catch(function(error) {
            console.log("getOutline error: " + error);
        });
        
        // Check if we should start in vertical scroll mode
        const shouldUseVerticalMode = channel.isVerticalScrollMode && channel.isVerticalScrollMode();
        if (shouldUseVerticalMode) {
            switchToVerticalMode();
        } else {
            renderPage(channel.getPage(), false, false);
        }
    }, function (reason) {
        console.error(reason.name + ": " + reason.message);
    });
};

globalThis.onresize = () => {
    setLayerTransform(canvas.clientWidth, canvas.clientHeight, textLayerDiv);
};
