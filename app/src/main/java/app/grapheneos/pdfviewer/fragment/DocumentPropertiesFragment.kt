package app.grapheneos.pdfviewer.fragment

import android.app.Dialog
import android.graphics.Typeface
import android.os.Bundle
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.StyleSpan
import android.widget.ArrayAdapter
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.activityViewModels
import app.grapheneos.pdfviewer.R
import app.grapheneos.pdfviewer.loader.DocumentProperty
import app.grapheneos.pdfviewer.viewModel.PdfViewModel
import com.google.android.material.dialog.MaterialAlertDialogBuilder

class DocumentPropertiesFragment : DialogFragment() {

    private val viewModel by activityViewModels<PdfViewModel>()

    private fun formatProperties(properties: Map<DocumentProperty, String>): List<CharSequence> {
        return properties.map { (property, value) ->
            val name = getString(property.nameResource)
            SpannableStringBuilder()
                .append(name)
                .append(":\n")
                .append(value)
                .apply {
                    setSpan(
                        StyleSpan(Typeface.BOLD),
                        0,
                        name.length,
                        Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                    )
                }
        }
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val properties = viewModel.documentProperties.value

        return MaterialAlertDialogBuilder(requireActivity())
            .setPositiveButton(android.R.string.ok, null).apply {
                if (!properties.isNullOrEmpty()) {
                    setTitle(getString(R.string.action_view_document_properties))
                    setAdapter(
                        ArrayAdapter(
                            requireActivity(),
                            android.R.layout.simple_list_item_1,
                            formatProperties(properties)
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

        @JvmStatic
        fun newInstance() = DocumentPropertiesFragment()
    }
}
