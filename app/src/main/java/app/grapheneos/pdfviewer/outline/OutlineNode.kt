package app.grapheneos.pdfviewer.outline

import android.util.JsonReader
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.isActive
import java.io.StringReader

data class OutlineNode(
    val id: Int,
    val title: String,
    val pageNumber: Int,
    val children: List<OutlineNode>
) {
    companion object {
        private const val KEY_TITLE = "t"
        private const val KEY_PAGE_NUMBER = "p"
        private const val KEY_CHILDREN = "c"

        data class IdTracker(private var currentId: Int = 0) {
            fun getAndIncrement(): Int {
                currentId++
                return currentId - 1
            }
        }

        suspend fun parse(input: String): List<OutlineNode> {
            val idTracker = IdTracker()

            StringReader(input).use { sr ->
                JsonReader(sr).use { reader ->
                    return coroutineScope { parseOutlineArray(reader, idTracker) }
                }
            }
        }

        private fun CoroutineScope.parseOutlineArray(
            reader: JsonReader,
            id: IdTracker
        ): List<OutlineNode> = with(reader) {
            val topLevelNodes = arrayListOf<OutlineNode>()
            beginArray()
            while (hasNext() && isActive) {
                topLevelNodes.add(parseOutlineObject(this, id))
            }
            endArray()
            topLevelNodes
        }

        private fun CoroutineScope.parseOutlineObject(
            reader: JsonReader,
            id: IdTracker
        ): OutlineNode = with(reader) {
            var title = ""
            var pageNumber = -1
            var children = emptyList<OutlineNode>()

            beginObject()
            while (hasNext() && isActive) {
                when (nextName()) {
                    KEY_TITLE -> title = reader.nextString()
                    KEY_PAGE_NUMBER -> pageNumber = reader.nextInt()
                    KEY_CHILDREN -> children = parseOutlineArray(reader, id)
                    else -> reader.skipValue()
                }
            }
            endObject()

            OutlineNode(id.getAndIncrement(), title, pageNumber, children)
        }
    }
}
