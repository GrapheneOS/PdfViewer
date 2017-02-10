"use strict";

var pdfDoc = null;
var pageRendering = false;
var renderPending = false;
var canvas = document.getElementById('content');
var ctx = canvas.getContext('2d');
var textLayerDiv = document.getElementById("text");
var zoomLevels = [50, 75, 100, 125, 150];

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
        var last;
        while (last = textLayerDiv.lastChild) {
            textLayerDiv.removeChild(last);
        }

        var viewport = page.getViewport(zoomLevels[channel.getZoomLevel()] / 100)
        var ratio = window.devicePixelRatio;
        canvas.height = viewport.height * ratio;
        canvas.width = viewport.width * ratio;
        canvas.style.height = viewport.height + "px";
        canvas.style.width = viewport.width + "px";
        ctx.scale(ratio, ratio);

        textLayerDiv.style.height = canvas.style.height;
        textLayerDiv.style.width = canvas.style.width;

        var renderTask = page.render({
            canvasContext: ctx,
            viewport: viewport
        });

        renderTask.then(function() {
            if (maybeRenderNextPage()) {
                return;
            }

            page.getTextContent().then(function(textContent) {
                if (maybeRenderNextPage()) {
                    return;
                }

                var textLayerFrag = document.createDocumentFragment();
                var textLayerRenderTask = PDFJS.renderTextLayer({
                    textContent: textContent,
                    container: textLayerFrag,
                    viewport: viewport
                });
                textLayerRenderTask.promise.then(function() {
                    textLayerDiv.appendChild(textLayerFrag);
                    pageRendering = false;
                    maybeRenderNextPage();
                });
            })
        });
    });
}

function queueRenderPage() {
    if (pageRendering) {
        renderPending = true;
    } else {
        renderPage();
    }
}

function onRenderPage() {
    queueRenderPage();
}

function onGetDocument() {
    PDFJS.getDocument(channel.getUrl()).then(function(newDoc) {
        pdfDoc = newDoc;
        channel.setNumPages(pdfDoc.numPages);
        queueRenderPage();
    });
}
