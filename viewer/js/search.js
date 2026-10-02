// U+002D hyphen-minus, U+2010 hyphen, U+2011 non-breaking hyphen.
const EOL_HYPHENS = "-‐‑";

// U+00AD soft hyphen is completely ignorable in root collation, so a line-broken word joins
// back together for the matcher without changing any offset.
const SOFT_HYPHEN = "­";

/**
 * Maps the text items of one page to the strings the matcher concatenates.
 *
 * Item i contributes exactly `items[i].str.length + (hasEOL ? 1 : 0)` characters, so every
 * offset below `items[i].str.length` is also a valid offset into the text node of
 * `TextLayer.textDivs[i]`. The whole offset scheme rests on that invariant.
 *
 * Items that do not end a line are concatenated with no separator, because pdf.js splits runs
 * mid-word on font and kerning changes. A line-final hyphen is replaced by two soft hyphens so
 * "hyphen-\nation" matches "hyphenation"; every other line end becomes a single space, because
 * U+000A is not collation-equal to a space.
 *
 * @param {Array} items text content items from page.getTextContent()
 * @return {string[]} one string per item, index-aligned with TextLayer.textDivs
 */
export function pageTextItems(items) {
    return items.map((item) => {
        const str = item.str;
        if (!item.hasEOL) {
            return str;
        }
        if (str.length > 0 && EOL_HYPHENS.includes(str[str.length - 1])) {
            return str.slice(0, -1) + SOFT_HYPHEN + SOFT_HYPHEN;
        }
        return str + " ";
    });
}
