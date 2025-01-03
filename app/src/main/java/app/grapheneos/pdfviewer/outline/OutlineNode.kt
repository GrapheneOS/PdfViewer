package app.grapheneos.pdfviewer.outline

import android.util.JsonReader
import java.io.StringReader

data class OutlineNode(
    val title: String,
    val pageNumber: Int,
    val children: List<OutlineNode>
) {
    companion object {
        @JvmStatic
        fun parse(input: String): List<OutlineNode> {
            StringReader(input).use { sr ->
                JsonReader(sr).use { reader ->
                    return parseOutlineArray(reader)
                }
            }
        }

        private fun parseOutlineArray(reader: JsonReader): List<OutlineNode> = with(reader) {
            val topLevelNodes = arrayListOf<OutlineNode>()
            beginArray()
            while (hasNext()) {
                topLevelNodes.add(parseOutlineObject(this))
            }
            endArray()
            topLevelNodes
        }

        private fun parseOutlineObject(reader: JsonReader): OutlineNode = with(reader) {
            var title = ""
            var pageNumber = -1
            var children = emptyList<OutlineNode>()

            beginObject()
            while (hasNext()) {
                when (nextName()) {
                    "title" -> title = reader.nextString()
                    "pageNumber" -> pageNumber = reader.nextInt()
                    "children" -> children = parseOutlineArray(reader)
                    else -> reader.skipValue()
                }
            }
            endObject()

            OutlineNode(title, pageNumber, children)
        }
    }
}