"use strict";

pdfjsLib.GlobalWorkerOptions.workerSrc = "/pdf.worker.js";

let pdfDoc = null;
let pageRendering = false;
let renderPending = false;
let renderPendingZoom = 0;
const canvas = document.getElementById('content');
let orientationDegrees = 0;
let zoomRatio = 1;
let textLayerDiv = document.getElementById("text");
let task = null;

let newPageNumber = 0;
let newZoomRatio = 1;
let useRender;

const cache = [];
const maxCached = 6;

const H_PADDING_HINT = 32; // in pixels
const V_PADDING_HINT = 32; // in pixels

// This helps in preventing repetitive calculation (addition) ahead
// Instead of adding the canvas width/height with the padding we subtract the RHS instead
const sWWithoutPad = screen.width - H_PADDING_HINT;
const sHWithoutPad = screen.height - V_PADDING_HINT;

function maybeRenderNextPage() {
    if (renderPending) {
        pageRendering = false;
        renderPending = false;
        renderPage(channel.getPage(), renderPendingZoom, false);
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

// Centers the page (i.e. canvas + text layer) vertically
function verticallyCenterPage() {
    canvas.style.top = "50%";
    textLayerDiv.style.top = "50%";
}

// Centers the page (i.e. canvas + text layer) horizontally
function horizontallyCenterPage() {
    canvas.style.left = "50%";
    textLayerDiv.style.left = "50%";
}

// Fixes canvas position (both horizontally and vertically)
//
// Center the page if it's completely visible on the screen (along with the padding)
// else ensure that the user hasn't moved beyond the bounds and padding of the page while
// zooming
function fixCanvasPositioning() {
    if (canvas.clientWidth <= sWWithoutPad) horizontallyCenterPage();
    else ensurePageWithinHorizontalBounds();

    if (canvas.clientHeight <= sHWithoutPad) verticallyCenterPage();
    else ensurePageWithinVerticalBounds();
}

function ensurePageWithinHorizontalBounds() {
    var pageL = canvas.offsetLeft;
    const cWidthH = canvas.clientWidth / 2;

    const x0 = cWidthH + H_PADDING_HINT;
    const x1 = -x0 + screen.width;

    if (pageL > x0) pageL = x0;
    else if (pageL < x1) pageL = x1;

    pageL += "px";

    canvas.style.left = pageL;
    textLayerDiv.style.left = pageL;
}

function ensurePageWithinVerticalBounds() {
    var pageT = canvas.offsetTop;
    const cHeightH = canvas.clientHeight / 2;

    const y0 = cHeightH + V_PADDING_HINT;
    const y1 = -y0 + screen.height;

    if (pageT > y0) pageT = y0;
    else if (pageT < y1) pageT = y1;

    pageT += "px";

    canvas.style.top = pageT;
    textLayerDiv.style.top = pageT;
}

function renderPage(pageNumber, zoom, prerender, prerenderTrigger=0) {
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
                cache.orientationDegrees === orientationDegrees) {
            if (useRender) {
                cache.splice(i, 1);
                cache.push(cached);

                display(cached.canvas, zoom);

                textLayerDiv.replaceWith(cached.textLayerDiv);
                textLayerDiv = cached.textLayerDiv;
            }

            pageRendering = false;
            doPrerender(pageNumber, prerenderTrigger);
            fixCanvasPositioning();
            return;
        }
    }

    pdfDoc.getPage(pageNumber).then(function(page) {
        if (maybeRenderNextPage()) {
            return;
        }

        const viewport = page.getViewport({scale: newZoomRatio, rotation: orientationDegrees})

        if (useRender) {
            if (newZoomRatio !== zoomRatio) {
                canvas.style.height = viewport.height + "px";
                canvas.style.width = viewport.width + "px";
            }
            zoomRatio = newZoomRatio;
        }

        if (zoom == 2) {
            pageRendering = false;
            return;
        }

        const newCanvas = document.createElement("canvas");
        const ratio = window.devicePixelRatio;
        newCanvas.height = viewport.height * ratio;
        newCanvas.width = viewport.width * ratio;
        newCanvas.style.height = viewport.height + "px";
        newCanvas.style.width = viewport.width + "px";
        const newContext = newCanvas.getContext("2d", { alpha: false });
        newContext.scale(ratio, ratio);
        fixCanvasPositioning();

        task = page.render({
            canvasContext: newContext,
            viewport: viewport
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

            const textLayerFrag = document.createDocumentFragment();
            task = pdfjsLib.renderTextLayer({
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
                    zoomRatio: newZoomRatio,
                    orientationDegrees: orientationDegrees,
                    canvas: newCanvas,
                    textLayerDiv: newTextLayerDiv
                });

                pageRendering = false;
                doPrerender(pageNumber, prerenderTrigger);
            }).catch(handleRenderingError);
        }).catch(handleRenderingError);
    });
}

