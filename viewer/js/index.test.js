import { afterEach, describe, expect, it, vi } from "vitest";

const pdfJs = vi.hoisted(() => ({ loadingTask: null }));

vi.mock("pdfjs-dist", () => ({
    GlobalWorkerOptions: {},
    PasswordResponses: { NEED_PASSWORD: 1, INCORRECT_PASSWORD: 2 },
    TextLayer: class {
        render() {
            return Promise.resolve();
        }

        cancel() {}
    },
    getDocument: () => pdfJs.loadingTask,
}));

function styleDeclaration() {
    const properties = new Map();
    return {
        display: "",
        height: "",
        width: "",
        marginLeft: "",
        translate: "",
        setProperty(name, value) {
            properties.set(name, value);
        },
        getPropertyValue(name) {
            return properties.get(name) || "";
        },
    };
}

function px(value) {
    return Number.parseFloat(value) || 0;
}

class FakeElement {
    constructor(tagName, environment) {
        this.tagName = tagName;
        this.environment = environment;
        this.children = [];
        this.dataset = {};
        this.style = styleDeclaration();
        this.hidden = false;
        this.width = 0;
        this.height = 0;
    }

    appendChild(child) {
        child.parentElement = this;
        this.children.push(child);
        return child;
    }

    replaceChildren(...children) {
        this.children = [];
        for (const child of children) this.appendChild(child);
    }

    getContext() {
        return { scale() {}, drawImage() {} };
    }

    documentTop() {
        if (!this.parentElement) return 0;
        const parentTop = this.parentElement.documentTop();
        if (this.parentElement.className === "page-wrapper") return parentTop;

        let top = parentTop;
        for (const sibling of this.parentElement.children) {
            if (sibling === this) break;
            if (sibling.style.display !== "none") {
                top += px(sibling.style.height);
                if (sibling.className === "page-wrapper") {
                    top += this.environment.pageGap;
                }
            }
        }
        return top;
    }

    documentLeft() {
        if (!this.parentElement) return 0;
        const parentLeft = this.parentElement.documentLeft();
        return this.parentElement.className === "page-wrapper"
            ? parentLeft + px(this.style.marginLeft)
            : parentLeft;
    }

    getBoundingClientRect() {
        if (this.style.display === "none") {
            return { top: 0, bottom: 0, left: 0, right: 0, width: 0, height: 0 };
        }
        const height = px(this.style.height);
        const width = px(this.style.width);
        const top = this.documentTop() - globalThis.scrollY;
        const left = this.documentLeft() - globalThis.scrollX;
        return { top, bottom: top + height, left, right: left + width, height, width };
    }

    scrollIntoView() {
        const rect = this.getBoundingClientRect();
        globalThis.scrollY += rect.top;
        this.environment.scrolledPages.push(Number(this.dataset.page));
    }
}

function fakePage(pageNumber, state, { width = 100, height = 200 } = {}) {
    return {
        rotate: 0,
        getViewport({ scale, rotation }) {
            const sideways = Math.abs(rotation % 180) === 90;
            return {
                width: (sideways ? height : width) * scale,
                height: (sideways ? width : height) * scale,
            };
        },
        render() {
            state.renderCalls.push(pageNumber);
            return { promise: Promise.resolve(), cancel() {} };
        },
        streamTextContent() {
            return {};
        },
    };
}

async function flushPromises() {
    await new Promise((resolve) => setTimeout(resolve, 0));
    await new Promise((resolve) => setTimeout(resolve, 0));
}

function delay(milliseconds) {
    return new Promise((resolve) => setTimeout(resolve, milliseconds));
}

