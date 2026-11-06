import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

vi.mock("pdfjs-dist", () => ({
    GlobalWorkerOptions: {},
    PasswordResponses: {},
    TextLayer: vi.fn(),
    getDocument: vi.fn(),
}));

beforeEach(() => {
    vi.resetModules();
    for (const name of [
        "onresize", "onRenderPage", "isTextSelected", "getDocumentOutline",
        "abortDocumentOutline", "toggleTextLayerVisibility", "loadDocument",
    ]) {
        vi.stubGlobal(name, undefined);
    }
});

afterEach(() => {
    vi.unstubAllGlobals();
});

describe("text layer alignment on resize", () => {
    it.each([
        { width: 900, height: 1200, translation: "170px 240px" },
        { width: 300, height: 1200, translation: "5px 240px" },
        { width: 900, height: 400, translation: "170px 5px" },
        { width: 300, height: 400, translation: "5px 5px" },
    ])("keeps the text aligned with a $width x $height page", async ({ width, height, translation }) => {
        const padding = {
            paddingLeft: "20px",
            paddingRight: "10px",
            paddingTop: "40px",
            paddingBottom: "30px",
        };
        const canvas = {
            clientWidth: width + 30,
            clientHeight: height + 70,
            style: { width: `${width}px`, height: `${height}px`, ...padding },
        };
        const textLayer = { style: {} };
        vi.stubGlobal("document", {
            body: { clientWidth: 600, clientHeight: 800 },
            getElementById: (id) => ({ content: canvas, text: textLayer, container: {} })[id],
        });
        vi.stubGlobal("getComputedStyle", () => canvas.style);

        await import("./index.js");
        globalThis.onresize();

        expect(textLayer.style.translate).toBe(translation);
    });
});
