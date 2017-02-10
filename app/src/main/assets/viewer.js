"use strict";

var pdfDoc = null;
var pageRendering = false;
var pageNumPending = null;
var scale = 1.0;
var canvas = document.getElementById('content');
var ctx = canvas.getContext('2d');
var textLayerDiv = document.getElementById("text");
var zoomLevels = [50, 75, 100, 125, 150];

function maybeRenderNextPage() {
    if (pageNumPending !== null) {
        pageRendering = false;
        renderPage(pageNumPending);
        pageNumPending = null;
        return true;
    }
    return false;
}

/**
 * Get page info from document, resize canvas accordingly, and render page.
 * @param num Page number.
 */
function renderPage(num) {
    pageRendering = true;
    pdfDoc.getPage(num).then(function(page) {
        var last;
        while (last = textLayerDiv.lastChild) {
            textLayerDiv.removeChild(last);
        }

        var viewport = page.getViewport(scale);
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

/**
 * If another page rendering in progress, waits until the rendering is
 * finished. Otherwise, executes rendering immediately.
 */
function queueRenderPage(num) {
    if (pageRendering) {
        pageNumPending = num;
    } else {
        renderPage(num);
    }
}

function onRenderPage() {
    scale = zoomLevels[channel.getZoomLevel()] / 100;
    queueRenderPage(channel.getPage());
}

function onGetDocument() {
    PDFJS.getDocument(channel.getUrl()).then(function(newDoc) {
        pdfDoc = newDoc;
        channel.setNumPages(pdfDoc.numPages);
        scale = zoomLevels[channel.getZoomLevel()] / 100;
        renderPage(channel.getPage());
    });
}
