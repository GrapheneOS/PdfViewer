import {
    GlobalWorkerOptions,
    PDFDocumentProxy,
    PDFPageProxy,
    PasswordResponses,
    RenderTask,
    TextLayerRenderTask,
    getDocument,
    renderTextLayer,
} from "pdfjs-dist"

/**
 * Android JavaScript interface definition. Must be kept in sync with its implementation in
 * `PdfViewer.java`.
 */
interface Channel {
    getPage(): number
    getZoomRatio(): number
    setZoomRatio(ratio: number): void
    getMinZoomRatio(): number
    getMaxZoomRatio(): number
    getDocumentOrientationDegrees(): number
    setNumPages(numPages: number): void
    setDocumentProperties(properties: string): void
    showPasswordPrompt(): void
    invalidPassword(): void
    onLoaded(): void
    getPassword(): string
}

interface PageCache {
    pageNumber: number
    zoomRatio: number
    orientationDegrees: number
    canvas: HTMLCanvasElement
    textLayerDiv: HTMLDivElement
    pageWidth: number
    pageHeight: number
}

declare global {
    const channel: Channel
    function onRenderPage(zoom: 0 | 1 | 2): void
    function isTextSelected(): boolean
    function toggleTextLayerVisibility(): void
    function loadDocument(): void
}

GlobalWorkerOptions.workerSrc = "/viewer/js/worker.js"

let pdfDoc: PDFDocumentProxy | undefined
let pageRendering = false
let renderPending = false
let renderPendingZoom: 0 | 1 | 2 = 0
const canvas = document.getElementById("content") as HTMLCanvasElement
const container = document.getElementById("container") as HTMLDivElement
let orientationDegrees = 0
let zoomRatio = 1
let textLayerDiv = document.getElementById("text") as HTMLDivElement
let task: RenderTask | TextLayerRenderTask | null = null

let newPageNumber = 0
let newZoomRatio = 1
let useRender = false

const cache: PageCache[] = []
const maxCached = 6

let isTextLayerVisible = false

function maybeRenderNextPage() {
    if (renderPending) {
        pageRendering = false
        renderPending = false
        renderPage(channel.getPage(), renderPendingZoom, false)
        return true
    }
    return false
}

function handleRenderingError(error: unknown) {
    console.log("rendering error: " + error)

    pageRendering = false
    maybeRenderNextPage()
}

function doPrerender(pageNumber: number, prerenderTrigger: number) {
    if (pdfDoc === undefined) {
        throw new Error("doPrerender: pdfDoc is undefined")
    }

    if (useRender) {
        if (pageNumber + 1 <= pdfDoc.numPages) {
            renderPage(pageNumber + 1, 0, true, pageNumber)
        } else if (pageNumber - 1 > 0) {
            renderPage(pageNumber - 1, 0, true, pageNumber)
        }
    } else if (pageNumber === prerenderTrigger + 1) {
        if (prerenderTrigger - 1 > 0) {
            renderPage(prerenderTrigger - 1, 0, true, prerenderTrigger)
        }
    }
}

function display(newCanvas: HTMLCanvasElement, zoom: 0 | 1 | 2) {
    canvas.height = newCanvas.height
    canvas.width = newCanvas.width
    canvas.style.height = newCanvas.style.height
    canvas.style.width = newCanvas.style.width
    canvas.getContext("2d", { alpha: false })?.drawImage(newCanvas, 0, 0)
    if (zoom === 0) {
        scrollTo(0, 0)
    }
}

function setLayerTransform(pageWidth: number, pageHeight: number, layerDiv: HTMLDivElement) {
    const translate = {
        X: Math.max(0, pageWidth - document.body.clientWidth) / 2,
        Y: Math.max(0, pageHeight - document.body.clientHeight) / 2,
    }
    layerDiv.style.translate = `${translate.X}px ${translate.Y}px`
}

function getDefaultZoomRatio(page: PDFPageProxy, orientationDegrees: number) {
    const viewport = page.getViewport({ scale: 1, rotation: orientationDegrees })
    const widthZoomRatio = document.body.clientWidth / viewport.width
    const heightZoomRatio = document.body.clientHeight / viewport.height
    return Math.max(Math.min(widthZoomRatio, heightZoomRatio, channel.getMaxZoomRatio()), channel.getMinZoomRatio())
}

