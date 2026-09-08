package app.grapheneos.pdfviewer.test

import androidx.test.ext.junit.runners.AndroidJUnit4
import app.grapheneos.pdfviewer.search.DocumentSearch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Pins the behaviour of the ICU collation matcher, which is the one part of search whose
 * semantics come from the platform rather than from this codebase.
 */
@RunWith(AndroidJUnit4::class)
class PdfViewerSearchMatcherTest {

    private val search = DocumentSearch()

    /** Matches as (start, length) pairs. */
    private fun find(text: String, pattern: String): List<Pair<Int, Int>> =
        search.findMatches(text, pattern).toList().chunked(2).map { it[0] to it[1] }

    private fun starts(text: String, pattern: String) = find(text, pattern).map { it.first }

    @Test
    fun findsPlainSubstring() {
        assertEquals(listOf(4 to 4), find("the MIME database", "MIME"))
    }

    @Test
    fun ignoresCase() {
        assertEquals(listOf(4 to 4), find("the mime database", "MIME"))
        assertEquals(listOf(4 to 4), find("the MIME database", "mime"))
    }

    @Test
    fun findsEveryOccurrence() {
        assertEquals(listOf(0, 8, 16), starts("cat bat cat mat cat", "cat"))
    }

    @Test
    fun matchesAreNotOverlapping() {
        assertEquals(listOf(0, 2), starts("aaaa", "aa"))
    }

    @Test
    fun reportsNothingWhenAbsent() {
        assertEquals(emptyList<Pair<Int, Int>>(), find("hello world", "zzz"))
        assertEquals(emptyList<Pair<Int, Int>>(), find("ab", "abcdef"))
    }

    @Test
    fun foldsDiacritics() {
        assertEquals(listOf(3 to 6), find("Le Résumé final", "resume"))
        assertEquals(listOf(4 to 6), find("the resume of", "Résumé"))
    }

    @Test
    fun foldsDecomposedDiacritics() {
        // "Résumé" written NFD: e + U+0301 for each accent.
        val nfd = "Le Résumé final"
        val hits = find(nfd, "resume")
        assertEquals(1, hits.size)
        assertEquals(3, hits[0].first)
        // Length is in original-string units, so it covers the combining marks too.
        assertTrue("length ${hits[0].second} should cover the combining marks", hits[0].second >= 6)
    }

    @Test
    fun foldsLigatures() {
        assertEquals(listOf(4 to 1), find("the ﬁle is here", "fi"))
    }

    @Test
    fun foldsFullWidth() {
        assertEquals(listOf(5 to 3), find("code ＡＢＣ here", "ABC"))
    }

    @Test
    fun findsCjkSubstring() {
        assertEquals(listOf(0 to 2), find("日本語の本", "日本"))
    }

    @Test
    fun softHyphenIsIgnorable() {
        // The end-of-line rule in search.js turns "hyphen-\nation" into "hyphen­­ation";
        // this is what makes a line-broken word findable as one word.
        val hits = find("hyphen­­ation", "hyphenation")
        assertEquals(1, hits.size)
        assertEquals(0, hits[0].first)
        assertEquals(13, hits[0].second)
    }

    @Test
    fun spaceSeparatorJoinsLinesForPhraseSearch() {
        // "Chapter Three" + EOL + "Page Three Content" as search.js emits it: a phrase spanning
        // the line break is findable precisely because the separator is a space.
        assertEquals(listOf(10 to 10), find("3 Chapter Three Page Three Content", "three page"))
    }

    @Test
    fun newlineIsNotEqualToSpace() {
        // Documents the reason search.js emits U+0020 and never U+000A at a line end.
        assertEquals(emptyList<Pair<Int, Int>>(), find("line\nnext", "line next"))
    }

    @Test
    fun terminatesOnWhollyIgnorablePattern() {
        // A zero-length match would otherwise loop forever.
        assertEquals(emptyList<Pair<Int, Int>>(), find("abc", "­"))
    }

    @Test
    fun toleratesEmptyInputs() {
        assertEquals(emptyList<Pair<Int, Int>>(), find("abc", ""))
        assertEquals(emptyList<Pair<Int, Int>>(), find("", "abc"))
    }

