"use strict";

let pdfDoc = null;
let pageRendering = false;
let renderPending = false;
let renderPendingLazy = false;
const canvas = document.getElementById('content');
let zoomLevel = 100;
let textLayerDiv = document.getElementById("text");
const zoomLevels = [50, 75, 100, 125, 150];
let task = null;

let newPageNumber = 0;
let newZoomLevel = 0;
let useRender;

const cache = [];
const maxCached = 6;

function maybeRenderNextPage() {
    if (renderPending) {
        pageRendering = false;
        renderPending = false;
        renderPage(channel.getPage(), renderPendingLazy, false);
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
    } else if (pageNumber == prerenderTrigger + 1) {
        if (prerenderTrigger - 1 > 0) {
            renderPage(prerenderTrigger - 1, false, true, prerenderTrigger);
        }
    }
}

function display(newCanvas) {
    canvas.height = newCanvas.height;
    canvas.width = newCanvas.width;
    canvas.style.height = newCanvas.style.height;
    canvas.style.width = newCanvas.style.width;
    canvas.getContext("2d", { alpha: false }).drawImage(newCanvas, 0, 0);
    scrollTo(0, 0);
}

function renderPage(pageNumber, lazy, prerender, prerenderTrigger=0) {
    pageRendering = true;
    useRender = !prerender;

    newPageNumber = pageNumber;
    newZoomLevel = zoomLevels[channel.getZoomLevel()];
    console.log("page: " + pageNumber + ", zoom: " + newZoomLevel + ", prerender: " + prerender);
    for (let i = 0; i < cache.length; i++) {
        const cached = cache[i];
        if (cached.pageNumber === pageNumber && cached.zoomLevel === newZoomLevel) {
            if (useRender) {
                cache.splice(i, 1);
                cache.push(cached);

                display(cached.canvas);

                textLayerDiv.replaceWith(cached.textLayerDiv);
                textLayerDiv = cached.textLayerDiv;
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

        const newCanvas = document.createElement("canvas");
        const viewport = page.getViewport(newZoomLevel / 100)
        const ratio = window.devicePixelRatio;
        newCanvas.height = viewport.height * ratio;
        newCanvas.width = viewport.width * ratio;
        newCanvas.style.height = viewport.height + "px";
        newCanvas.style.width = viewport.width + "px";
        const newContext = newCanvas.getContext("2d", { alpha: false });
        newContext.scale(ratio, ratio);

        if (useRender) {
            if (newZoomLevel != zoomLevel) {
                canvas.style.height = viewport.height + "px";
                canvas.style.width = viewport.width + "px";
            }
            zoomLevel = newZoomLevel;
        }

        task = page.render({
            canvasContext: newContext,
            viewport: viewport
        });

        task.then(function() {
            task = null;

            let rendered = false;
            function render() {
                if (!useRender || rendered) {
                    return;
                }
                display(newCanvas);
                rendered = true;
            }
            render();

            const textLayerFrag = document.createDocumentFragment();
            task = PDFJS.renderTextLayer({
                textContentStream: page.streamTextContent(),
                container: textLayerFrag,
                viewport: viewport
            });
            task.promise.then(function() {
                task = null;

                render();

                const newTextLayerDiv = textLayerDiv.cloneNode();
                newTextLayerDiv.style.height = newCanvas.style.height;
                newTextLayerDiv.style.width = newCanvas.style.width;
                if (useRender) {
                    textLayerDiv.replaceWith(newTextLayerDiv);
                    textLayerDiv = newTextLayerDiv;
                }
                newTextLayerDiv.appendChild(textLayerFrag);

                if (cache.length === maxCached) {
                    cache.shift()
                }
                cache.push({
                    pageNumber: pageNumber,
                    zoomLevel: newZoomLevel,
                    canvas: newCanvas,
                    textLayerDiv: newTextLayerDiv
                });

                pageRendering = false;
                doPrerender(pageNumber, prerenderTrigger);
            }).catch(handleRenderingError);
        }).catch(handleRenderingError);
    });
}

function onRenderPage(lazy) {
    if (pageRendering) {
        if (newPageNumber === channel.getPage() && newZoomLevel === zoomLevels[channel.getZoomLevel()]) {
            useRender = true;
            return;
        }

        renderPending = true;
        renderPendingLazy = lazy;
        if (task !== null) {
            task.cancel();
            task = null;
        }
    } else {
        renderPage(channel.getPage(), lazy, false);
    }
}

PDFJS.getDocument("https://localhost/placeholder.pdf").then(function(newDoc) {
    pdfDoc = newDoc;
    channel.setNumPages(pdfDoc.numPages);
    pdfDoc.getMetadata().then(function(data) {
        channel.setDocumentProperties(JSON.stringify(data.info));
    }).catch(function(error) {
        console.log("getMetadata error: " + error);
    });
    renderPage(channel.getPage(), false, false);
}).catch(function(error) {
    console.log("getDocument error: " + error);
});
