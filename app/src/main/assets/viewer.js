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

let newPageNumber = 0;
let newZoomLevel = 0;
let newTextLayerDiv;
let newCanvas;

const cache = [];
const maxCached = 5;

function maybeRenderNextPage() {
    if (renderPending) {
        pageRendering = false;
        renderPending = false;
        renderPage(channel.getPage(), renderPendingLazy, false);
        return true;
    }
    return false;
}

function useRender() {
    if (textLayerDiv != newTextLayerDiv) {
        textLayerDiv.replaceWith(newTextLayerDiv);
        textLayerDiv = newTextLayerDiv;
    }

    if (canvas != newCanvas) {
        canvas.replaceWith(newCanvas);
        canvas = newCanvas;
    }
}

function renderPage(pageNumber, lazy, prerender) {
    pageRendering = true;

    function finishRendering() {
        pageRendering = false;
        maybeRenderNextPage();
    }

    newPageNumber = pageNumber;
    newZoomLevel = zoomLevels[channel.getZoomLevel()];
    console.log("page: " + pageNumber + ", zoom: " + newZoomLevel + ", prerender: " + prerender);
    for (let i = 0; i < cache.length; i++) {
        let cached = cache[i];
        if (cached.pageNumber == pageNumber && cached.zoomLevel == newZoomLevel) {
            if (!prerender) {
                canvas.replaceWith(cached.canvas);
                canvas = cached.canvas;
                textLayerDiv.replaceWith(cached.textLayerDiv);
                textLayerDiv = cached.textLayerDiv;
                cache.splice(i, 1);
                cache.push(cached);
            }

            pageRendering = false;
            if (!maybeRenderNextPage() && !prerender && pageNumber + 1 <= pdfDoc.numPages) {
                renderPage(pageNumber + 1, false, true);
            }
            return;
        }
    }

    pdfDoc.getPage(pageNumber).then(function(page) {
        newTextLayerDiv = textLayerDiv.cloneNode();
        if (!prerender) {
            textLayerDiv.replaceWith(newTextLayerDiv);
            textLayerDiv = newTextLayerDiv;
        }

        newCanvas = document.createElement("canvas");
        const viewport = page.getViewport(newZoomLevel / 100)
        const ratio = window.devicePixelRatio;
        newCanvas.height = viewport.height * ratio;
        newCanvas.width = viewport.width * ratio;
        newCanvas.style.height = viewport.height + "px";
        newCanvas.style.width = viewport.width + "px";
        newTextLayerDiv.style.height = newCanvas.style.height;
        newTextLayerDiv.style.width = newCanvas.style.width;
        const ctx = newCanvas.getContext("2d", { alpha: false });
        ctx.scale(ratio, ratio);

        if (!prerender) {
            if (!lazy) {
                canvas.replaceWith(newCanvas);
                canvas = newCanvas;
            } else if (newZoomLevel != zoomLevel) {
                canvas.style.height = viewport.height + "px";
                canvas.style.width = viewport.width + "px";
            }
            zoomLevel = newZoomLevel;
        }

        renderTask = page.render({
            canvasContext: ctx,
            viewport: viewport
        });

        renderTask.then(function() {
            if (maybeRenderNextPage()) {
                return;
            }

            if (!prerender && lazy) {
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
                    newTextLayerDiv.appendChild(textLayerFrag);
                    if (cache.length == maxCached) {
                        cache.shift()
                    }
                    cache.push({
                        pageNumber: pageNumber,
                        zoomLevel: newZoomLevel,
                        canvas: newCanvas,
                        textLayerDiv: newTextLayerDiv
                    });
                    pageRendering = false;
                    if (!maybeRenderNextPage() && !prerender && pageNumber + 1 <= pdfDoc.numPages) {
                        renderPage(pageNumber + 1, false, true);
                    }
                }).catch(finishRendering);
            }).catch(finishRendering);
        }).catch(finishRendering);
    });
}

function onRenderPage(lazy) {
    if (pageRendering) {
        if (newPageNumber == channel.getPage() && newZoomLevel == zoomLevels[channel.getZoomLevel()]) {
            useRender();
            return;
        }

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
        renderPage(channel.getPage(), lazy, false);
    }
}

PDFJS.getDocument("https://localhost/placeholder.pdf").then(function(newDoc) {
    pdfDoc = newDoc;
    channel.setNumPages(pdfDoc.numPages);
    pdfDoc.getMetadata().then(function(data) {
        channel.setDocumentProperties(JSON.stringify(data.info, null, 2));
    });
    renderPage(channel.getPage(), false, false);
});