function onRenderPage(zoom) {
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
}

function isTextSelected() {
    return window.getSelection().toString() !== "";
}

function loadDocument() {
    const pdfPassword = channel.getPassword();
    const loadingTask = pdfjsLib.getDocument({ url: "https://localhost/placeholder.pdf", password: pdfPassword });
    loadingTask.onPassword = (_, error) => {
        if (error === pdfjsLib.PasswordResponses.NEED_PASSWORD) {
            channel.showPasswordPrompt();
        } else if (error === pdfjsLib.PasswordResponses.INCORRECT_PASSWORD) {
            channel.invalidPassword();
        }
    }

    loadingTask.promise.then(function (newDoc) {
        pdfDoc = newDoc;
        channel.setNumPages(pdfDoc.numPages);
        pdfDoc.getMetadata().then(function (data) {
            channel.setDocumentProperties(JSON.stringify(data.info));
        }).catch(function (error) {
            console.log("getMetadata error: " + error);
        });
        renderPage(channel.getPage(), false, false);
    }, function (reason) {
        console.error(reason.name + ": " + reason.message);
    });
}

let lastX = 0;
let lastY = 0;
let shouldMove = false;

function onTouchStart(te) {
    if (te.touches.length == 1) {
        shouldMove = true;
        let t = te.touches[0];
        lastX = t.clientX;
        lastY = t.clientY;
        return;
    }
    shouldMove = false;
}

function onTouchMove(te) {

    if (!shouldMove) return;

    const t = te.touches[0];

    if (canvas.clientWidth > sWWithoutPad) {

        const xDelta = (lastX - t.clientX);
        var pageL = (canvas.offsetLeft - xDelta);

        const cWidthH = canvas.clientWidth / 2;

        const x0 = cWidthH + H_PADDING_HINT;
        const x1 = -x0 + screen.width;

        // console.log("x0: " + x0);
        // console.log("x1: " + x1);

        // console.log("old.pageL: " + pageL);

        if (pageL > x0) pageL = x0;
        else if (pageL < x1) pageL = x1;

        // console.log("new.pageL: " + pageL);

        pageL += "px";

        canvas.style.left = pageL;
        textLayerDiv.style.left = pageL;
    }

    if (canvas.clientHeight > sHWithoutPad) {

        const yDelta = (lastY - t.clientY);
        var pageT = (canvas.offsetTop - yDelta);

        const cHeightH = canvas.clientHeight / 2;

        // Calculating the top and bottom max bounds
        const y0 = cHeightH + V_PADDING_HINT;
        const y1 = -y0 + screen.height;

        // Logging the max. vertical bounds
        // console.log("y0: " + y0);
        // console.log("y1: " + y1);

        // Value of pageT before adjusting it w.r.t max bounds
        // console.log("old.pageT: " + pageT);

        // Adjusting the value of pageT w.r.t max bounds
        if (pageT > y0) pageT = y0;
        else if (pageT < y1) pageT = y1;

        // Value of pageT after adjusting it w.r.t max bounds
        // console.log("new.pageT: " + pageT);

        pageT += "px";

        canvas.style.top = pageT;
        textLayerDiv.style.top = pageT;
    }

    lastX = t.clientX;
    lastY = t.clientY;
}

function addDocumentEventListeners() {
    document.addEventListener('touchstart', onTouchStart);
    document.addEventListener('touchmove', onTouchMove);
}

document.addEventListener("DOMContentLoaded", addDocumentEventListeners);