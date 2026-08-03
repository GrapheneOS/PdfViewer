package app.grapheneos.pdfviewer.search

import android.icu.text.Collator
import android.icu.text.RuleBasedCollator
import android.icu.text.SearchIterator
import android.icu.text.StringSearch
import android.icu.util.ULocale
import androidx.annotation.VisibleForTesting
import org.json.JSONArray
import java.text.StringCharacterIterator
import java.util.TreeMap

/** One page of extracted text plus the start offset of each text item within it. */
private class PageText(val text: String, val starts: IntArray)

/**
 * The whole search engine: corpus, matcher and match index.
 *
 * Matching is [StringSearch] at [Collator.PRIMARY], which folds case, diacritics, ligatures,
 * width and kana the way Chromium's find-in-page does, and reports offsets into the *original*
 * string. That is what removes any need for a normalized copy of the text and a map back to it.
 *
 * Holds no Android UI or coroutine types so it can be tested directly.
 */
class DocumentSearch {

    companion object {
        // ponytail: two caps, one behaviour. 8M chars (~16 MB UTF-16) covers a 2000 page dense
        // book; past either cap extraction stops and the count renders as "n/m+". Upgrade path
        // if any retention is objectionable: drop the corpus in onStop and re-extract, at the
        // cost of a full worker sweep on the next keystroke.
        const val MAX_CORPUS_CHARS = 8_000_000
        const val MAX_MATCHES = 100_000

        // pdf.js takes the page count from the catalog's /Count, which it trusts after probing
        // only the last page, so a kilobyte of hostile page tree can claim a hundred million
        // pages. Text-free pages cost no characters, so the character cap alone would never stop
        // the sweep; this bounds it by entries as well.
        const val MAX_PAGES = 50_000
    }

    // ULocale.ROOT, not the user locale: a Turkish collator makes "i" != "I", which is correct
    // for sorting Turkish and wrong for searching a document.
    //
    // Collation already compares canonically equivalent text through its internal FCD check, so
    // precomposed and combining-mark forms match with or without CANONICAL_DECOMPOSITION.
    // Measured on API 36: byte-identical match offsets for NFC/NFD Latin, Hangul, Vietnamese,
    // Arabic, ligatures, eszett, full width and CJK. NO_DECOMPOSITION is the root default and
    // skips a normalization pass PDF text never needs, so it is set explicitly rather than
    // inherited.
    //
    // ponytail: matching runs inline on the WebView binder thread, which blocks the JS
    // extraction loop until it returns. That is deliberate backpressure and costs microseconds
    // for a typical 2 KB page. ICU does pay a large one-time cost on its first substantial
    // scan (~12s on an x86 emulator, then ~1s per 244k chars), which lands on the first page of
    // the first query; if that is ever measurable on real hardware, warm the collator on a
    // background thread when the search bar opens.
    private val collator = (Collator.getInstance(ULocale.ROOT) as RuleBasedCollator).apply {
        strength = Collator.PRIMARY
        decomposition = Collator.NO_DECOMPOSITION
    }

    private val lock = Any()
    private val pages = HashMap<Int, PageText>()
    /** page -> flat [start, length, start, length, ...] in that page's text. */
    private val matches = TreeMap<Int, IntArray>()
    private var query = ""
    private var chars = 0
    private var total = 0
    private var truncated = false
    private var version = 0

    /**
     * Everything the UI needs about the index, read under a single lock acquisition. [version]
     * changes whenever the query does, so consumers can tell "same active match, different query"
     * apart from "nothing happened".
     */
    class Stats(
        val total: Int,
        val scannedPages: Int,
        val truncated: Boolean,
        val version: Int
    )

    fun stats(): Stats = synchronized(lock) { Stats(total, pages.size, truncated, version) }

    fun clear() = synchronized(lock) {
        pages.clear()
        matches.clear()
        total = 0
        chars = 0
        truncated = false
        query = ""
        version++
    }

    fun setQuery(value: String) = synchronized(lock) {
        query = value
        matches.clear()
        total = 0
        version++
        // Only the corpus cap is sticky. A single very common character can cross MAX_MATCHES on
        // a big book; if that latched, extraction would stay stopped for the rest of the session
        // and every later query would silently search only the pages scanned before it.
        truncated = chars >= MAX_CORPUS_CHARS || pages.size >= MAX_PAGES
    }

    /** True when [value] is already the indexed query, so a re-scan would be wasted work. */
    fun isIndexed(value: String): Boolean =
        synchronized(lock) { value.isNotEmpty() && value == query && pages.isNotEmpty() }

