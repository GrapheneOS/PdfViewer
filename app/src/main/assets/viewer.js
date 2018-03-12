"use strict";

let pdfDoc = null;
let pageRendering = false;
let renderPending = false;
let renderPendingLazy = false;
let canvas = document.getElementById('content');
let zoomLevel = 100;
const textLayerDiv = document.getElementById("text");
const zoomLevels = [50, 75, 100, 125, 150];
let renderTask = null;
let textLayerRenderTask = null;

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
    pdfDoc.getPage(channel.getPage()).then(function(page) {
        let last;
        while (last = textLayerDiv.lastChild) {
            textLayerDiv.removeChild(last);
        }

        const newZoomLevel = zoomLevels[channel.getZoomLevel()];

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

        function finishRendering() {
            pageRendering = false;
            maybeRenderNextPage();
        }

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
