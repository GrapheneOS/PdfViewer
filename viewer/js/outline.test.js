import { describe, it, expect } from "vitest";
import { getSimplifiedOutline } from "./outline.js";

// Helpers

function node(title, { items = [], dest = null } = {}) {
    return { title, items, dest };
}

function ref(num) {
    return { gen: 0, num };
}

function fakePdfDoc({ namedDests = {}, pageIndices = new Map() } = {}) {
    return {
        getDestination: async (name) => namedDests[name] ?? null,
        getPageIndex: async (pageRef) => {
            if (!pageIndices.has(pageRef.num)) {
                throw new Error(`No page index configured for ref=${pageRef.num}`);
            }
            return pageIndices.get(pageRef.num);
        },
    };
}

// Tests

describe("getSimplifiedOutline", () => {
    it("returns null for empty/null/undefined input", async () => {
        const ac = new AbortController();
        expect(await getSimplifiedOutline(null, ac, fakePdfDoc())).toBeNull();
        expect(await getSimplifiedOutline(undefined, ac, fakePdfDoc())).toBeNull();
        expect(await getSimplifiedOutline([], ac, fakePdfDoc())).toBeNull();
    });

    it("maps dest to correct page number", async () => {
        const outline = [node("Chapter", { dest: [5, null] })];

        const result = await getSimplifiedOutline(
            outline, new AbortController(), fakePdfDoc()
        );

        expect(result).toEqual([{ t: "Chapter", p: 6, c: [] }]);
    });

    it("resolves object dest by getPageIndex", async () => {
        const outline = [node("Chapter", { dest: [ref(42), null] })];
        const pdfDoc = fakePdfDoc({ pageIndices: new Map([[42, 2]]) });

        const result = await getSimplifiedOutline(
            outline, new AbortController(), pdfDoc
        );

        expect(result[0].p).toBe(3);
    });

    it("resolves named dest by getDestination then getPageIndex", async () => {
        const outline = [node("Chapter", { dest: "chapter-1" })];
        const pdfDoc = fakePdfDoc({
            namedDests: { "chapter-1": [ref(42), null] },
            pageIndices: new Map([[42, 2]]),
        });

        const result = await getSimplifiedOutline(
            outline, new AbortController(), pdfDoc
        );

        expect(result[0].p).toBe(3);
    });

    it("preserves nested hierarchy", async () => {
        const outline = [
            node("Parent", {
                dest: [0, null],
                items: [
                    node("Child1", { dest: [1, null] }),
                    node("Child2", { dest: [2, null] }),
                ],
            }),
        ];

        const result = await getSimplifiedOutline(
            outline, new AbortController(), fakePdfDoc()
        );

        expect(result).toHaveLength(1);
        expect(result[0].t).toBe("Parent");
        expect(result[0].p).toBe(1);
        expect(result[0].c.map((n) => n.t)).toEqual(["Child1", "Child2"]);
        expect(result[0].c.map((n) => n.p)).toEqual([2, 3]);
    });

    it("throws when the controller is aborted", async () => {
        const controller = new AbortController();
        const outline = [
            node("First", { dest: "first-dest" }),
            node("Second", { dest: [0, null] }),
        ];
        const pdfDoc = {
            getDestination: async () => {
                controller.abort();
                return [ref(1), null];
            },
            getPageIndex: async () => 0,
        };

        await expect(
            getSimplifiedOutline(outline, controller, pdfDoc)
        ).rejects.toThrow();
    });
});