    /**
     * Adds one page of extracted text. Returns false once a cap is reached, which is the
     * signal for the extraction loop in JS to stop.
     */
    fun addPage(page: Int, itemsJson: String): Boolean {
        val pending: String
        synchronized(lock) {
            // Checked before parsing, so a page that arrives after the cap cannot make the
            // binder thread materialise it.
            if (truncated) return false
            if (pages.containsKey(page)) return true
            pending = query
        }
        // Parsed outside the lock: this runs on a WebView binder thread and the parse is by far
        // the slowest part of the call.
        val array = JSONArray(itemsJson)
        val builder = StringBuilder()
        val starts = IntArray(array.length())
        for (i in 0 until array.length()) {
            starts[i] = builder.length
            builder.append(array.getString(i))
        }
        val text = PageText(builder.toString(), starts)
        // Matched outside the lock too: see matchPage. Retried rather than abandoned if the query
        // changed meanwhile, because the scan loop that runSearch starts skips pages that were not
        // yet in the corpus when it passed them — dropping the result here would leave this page
        // unmatched for the rest of the query's life.
        var attempt = pending
        // Bounded: this runs on a binder thread, and the retry only exists to cover a query that
        // changed during one page's match pass. Four consecutive changes inside a few milliseconds
        // does not happen; if it somehow did, the page lands unmatched and the next keystroke
        // re-matches it.
        repeat(4) {
            val found = if (attempt.isEmpty()) IntArray(0) else findMatches(text.text, attempt)
            synchronized(lock) {
                if (truncated || pages.containsKey(page)) return !truncated
                if (attempt != query) {
                    attempt = query
                    return@synchronized
                }
                pages[page] = text
                chars += text.text.length
                store(page, found)
                if (chars >= MAX_CORPUS_CHARS || total >= MAX_MATCHES || pages.size >= MAX_PAGES) {
                    truncated = true
                    return false
                }
                return true
            }
        }
        // Retries exhausted: still record the text, so the page is searchable from the next pass.
        synchronized(lock) {
            if (truncated || pages.containsKey(page)) return !truncated
            pages[page] = text
            chars += text.text.length
            if (chars >= MAX_CORPUS_CHARS || pages.size >= MAX_PAGES) {
                truncated = true
                return false
            }
            return true
        }
    }

    /**
     * No-op for a page that has not been extracted yet; it is matched on arrival instead.
     *
     * The ICU pass runs outside the lock. Holding it across a scan would make every main-thread
     * caller ([stats], [step], [setQuery]) wait for that scan, which is an ANR on the first
     * search of a process or on a page carrying a book's worth of text. [PageText] is immutable,
     * so matching a snapshot is safe; the result is dropped if the query moved on meanwhile.
     */
    fun matchPage(page: Int) {
        val text: PageText
        val pending: String
        synchronized(lock) {
            text = pages[page] ?: return
            pending = query
            if (pending.isEmpty()) return
        }
        val found = findMatches(text.text, pending)
        synchronized(lock) {
            if (pending == query) store(page, found)
        }
    }

    private fun store(page: Int, found: IntArray) {
        if (found.isEmpty()) matches.remove(page) else matches[page] = found
        // Recomputed rather than accumulated, so matching the same page twice cannot double count.
        total = matches.values.sumOf { it.size / 2 }
    }

    @VisibleForTesting
    fun findMatches(text: String, pattern: String): IntArray {
        if (pattern.isEmpty() || text.isEmpty()) return IntArray(0)
        val out = ArrayList<Int>()
        try {
            val search = StringSearch(pattern, StringCharacterIterator(text), collator, null)
            var index = search.first()
            while (index != SearchIterator.DONE) {
                val length = search.matchLength
                // A wholly collation-ignorable pattern (a lone soft hyphen, say) matches
                // everywhere with zero length and would otherwise spin forever.
                if (length <= 0) break
                out.add(index)
                out.add(length)
                if (out.size / 2 >= MAX_MATCHES) break
                index = search.next()
            }
        } catch (_: IllegalArgumentException) {
            // ICU rejects some degenerate patterns outright.
            return IntArray(0)
        }
        return out.toIntArray()
    }

    /** Snapshot of the page numbers actually held, so callers never loop over a claimed count. */
    fun pageNumbers(): List<Int> = synchronized(lock) { pages.keys.sorted() }

    fun countOn(page: Int): Int = synchronized(lock) { (matches[page]?.size ?: 0) / 2 }

    fun ordinalBefore(page: Int): Int =
        synchronized(lock) { matches.headMap(page).values.sumOf { it.size / 2 } }

    fun firstPageFrom(page: Int): Int? =
        synchronized(lock) { matches.ceilingKey(page) ?: matches.firstEntry()?.key }

    /** Next/previous match as (page, indexOnPage), wrapping around the document. */
    fun step(page: Int, index: Int, forward: Boolean): Pair<Int, Int>? = synchronized(lock) {
        if (matches.isEmpty()) return null
        val here = matches[page]
        if (here != null) {
            val next = index + if (forward) 1 else -1
            if (next >= 0 && next < here.size / 2) return page to next
        }
        val target = if (forward) {
            matches.higherKey(page) ?: matches.firstKey()
        } else {
            matches.lowerKey(page) ?: matches.lastKey()
        }
        return target to if (forward) 0 else matches[target]!!.size / 2 - 1
    }

    /**
     * The current page's matches as `[[[itemIndex,offsetInItem,length],...],...]` — one inner
     * array per match, one triple per text item that match spans. Digits, commas and brackets
     * only, so it is safe to inline into an evaluateJavascript call.
     */
    fun tuplesFor(page: Int): String = synchronized(lock) {
        val flat = matches[page] ?: return "[]"
        val text = pages[page] ?: return "[]"
        val starts = text.starts
        val out = StringBuilder("[")
        var m = 0
        while (m < flat.size) {
            if (m > 0) out.append(',')
            out.append('[')
            val from = flat[m]
            val to = from + flat[m + 1]
            var item = starts.binarySearch(from).let { if (it >= 0) it else -it - 2 }
                .coerceAtLeast(0)
            var first = true
            while (item < starts.size && starts[item] < to) {
                val itemEnd = if (item + 1 < starts.size) starts[item + 1] else text.text.length
                val begin = maxOf(from, starts[item])
                val end = minOf(to, itemEnd)
                if (end > begin) {
                    if (!first) out.append(',')
                    out.append('[').append(item).append(',')
                        .append(begin - starts[item]).append(',')
                        .append(end - begin).append(']')
                    first = false
                }
                item++
            }
            out.append(']')
            m += 2
        }
        return out.append(']').toString()
    }
}
