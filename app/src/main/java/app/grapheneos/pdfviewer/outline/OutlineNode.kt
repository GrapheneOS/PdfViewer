package app.grapheneos.pdfviewer.outline

import android.util.JsonReader
import java.io.StringReader

data class OutlineNode(
    val id: Int,
    val title: String,
    val pageNumber: Int,
    val children: List<OutlineNode>
) {
    companion object {
        data class IdTracker(private var currentId: Int = 0) {
            fun getAndIncrement(): Int {
                currentId++
                return currentId - 1
            }
        }

        @JvmStatic
        fun parse(input: String): List<OutlineNode> {
            val idTracker = IdTracker()
            StringReader(input).use { sr ->
                JsonReader(sr).use { reader ->
                    return parseOutlineArray(reader, idTracker)
                }
            }
        }

        private fun parseOutlineArray(reader: JsonReader, id: IdTracker): List<OutlineNode> = with(reader) {
            val topLevelNodes = arrayListOf<OutlineNode>()
            beginArray()
            while (hasNext()) {
                topLevelNodes.add(parseOutlineObject(this, id))
            }
            endArray()
            topLevelNodes
        }

        private fun parseOutlineObject(reader: JsonReader, id: IdTracker): OutlineNode = with(reader) {
            var title = ""
            var pageNumber = -1
            var children = emptyList<OutlineNode>()

            beginObject()
            while (hasNext()) {
                when (nextName()) {
                    "title" -> title = reader.nextString()
                    "pageNumber" -> pageNumber = reader.nextInt()
                    "children" -> children = parseOutlineArray(reader, id)
                    else -> reader.skipValue()
                }
            }
            endObject()

            OutlineNode(id.getAndIncrement(), title, pageNumber, children)
        }
    }
}