async function setupViewer({
    continuous = true,
    currentPage = 1,
    fitMode = 1,
    focusX = 50,
    focusY = 20,
    pageGap = 0,
    pageCount = 3,
    getPage,
} = {}) {
    vi.resetModules();
    const state = {
        continuous,
        currentPage,
        fitMode,
        focusX,
        focusY,
        zoom: 0.5,
        orientation: 0,
        renderCalls: [],
        scrollCalls: [],
        scrolledPages: [],
        zoomReports: [],
    };
    const environment = { pageGap, scrolledPages: state.scrolledPages };
    const container = new FakeElement("div", environment);
    const pages = new FakeElement("div", environment);
    container.appendChild(pages);
    const body = new FakeElement("body", environment);
    body.clientWidth = 100;
    body.clientHeight = 100;
    const documentElement = new FakeElement("html", environment);

    globalThis.document = {
        body,
        documentElement,
        createElement: (tagName) => new FakeElement(tagName, environment),
        getElementById: (id) => id === "container" ? container : pages,
    };
    globalThis.window = globalThis;
    globalThis.innerWidth = 100;
    globalThis.innerHeight = 100;
    globalThis.devicePixelRatio = 1;
    globalThis.scrollX = 0;
    globalThis.scrollY = 0;
    globalThis.scrollBy = (x, y) => {
        state.scrollCalls.push([x, y]);
        globalThis.scrollX += x;
        globalThis.scrollY += y;
    };
    globalThis.getSelection = () => ({ toString: () => "" });
    globalThis.IntersectionObserver = class {
        observe() {}
        disconnect() {}
    };
    globalThis.channel = {
        getMaxZoomRatio: () => 10,
        getMinZoomRatio: () => 0.1,
        getPageFitMode: () => state.fitMode,
        getContinuousMode: () => state.continuous,
        getPage: () => state.currentPage,
        getInsetLeft: () => 0,
        getInsetRight: () => 0,
        getInsetTop: () => 0,
        getInsetBottom: () => 0,
        getDocumentOrientationDegrees: () => state.orientation,
        getMaxRenderPixels: () => 10_000_000,
        getZoomRatio: () => state.zoom,
        getZoomFocusX: () => state.focusX,
        getZoomFocusY: () => state.focusY,
        setZoomRatio: (zoom) => {
            state.zoom = zoom;
            state.zoomReports.push(zoom);
        },
        setCurrentPage: (page) => {
            state.currentPage = page;
        },
        getPassword: () => "",
        onLoaded() {},
        setNumPages() {},
        setDocumentProperties() {},
        setHasDocumentOutline() {},
        onLoadError() {},
    };

    const pdfDocument = {
        numPages: pageCount,
        getPage: getPage
            ? (pageNumber) => getPage(pageNumber, state)
            : (pageNumber) => Promise.resolve(fakePage(pageNumber, state)),
        getMetadata: () => Promise.resolve({ info: {} }),
        getOutline: () => Promise.resolve([]),
    };
    pdfJs.loadingTask = { promise: Promise.resolve(pdfDocument) };

    await import("./index.js");
    globalThis.loadDocument();
    await flushPromises();
    return { state, pagesElement: pages, pdfDocument };
}

afterEach(() => {
    vi.restoreAllMocks();
});

