"use strict";

var pdfDoc = null,
    pageRendering = false,
    pageNumPending = null,
    scale = 1.0,
    canvas = document.getElementById('content'),
    ctx = canvas.getContext('2d');

var zoomLevels = [50, 75, 100, 125, 150];

/**
 * Get page info from document, resize canvas accordingly, and render page.
 * @param num Page number.
 */
function renderPage(num) {
    pageRendering = true;
    pdfDoc.getPage(num).then(function(page) {
        var viewport = page.getViewport(scale);
        var ratio = window.devicePixelRatio;
        canvas.height = viewport.height * ratio;
        canvas.width = viewport.width * ratio;
        canvas.style.height = viewport.height + "px";
        canvas.style.width = viewport.width + "px";
        ctx.scale(ratio, ratio);

        var renderContext = {
            canvasContext: ctx,
            viewport: viewport
        };
        var renderTask = page.render(renderContext);

        renderTask.promise.then(function () {
            pageRendering = false;
            if (pageNumPending !== null) {
                renderPage(pageNumPending);
                pageNumPending = null;
            }
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
    PDFJS.getDocument(channel.getUrl()).then(function (newDoc) {
        pdfDoc = newDoc;
        channel.setNumPages(pdfDoc.numPages);
        scale = zoomLevels[channel.getZoomLevel()] / 100;
        renderPage(channel.getPage());
    });
}
