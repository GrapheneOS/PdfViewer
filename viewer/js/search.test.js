import { describe, expect, test } from "vitest";
import { pageTextItems } from "./search.js";

const SHY = "­";

function items(...specs) {
    return specs.map(([str, hasEOL]) => ({ str, hasEOL }));
}

describe("pageTextItems", () => {
    test("items that do not end a line pass through unchanged", () => {
        expect(pageTextItems(items(["Chap", false], ["ter", false]))).toEqual(["Chap", "ter"]);
    });

    test("a line end becomes a single space", () => {
        expect(pageTextItems(items(["Chapter One", true]))).toEqual(["Chapter One "]);
    });

    test("an empty item that ends a line still yields a separator", () => {
        expect(pageTextItems(items(["", true]))).toEqual([" "]);
    });

    test.each(["-", "‐", "‑"])("a line-final %j joins the word back up", (hyphen) => {
        expect(pageTextItems(items([`hyphen${hyphen}`, true]))).toEqual([`hyphen${SHY}${SHY}`]);
    });

    test("a hyphen that does not end a line is left alone", () => {
        expect(pageTextItems(items(["e-mail", false]))).toEqual(["e-mail"]);
    });

    test("a lone hyphen ending a line does not underflow", () => {
        expect(pageTextItems(items(["-", true]))).toEqual([`${SHY}${SHY}`]);
    });

    // The load-bearing invariant: offsets below str.length stay valid in the matching corpus,
    // which is what lets Kotlin return per-item offsets that index the text layer directly.
    test("every item contributes str.length + (hasEOL ? 1 : 0) characters", () => {
        const input = items(
            ["Chap", false], ["ter One", true], ["", true], ["hyphen-", true],
            ["‐", true], ["Page One Content", false], ["ﬁle", true]
        );
        const out = pageTextItems(input);
        for (const [i, item] of input.entries()) {
            expect(out[i].length).toBe(item.str.length + (item.hasEOL ? 1 : 0));
        }
    });

    // Real item list of page 3 of app/src/androidTest/assets/test-multipage.pdf. The empty
    // hasEOL item is the one whose textDivs entry is never appended to the DOM, so it is also
    // the fixture that pins the null-firstChild guard in index.js.
    test("joins a real page into searchable text", () => {
        const page3 = items(
            ["3", false], [" ", false], ["Chapter Three", false],
            ["", true], ["Page Three Content", false]
        );
        expect(pageTextItems(page3).join("")).toBe("3 Chapter Three Page Three Content");
    });
});
