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
        translate: "",
        setProperty(name, value) {
            properties.set(name, value);
        },
        getPropertyValue(name) {
            return properties.get(name) || "";
        },
    };
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
        return { scale() {} };
    }

    getBoundingClientRect() {
        if (this.style.display === "none") {
            return { top: 0, bottom: 0, width: 0, height: 0 };
        }
        const siblings = this.parentElement ? this.parentElement.children : [];
        let documentTop = 0;
        for (const sibling of siblings) {
            if (sibling === this) break;
            if (sibling.style.display !== "none") {
                documentTop += Number.parseFloat(sibling.style.height) || 0;
            }
        }
        const height = Number.parseFloat(this.style.height) || 0;
        const top = documentTop - globalThis.scrollY;
        return { top, bottom: top + height, height, width: Number.parseFloat(this.style.width) || 0 };
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
    pageCount = 3,
    getPage,
} = {}) {
    vi.resetModules();
    const state = {
        continuous,
        currentPage,
        fitMode,
        zoom: 0.5,
        orientation: 0,
        renderCalls: [],
        scrollCalls: [],
        scrolledPages: [],
        zoomReports: [],
    };
    const environment = { scrolledPages: state.scrolledPages };
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
        getZoomFocusX: () => 10,
        getZoomFocusY: () => 20,
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

    it("compensates each pinch event from the previously requested zoom", async () => {
        const { state } = await setupViewer({ continuous: false, currentPage: 1 });
        state.fitMode = 0;

        state.zoom = 0.6;
        globalThis.onRenderPage(2);
        state.zoom = 0.7;
        globalThis.onRenderPage(2);
        globalThis.onRenderPage(1);

        expect(state.scrollCalls).toHaveLength(3);
        expect(state.scrollCalls[0][0]).toBeCloseTo(2);
        expect(state.scrollCalls[0][1]).toBeCloseTo(4);
        expect(state.scrollCalls[1][0]).toBeCloseTo(2);
        expect(state.scrollCalls[1][1]).toBeCloseTo(4);
        expect(state.scrollCalls[2]).toEqual([0, 0]);
    });

    it("resizes far placeholders when free zoom changes", async () => {
        const { state, pagesElement } = await setupViewer({ pageCount: 5 });
        state.fitMode = 0;
        state.zoom = 1;

        globalThis.onRenderPage(2);

        expect(pagesElement.children[4].style.height).toBe("200px");
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
