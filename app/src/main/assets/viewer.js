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

var loading_bar = document.getElementById("loading-bar");
var loading_bar_container = document.getElementById("loading-bar-container");

function show_progress_bar() {
    loading_bar.style.display = "block";
    loading_bar_container.style.display = "block";
}

function hide_progress_bar() {
    loading_bar.style.display = "none";
    loading_bar_container.style.display = "none";
}

function set_progress(value) {
    if(value==0) show_progress_bar();
    else if(value==100) hide_progress_bar();
    else loading_bar.style.width = value + "%";
}

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
                cached.orientationDegrees === orientationDegrees) {
            if (useRender) {
                cache.splice(i, 1);
                cache.push(cached);

                display(cached.canvas, zoom);

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

    loadingTask.onProgress = function(data){
        var progress = Math.ceil((data.loaded/data.total)*100);
        console.log("Progress: " + progress);
        set_progress(progress);
    }

    show_progress_bar();

    loadingTask.promise.then(function (newDoc) {
        channel.onLoaded();
        pdfDoc = newDoc;
        channel.setNumPages(pdfDoc.numPages);
        pdfDoc.getMetadata().then(function (data) {
            channel.setDocumentProperties(JSON.stringify(data.info));
        }).catch(function (error) {
            console.log("getMetadata error: " + error);
        });
        renderPage(channel.getPage(), false, false);
        hide_progress_bar();
    }, function (reason) {
        hide_progress_bar();
        console.error(reason.name + ": " + reason.message);
    });
}
