package app.grapheneos.pdfviewer.fragment

import android.app.Dialog
import android.os.Bundle
import android.widget.ArrayAdapter
import androidx.fragment.app.DialogFragment
import app.grapheneos.pdfviewer.R
import com.google.android.material.dialog.MaterialAlertDialogBuilder

class DocumentPropertiesFragment : DialogFragment() {

    // TODO replace with nav args once the `PdfViewer` activity is converted to kotlin
    private val mDocumentProperties: List<String> by lazy {
        requireArguments().getStringArrayList(KEY_DOCUMENT_PROPERTIES)?.toList() ?: emptyList()
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        return MaterialAlertDialogBuilder(requireActivity())
            .setPositiveButton(android.R.string.ok, null).apply {
                if (mDocumentProperties.isNotEmpty()) {
                    setTitle(getString(R.string.action_view_document_properties))
                    setAdapter(
                        ArrayAdapter(
                            requireActivity(),
                            android.R.layout.simple_list_item_1,
                            mDocumentProperties
                        ), null
                    )
                } else {
                    setTitle(R.string.document_properties_retrieval_failed)
                }
            }
            .create()
    }

    companion object {

        const val TAG = "DocumentPropertiesFragment"
        private const val KEY_DOCUMENT_PROPERTIES = "document_properties"

        @JvmStatic
        fun newInstance(metaData: List<CharSequence>): DocumentPropertiesFragment {
            val fragment = DocumentPropertiesFragment()
            val args = Bundle()
            args.putCharSequenceArrayList(
                KEY_DOCUMENT_PROPERTIES,
                metaData as ArrayList<CharSequence>
            )
            fragment.arguments = args
            return fragment
        }
    }
}