function renderPage(pageNumber: number, zoom: 0 | 1 | 2, prerender: boolean, prerenderTrigger = 0) {
    pageRendering = true
    useRender = !prerender

    newPageNumber = pageNumber
    newZoomRatio = channel.getZoomRatio()
    orientationDegrees = channel.getDocumentOrientationDegrees()
    console.log("page: " + pageNumber + ", zoom: " + newZoomRatio +
        ", orientationDegrees: " + orientationDegrees + ", prerender: " + prerender)
    for (let i = 0; i < cache.length; i++) {
        const cached = cache[i]
        if (cached.pageNumber === pageNumber && cached.zoomRatio === newZoomRatio &&
            cached.orientationDegrees === orientationDegrees) {
            if (useRender) {
                cache.splice(i, 1)
                cache.push(cached)

                display(cached.canvas, zoom)

                textLayerDiv.replaceWith(cached.textLayerDiv)
                textLayerDiv = cached.textLayerDiv
                setLayerTransform(cached.pageWidth, cached.pageHeight, textLayerDiv)
                container.style.setProperty("--scale-factor", newZoomRatio.toString())
            }

            pageRendering = false
            doPrerender(pageNumber, prerenderTrigger)
            return
        }
    }

    if (pdfDoc === undefined) {
        throw new Error("renderPage: pdfDoc is undefined")
    }

    pdfDoc.getPage(pageNumber).then(function (page) {
        if (maybeRenderNextPage()) {
            return
        }

        const defaultZoomRatio = getDefaultZoomRatio(page, orientationDegrees)

        if (cache.length === 0) {
            zoomRatio = defaultZoomRatio
            newZoomRatio = defaultZoomRatio
            channel.setZoomRatio(defaultZoomRatio)
        }

        const viewport = page.getViewport({ scale: newZoomRatio, rotation: orientationDegrees })

        if (useRender) {
            if (newZoomRatio !== zoomRatio) {
                canvas.style.height = viewport.height + "px"
                canvas.style.width = viewport.width + "px"
            }
            zoomRatio = newZoomRatio
        }

        if (zoom === 2) {
            pageRendering = false
            return
        }

        const newCanvas = document.createElement("canvas")
        const ratio = globalThis.devicePixelRatio
        newCanvas.height = viewport.height * ratio
        newCanvas.width = viewport.width * ratio
        newCanvas.style.height = viewport.height + "px"
        newCanvas.style.width = viewport.width + "px"
        const newContext = newCanvas.getContext("2d", { alpha: false })
        if (newContext === null) {
            throw new Error("renderPage: newContext is null")
        }

        newContext.scale(ratio, ratio)

        task = page.render({
            canvasContext: newContext,
            viewport: viewport,
        })

        task.promise.then(function () {
            task = null

            let rendered = false
            function render() {
                if (!useRender || rendered) {
                    return
                }
                display(newCanvas, zoom)
                rendered = true
            }
            render()

            const newTextLayerDiv = textLayerDiv.cloneNode() as HTMLDivElement
            task = renderTextLayer({
                textContentSource: page.streamTextContent(),
                container: newTextLayerDiv,
                viewport: viewport,
            })
            task.promise.then(function () {
                task = null

                render()

                // We use CSS transform to rotate a text layer div of zero
                // degrees rotation. So, when the rotation is 90 or 270
                // degrees, set width and height of the text layer div to the
                // height and width of the canvas, respectively, to prevent
                // text layer misalignment.
                if (orientationDegrees % 180 === 0) {
                    newTextLayerDiv.style.height = newCanvas.style.height
                    newTextLayerDiv.style.width = newCanvas.style.width
                } else {
                    newTextLayerDiv.style.height = newCanvas.style.width
                    newTextLayerDiv.style.width = newCanvas.style.height
                }
                setLayerTransform(viewport.width, viewport.height, newTextLayerDiv)
                if (useRender) {
                    textLayerDiv.replaceWith(newTextLayerDiv)
                    textLayerDiv = newTextLayerDiv
                    container.style.setProperty("--scale-factor", newZoomRatio.toString())
                }

                if (cache.length === maxCached) {
                    cache.shift()
                }
                cache.push({
                    pageNumber: pageNumber,
                    zoomRatio: newZoomRatio,
                    orientationDegrees: orientationDegrees,
                    canvas: newCanvas,
                    textLayerDiv: newTextLayerDiv,
                    pageWidth: viewport.width,
                    pageHeight: viewport.height,
                })

                pageRendering = false
                doPrerender(pageNumber, prerenderTrigger)
            }).catch(handleRenderingError)
        }).catch(handleRenderingError)
    })
}

globalThis.onRenderPage = function (zoom: 0 | 1 | 2) {
    if (pageRendering) {
        if (newPageNumber === channel.getPage() && newZoomRatio === channel.getZoomRatio() &&
            orientationDegrees === channel.getDocumentOrientationDegrees()) {
            useRender = true
            return
        }

        renderPending = true
        renderPendingZoom = zoom
        if (task !== null) {
            task.cancel()
            task = null
        }
    } else {
        renderPage(channel.getPage(), zoom, false)
    }
}

globalThis.isTextSelected = function () {
    return globalThis.getSelection()?.toString() !== ""
}

globalThis.toggleTextLayerVisibility = function () {
    let textLayerForeground = "red"
    let textLayerOpacity = 1
    if (isTextLayerVisible) {
        textLayerForeground = "transparent"
        textLayerOpacity = 0.2
    }
    document.documentElement.style.setProperty("--text-layer-foreground", textLayerForeground)
    document.documentElement.style.setProperty("--text-layer-opacity", textLayerOpacity.toString())
    isTextLayerVisible = !isTextLayerVisible
}

globalThis.loadDocument = function () {
    const pdfPassword = channel.getPassword()
    const loadingTask = getDocument({ url: "https://localhost/placeholder.pdf", password: pdfPassword })
    loadingTask.onPassword = (_: unknown, error: number) => {
        if (error === PasswordResponses.NEED_PASSWORD) {
            channel.showPasswordPrompt()
        } else if (error === PasswordResponses.INCORRECT_PASSWORD) {
            channel.invalidPassword()
        }
    }

    loadingTask.promise.then(function (newDoc) {
        channel.onLoaded()
        pdfDoc = newDoc
        channel.setNumPages(pdfDoc.numPages)
        pdfDoc.getMetadata().then(function (data) {
            channel.setDocumentProperties(JSON.stringify(data.info))
        }).catch(function (error) {
            console.log("getMetadata error: " + error)
        })
        renderPage(channel.getPage(), 0, false)
    }, function (reason) {
        console.error(reason.name + ": " + reason.message)
    })
}

globalThis.onresize = () => {
    setLayerTransform(canvas.clientWidth, canvas.clientHeight, textLayerDiv)
}