    @Test
    fun tuplesMapOffsetsBackToTextItems() {
        // Item starts: "3"@0 " "@1 "Chapter Three"@2 " "@15 "Page Three Content"@16.
        search.setQuery("three page")
        assertTrue(search.addPage(1, """["3"," ","Chapter Three"," ","Page Three Content"]"""))
        // "Three Page" is offsets 10..20, so it spans three items: the tail of "Chapter Three",
        // the synthetic end-of-line space, and the head of "Page Three Content".
        assertEquals("[[[2,8,5],[3,0,1],[4,0,4]]]", search.tuplesFor(1))
        assertEquals(1, search.stats().total)
    }

    @Test
    fun indexesMatchesAcrossPages() {
        search.setQuery("content")
        for (page in 1..4) {
            assertTrue(search.addPage(page, """["Page $page Content"]"""))
        }
        assertEquals(4, search.stats().total)
        assertEquals(1, search.countOn(3))
        assertEquals(2, search.ordinalBefore(3))
        assertEquals(1, search.firstPageFrom(1))
        assertEquals(3, search.firstPageFrom(3))
        // Wraps forward off the last page and backward off the first.
        assertEquals(2 to 0, search.step(1, 0, forward = true))
        assertEquals(1 to 0, search.step(4, 0, forward = true))
        assertEquals(4 to 0, search.step(1, 0, forward = false))
    }

    @Test
    fun aNewQueryClearsMatchCapTruncation() {
        // A single very common character can cross the match cap. If that latched, extraction
        // would stay stopped and every later query would search only the pages scanned so far.
        search.setQuery("e")
        val dense = "e ".repeat(DocumentSearch.MAX_MATCHES + 1_000)
        // false is the signal to JS that the index is full and extraction should stop.
        assertFalse("the match cap should have tripped", search.addPage(1, """["$dense"]"""))
        assertTrue(search.stats().truncated)
        search.setQuery("content")
        assertFalse("a new query must resume extraction", search.stats().truncated)
        assertTrue(search.addPage(2, """["Page Two Content"]"""))
        assertEquals(1, search.stats().total)
    }

    @Test
    fun theIndexVersionChangesWithTheQuery() {
        // The paint effect keys on this: two different queries can select the same (page, index),
        // and without a version change the highlights would keep the old query's offsets.
        search.setQuery("one")
        assertTrue(search.addPage(1, """["one two"]"""))
        val first = search.stats().version
        search.setQuery("two")
        assertNotEquals(first, search.stats().version)
    }

    @Test
    fun aQueryStartingOnAnEndOfLineSeparatorStillMapsBack() {
        // "Chapter One" + synthetic EOL space + "Two": a query starting with the space produces a
        // piece whose offset sits at the end of the item's real text.
        search.setQuery(" two")
        assertTrue(search.addPage(1, """["Chapter One ","Two"]"""))
        assertEquals(1, search.stats().total)
        // Offset 11 is past "Chapter One".length is false - it is exactly the length, so the JS
        // side clamps it to a collapsed range and skips it, and item 1 carries the visible part.
        assertEquals("[[[0,11,1],[1,0,3]]]", search.tuplesFor(1))
    }

    /**
     * Steady-state throughput. The first substantial ICU scan in a process pays a large one-time
     * cost (~12s on an x86 emulator) that a short scan does not absorb, so the first pass here is
     * untimed; what matters for search is the rate afterwards, which was ~1s per 244k characters
     * on the same emulator. The bound is loose enough not to measure whichever machine CI
     * allocates, and tight enough to catch an order-of-magnitude regression.
     */
    @Test
    fun scansALargeCorpusQuickly() {
        val corpus = "The quick brown fox jumps over the lazy dog. MIME type test. ".repeat(4000)
        assertEquals(4000, search.findMatches(corpus, "lazy dog").size / 2)
        val start = System.nanoTime()
        val hits = search.findMatches(corpus, "lazy dog")
        val ms = (System.nanoTime() - start) / 1_000_000
        assertEquals(4000, hits.size / 2)
        assertTrue("scanning ${corpus.length} chars took ${ms}ms", ms < 10_000)
    }
}
