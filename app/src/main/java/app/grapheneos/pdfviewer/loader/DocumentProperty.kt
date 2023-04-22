package app.grapheneos.pdfviewer.loader

import androidx.annotation.StringRes
import app.grapheneos.pdfviewer.R

private const val TITLE_KEY = "Title"
private const val AUTHOR_KEY = "Author"
private const val SUBJECT_KEY = "Subject"
private const val KEYWORDS_KEY = "Keywords"
private const val CREATION_DATE_KEY = "CreationDate"
private const val MODIFY_DATE_KEY = "ModDate"
private const val PRODUCER_KEY = "Producer"
private const val CREATOR_KEY = "Creator"
private const val PDF_VERSION_KEY = "PDFFormatVersion"

const val DEFAULT_VALUE = "-"

enum class DocumentProperty(
    val key: String = "",
    @StringRes val nameResource: Int,
    val isDate: Boolean = false
) {
    FileName(key = "", nameResource = R.string.file_name),
    FileSize(key = "", nameResource = R.string.file_size),
    Pages(key = "", nameResource = R.string.pages),
    Title(key = TITLE_KEY, nameResource = R.string.title),
    Author(key = AUTHOR_KEY, nameResource = R.string.author),
    Subject(key = SUBJECT_KEY, nameResource = R.string.subject),
    Keywords(key = KEYWORDS_KEY, nameResource = R.string.keywords),
    CreationDate(key = CREATION_DATE_KEY, nameResource = R.string.creation_date, isDate = true),
    ModifyDate(key = MODIFY_DATE_KEY, nameResource = R.string.modify_date, isDate = true),
    Producer(key = PRODUCER_KEY, nameResource = R.string.producer),
    Creator(key = CREATOR_KEY, nameResource = R.string.creator),
    PDFVersion(key = PDF_VERSION_KEY, nameResource = R.string.pdf_version);
}