describe("continuous page layout", () => {
    it("renders only the displayed page in single-page mode", async () => {
        const { state } = await setupViewer({ continuous: false, currentPage: 2 });

        expect(state.renderCalls).toEqual([2]);
    });

    it("keeps the focused canvas point fixed across pinch events", async () => {
        const { state, pagesElement } = await setupViewer({
            continuous: false,
            currentPage: 1,
        });
        const canvas = pagesElement.children[0].children[0];
        const before = canvas.getBoundingClientRect();
        const normalizedX = (state.focusX - before.left) / before.width;
        const normalizedY = (state.focusY - before.top) / before.height;
        state.fitMode = 0;

        state.zoom = 0.6;
        globalThis.onRenderPage(2);
        let after = canvas.getBoundingClientRect();
        expect(after.left + after.width * normalizedX).toBeCloseTo(state.focusX);
        expect(after.top + after.height * normalizedY).toBeCloseTo(state.focusY);

        state.zoom = 0.7;
        globalThis.onRenderPage(2);
        after = canvas.getBoundingClientRect();
        expect(after.left + after.width * normalizedX).toBeCloseTo(state.focusX);
        expect(after.top + after.height * normalizedY).toBeCloseTo(state.focusY);

        globalThis.onRenderPage(1);
        after = canvas.getBoundingClientRect();
        expect(after.left + after.width * normalizedX).toBeCloseTo(state.focusX);
        expect(after.top + after.height * normalizedY).toBeCloseTo(state.focusY);
    });

    it("keeps menu zoom focused on the viewport center", async () => {
        const { state, pagesElement } = await setupViewer({
            continuous: false,
            currentPage: 1,
        });
        const canvas = pagesElement.children[0].children[0];
        const before = canvas.getBoundingClientRect();
        const normalizedX = (globalThis.innerWidth / 2 - before.left) / before.width;
        const normalizedY = (globalThis.innerHeight / 2 - before.top) / before.height;
        state.fitMode = 0;
        state.zoom = 1;

        globalThis.onRenderPage(3);

        const after = canvas.getBoundingClientRect();
        expect(after.left + after.width * normalizedX).toBeCloseTo(globalThis.innerWidth / 2);
        expect(after.top + after.height * normalizedY).toBeCloseTo(globalThis.innerHeight / 2);
    });

    it("keeps a deep mixed-size page point fixed across zoom", async () => {
        const pagePatterns = [
            {width: 100, height: 100},
            {width: 200, height: 100},
            {width: 50, height: 200},
            {width: 100, height: 200},
        ];
        const dimensions = Array.from(
            {length: 12},
            (_, index) => pagePatterns[index % pagePatterns.length]
        );
        const { state, pagesElement } = await setupViewer({
            continuous: true,
            currentPage: dimensions.length,
            fitMode: 2,
            pageCount: dimensions.length,
            pageGap: 14,
            getPage: (pageNumber, viewerState) => Promise.resolve(
                fakePage(pageNumber, viewerState, dimensions[pageNumber - 1])
            ),
        });
        const canvas = pagesElement.children.at(-1).children[0];
        const before = canvas.getBoundingClientRect();
        const normalizedX = (state.focusX - before.left) / before.width;
        const normalizedY = (state.focusY - before.top) / before.height;
        state.fitMode = 0;
        state.zoom = 2;

        globalThis.onRenderPage(2);

        const after = canvas.getBoundingClientRect();
        expect(after.left + after.width * normalizedX).toBeCloseTo(state.focusX);
        expect(after.top + after.height * normalizedY).toBeCloseTo(state.focusY);
    });

    it("keeps a focus in a fixed page gap at the same edge distance", async () => {
        const { state, pagesElement } = await setupViewer({
            continuous: true,
            currentPage: 2,
            fitMode: 2,
            focusY: 18,
            pageCount: 2,
            pageGap: 14,
            getPage: (pageNumber, viewerState) => Promise.resolve(
                fakePage(pageNumber, viewerState, {width: 100, height: 50})
            ),
        });
        const firstCanvas = pagesElement.children[0].children[0];
        const secondCanvas = pagesElement.children[1].children[0];
        const firstBefore = firstCanvas.getBoundingClientRect();
        const secondBefore = secondCanvas.getBoundingClientRect();
        const distanceFromFirst = state.focusY - firstBefore.bottom;
        const distanceFromSecond = secondBefore.top - state.focusY;
        state.fitMode = 0;
        state.zoom = 2;

        globalThis.onRenderPage(2);

        const firstAfter = firstCanvas.getBoundingClientRect();
        const secondAfter = secondCanvas.getBoundingClientRect();
        expect(state.focusY - firstAfter.bottom).toBeCloseTo(distanceFromFirst);
        expect(secondAfter.top - state.focusY).toBeCloseTo(distanceFromSecond);
    });

    it("keeps over-wide pages within the horizontally scrollable area", async () => {
        const { state, pagesElement } = await setupViewer({
            continuous: true,
            currentPage: 4,
            pageCount: 4,
        });
        state.fitMode = 0;
        state.zoom = 2;

        globalThis.onRenderPage(2);

        const wrapper = pagesElement.children[3];
        const [canvas, textLayer] = wrapper.children;
        expect(wrapper.style.width).toBe("200px");
        expect(canvas.style.width).toBe("200px");
        expect(canvas.style.marginLeft).toBe("0px");
        expect(textLayer.style.translate).toBe("0px 0px");
    });

    it("resizes far placeholders when free zoom changes", async () => {
        const { state, pagesElement } = await setupViewer({ pageCount: 5 });
        state.fitMode = 0;
        state.zoom = 1;

        globalThis.onRenderPage(2);

        expect(pagesElement.children[4].style.height).toBe("200px");
    });

    it("keeps the previous frame on screen while a re-render is in flight", async () => {
        const { state, pagesElement } = await setupViewer({ pageCount: 2 });
        await flushPromises();
        const [canvas] = pagesElement.children[0].children;
        expect(canvas.width).toBeGreaterThan(0);

        state.fitMode = 0;
        state.zoom = 1;
        globalThis.onRenderPage(2);

        // The new render has only just started; clearing the visible canvas
        // now would blank the page until the render completes (upstream
        // review: "viewer would very often suddenly become a blank page").
        expect(canvas.width).toBeGreaterThan(0);

        await flushPromises();
        expect(canvas.width).toBeGreaterThan(0);
        expect(state.renderCalls.length).toBeGreaterThanOrEqual(2);
    });

    it("publishes the fit zoom computed for the new layout", async () => {
        const { state } = await setupViewer({ pageCount: 1, fitMode: 1 });
        state.fitMode = 2;

        globalThis.onRenderPage(0);

        expect(state.zoomReports.at(-1)).toBe(1);
    });

    it("keeps the requested page anchored across a relayout", async () => {
        const { state } = await setupViewer({
            continuous: true,
            currentPage: 3,
            pageCount: 3,
            fitMode: 1,
        });
        expect(globalThis.scrollY).toBe(200);
        state.fitMode = 2;

        globalThis.onRenderPage(0);

        expect(state.currentPage).toBe(3);
        expect(globalThis.scrollY).toBe(400);
    });

    it("re-anchors the current page when continuous mode changes", async () => {
        const { state } = await setupViewer({
            continuous: false,
            currentPage: 3,
            pageCount: 3,
        });
        expect(globalThis.scrollY).toBe(0);

        state.continuous = true;
        globalThis.setContinuousMode();
        expect(globalThis.scrollY).toBe(200);

        state.continuous = false;
        globalThis.setContinuousMode();
        expect(globalThis.scrollY).toBe(0);
        expect(state.currentPage).toBe(3);
    });

    it("starts rendering progressively and survives a later page failure", async () => {
        let rejectSecondPage;
        const secondPage = new Promise((resolve, reject) => {
            rejectSecondPage = reject;
        });
        const { state, pagesElement } = await setupViewer({
            pageCount: 3,
            getPage: (pageNumber, viewerState) => {
                if (pageNumber === 2) return secondPage;
                return Promise.resolve(fakePage(pageNumber, viewerState));
            },
        });

        expect(pagesElement.children.map((page) => page.dataset.page)).toEqual(["1"]);
        expect(state.renderCalls).toContain(1);

        rejectSecondPage(new Error("damaged page"));
        await flushPromises();

        expect(pagesElement.children.map((page) => page.dataset.page)).toEqual(["1", "3"]);
    });

    it("redirects a failed page request and keeps scroll tracking live", async () => {
        const { state } = await setupViewer({
            pageCount: 3,
            getPage: (pageNumber, viewerState) => pageNumber === 2
                ? Promise.reject(new Error("damaged page"))
                : Promise.resolve(fakePage(pageNumber, viewerState)),
        });

        globalThis.scrollToPage(3);
        expect(state.currentPage).toBe(3);

        // Android publishes the requested page before invoking onRenderPage.
        // The failed target must redirect by page proximity, not merely keep
        // whichever readable page happens to be visible.
        state.currentPage = 2;
        globalThis.onRenderPage(0);
        expect(state.currentPage).toBe(1);

        globalThis.scrollBy(0, 100);
        globalThis.onscroll();
        await delay(200);

        expect(state.currentPage).toBe(3);
    });

    it("keeps a late-page request sticky until progressive setup reaches it", async () => {
        let resolveSecondPage;
        const secondPage = new Promise((resolve) => {
            resolveSecondPage = resolve;
        });
        const { state } = await setupViewer({
            pageCount: 3,
            getPage: (pageNumber, viewerState) => pageNumber === 2
                ? secondPage
                : Promise.resolve(fakePage(pageNumber, viewerState)),
        });

        globalThis.scrollToPage(3);
        globalThis.onscroll();
        await delay(200);

        expect(state.currentPage).toBe(3);

        resolveSecondPage(fakePage(2, state));
        await flushPromises();

        expect(globalThis.scrollY).toBeGreaterThan(0);
        expect(state.currentPage).toBe(3);
    });
});
