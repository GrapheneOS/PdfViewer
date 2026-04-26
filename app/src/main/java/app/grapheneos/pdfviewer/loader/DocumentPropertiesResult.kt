package app.grapheneos.pdfviewer.loader

/**
 * Holds the output of [DocumentPropertiesLoader]:
 * - [list]: pre-formatted, localized strings used by the document properties dialog.
 * - [documentName]: the document name resolved from raw, non-localized values
 *   (file name, falling back to the PDF title). Empty if neither is available.
 */
data class DocumentPropertiesResult(
    val list: List<CharSequence>,
    val documentName: String,
)
