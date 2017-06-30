"use strict";

let pdfDoc = null;
let pageRendering = false;
let renderPending = false;
let canvas = document.getElementById('content');
const textLayerDiv = document.getElementById("text");
const zoomLevels = [50, 75, 100, 125, 150];
let renderTask = null;
let textLayerRenderTask = null;

function maybeRenderNextPage() {
    if (renderPending) {
        pageRendering = false;
        renderPending = false;
        renderPage();
        return true;
    }
    return false;
}

function renderPage() {
    pageRendering = true;
    pdfDoc.getPage(channel.getPage()).then(function(page) {
        let last;
        while (last = textLayerDiv.lastChild) {
            textLayerDiv.removeChild(last);
        }

        const newCanvas = document.createElement("canvas");
        canvas.replaceWith(newCanvas);
        canvas = newCanvas;

        const viewport = page.getViewport(zoomLevels[channel.getZoomLevel()] / 100)
        const ratio = window.devicePixelRatio;
        canvas.height = viewport.height * ratio;
        canvas.width = viewport.width * ratio;
        canvas.style.height = viewport.height + "px";
        canvas.style.width = viewport.width + "px";
        const ctx = canvas.getContext("2d");
        ctx.scale(ratio, ratio);

        textLayerDiv.style.height = canvas.style.height;
        textLayerDiv.style.width = canvas.style.width;

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

function onRenderPage() {
    if (pageRendering) {
        renderPending = true;
        if (renderTask !== null) {
            renderTask.cancel();
            renderTask = null;
        }
        if (textLayerRenderTask !== null) {
            textLayerRenderTask.cancel();
            textLayerRenderTask = null;
        }
    } else {
        renderPage();
    }
}

PDFJS.getDocument("https://localhost/placeholder.pdf").then(function(newDoc) {
    pdfDoc = newDoc;
    channel.setNumPages(pdfDoc.numPages);
    pdfDoc.getMetadata().then(function(data) {
        channel.setDocumentProperties(JSON.stringify(data.info, null, 2));
    });
    renderPage();
});
