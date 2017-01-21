"use strict";

var pdfDoc = null,
    pageRendering = false,
    pageNumPending = null,
    scale = 1.0,
    canvas = document.getElementById('content'),
    ctx = canvas.getContext('2d');

var zoomLevels = [50, 75, 100, 125, 150];
var zoomLevel = 2;

/**
 * Get page info from document, resize canvas accordingly, and render page.
 * @param num Page number.
 */
function renderPage(num) {
    pageRendering = true;
    // Using promise to fetch the page
    pdfDoc.getPage(num).then(function(page) {
        var viewport = page.getViewport(scale);
        var ratio = window.devicePixelRatio;
        canvas.height = viewport.height * ratio;
        canvas.width = viewport.width * ratio;
        canvas.style.height = viewport.height + "px";
        canvas.style.width = viewport.width + "px";
        ctx.scale(ratio, ratio);

        // Render PDF page into canvas context
        var renderContext = {
            canvasContext: ctx,
            viewport: viewport
        };
        var renderTask = page.render(renderContext);

        // Wait for rendering to finish
        renderTask.promise.then(function () {
            pageRendering = false;
            if (pageNumPending !== null) {
                // New page rendering is pending
                renderPage(pageNumPending);
                pageNumPending = null;
            }
        });
    });

    // Update page counters
    //document.getElementById('page_num').textContent = pageNum;
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
    queueRenderPage(channel.getPage());
}

function onZoomOut() {
    if (zoomLevel == 0) {
        return;
    }
    zoomLevel--;
    scale = zoomLevels[zoomLevel] / 100;
    queueRenderPage(channel.getPage());
}

function onZoomIn() {
    if (zoomLevel == zoomLevels.length - 1) {
        return;
    }
    zoomLevel++;
    scale = zoomLevels[zoomLevel] / 100;
    queueRenderPage(channel.getPage());
}

function onGetDocument() {
    PDFJS.getDocument(channel.getUrl()).then(function (newDoc) {
        pdfDoc = newDoc;
        channel.setNumPages(pdfDoc.numPages);
        renderPage(channel.getPage());
    });
}
