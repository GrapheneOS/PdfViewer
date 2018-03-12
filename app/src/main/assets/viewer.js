"use strict";

let pdfDoc = null;
let pageRendering = false;
let renderPending = false;
let renderPendingLazy = false;
let canvas = document.getElementById('content');
let zoomLevel = 100;
let textLayerDiv = document.getElementById("text");
const zoomLevels = [50, 75, 100, 125, 150];
let renderTask = null;
let textLayerRenderTask = null;

const cache = [];
const maxCached = 5;

function maybeRenderNextPage() {
    if (renderPending) {
        pageRendering = false;
        renderPending = false;
        renderPage(renderPendingLazy);
        return true;
    }
    return false;
}

function renderPage(lazy) {
    pageRendering = true;

    function finishRendering() {
        pageRendering = false;
        maybeRenderNextPage();
    }

    const pageNumber = channel.getPage();
    const newZoomLevel = zoomLevels[channel.getZoomLevel()];
    for (let i = 0; i < cache.length; i++) {
        let cached = cache[i];
        if (cached.pageNumber == pageNumber && cached.zoomLevel == newZoomLevel) {
            canvas.replaceWith(cached.canvas);
            canvas = cached.canvas;
            textLayerDiv.replaceWith(cached.textLayerDiv);
            textLayerDiv = cached.textLayerDiv;

            cache.splice(i, 1);
            cache.push(cached);

            finishRendering();
            return;
        }
    }

    pdfDoc.getPage(pageNumber).then(function(page) {
        const newTextLayerDiv = textLayerDiv.cloneNode();
        textLayerDiv.replaceWith(newTextLayerDiv);
        textLayerDiv = newTextLayerDiv;

        const newCanvas = document.createElement("canvas");
        const viewport = page.getViewport(newZoomLevel / 100)
        const ratio = window.devicePixelRatio;
        newCanvas.height = viewport.height * ratio;
        newCanvas.width = viewport.width * ratio;
        newCanvas.style.height = viewport.height + "px";
        newCanvas.style.width = viewport.width + "px";
        textLayerDiv.style.height = newCanvas.style.height;
        textLayerDiv.style.width = newCanvas.style.width;
        const ctx = newCanvas.getContext("2d");
        ctx.scale(ratio, ratio);

        if (!lazy) {
            canvas.replaceWith(newCanvas);
            canvas = newCanvas;
        } else if (newZoomLevel != zoomLevel) {
            canvas.style.height = viewport.height + "px";
            canvas.style.width = viewport.width + "px";
        }
        zoomLevel = newZoomLevel;

        renderTask = page.render({
            canvasContext: ctx,
            viewport: viewport
        });

        renderTask.then(function() {
            if (maybeRenderNextPage()) {
                return;
            }

            if (lazy) {
                canvas.replaceWith(newCanvas);
                canvas = newCanvas;
            }

            page.getTextContent().then(function(textContent) {
                if (maybeRenderNextPage()) {
                    return;
                }

                const textLayerFrag = document.createDocumentFragment();
                textLayerRenderTask = PDFJS.renderTextLayer({
                    textContent: textContent,
                    container: textLayerFrag,
                    viewport: viewport
                });
                textLayerRenderTask.promise.then(function() {
                    textLayerDiv.appendChild(textLayerFrag);
                    if (cache.length == maxCached) {
                        cache.shift()
                    }
                    cache.push({
                        pageNumber: pageNumber,
                        zoomLevel: zoomLevel,
                        canvas: canvas,
                        textLayerDiv: textLayerDiv
                    });
                    finishRendering();
                }).catch(finishRendering);
            }).catch(finishRendering);
        }).catch(finishRendering);
    });
}

function onRenderPage(lazy) {
    if (pageRendering) {
        renderPending = true;
        renderPendingLazy = lazy;
        if (renderTask !== null) {
            renderTask.cancel();
            renderTask = null;
        }
        if (textLayerRenderTask !== null) {
            textLayerRenderTask.cancel();
            textLayerRenderTask = null;
        }
    } else {
        renderPage(lazy);
    }
}

PDFJS.getDocument("https://localhost/placeholder.pdf").then(function(newDoc) {
    pdfDoc = newDoc;
    channel.setNumPages(pdfDoc.numPages);
    pdfDoc.getMetadata().then(function(data) {
        channel.setDocumentProperties(JSON.stringify(data.info, null, 2));
    });
    renderPage(false);
});
