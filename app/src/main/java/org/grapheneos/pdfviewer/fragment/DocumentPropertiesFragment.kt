package org.grapheneos.pdfviewer.fragment

import android.os.Bundle
import android.app.Activity
import android.app.Dialog
import android.widget.ArrayAdapter

import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment

import java.util.ArrayList

import org.grapheneos.pdfviewer.R

class DocumentPropertiesFragment : DialogFragment() {
    companion object {
        const val TAG = "DocumentPropertiesFragment"
        private const val KEY_DOCUMENT_PROPERTIES = "document_properties"
        @JvmStatic
        fun newInstance(metaData: List<CharSequence?>?): DocumentPropertiesFragment {
            val fragment = DocumentPropertiesFragment()
            val args = Bundle()
            args.putCharSequenceArrayList(
                KEY_DOCUMENT_PROPERTIES,
                metaData as ArrayList<CharSequence?>?
            )
            fragment.arguments = args
            return fragment
        }
    }

    private var mDocumentProperties: List<String>? = null
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (arguments != null) {
            mDocumentProperties = requireArguments().getStringArrayList(KEY_DOCUMENT_PROPERTIES)
        }
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val activity: Activity = requireActivity()
        val dialog = AlertDialog.Builder(activity)
            .setPositiveButton(android.R.string.ok, null)

        if (mDocumentProperties != null) {
            dialog.setTitle(getString(R.string.action_view_document_properties))
            dialog.setAdapter(
                ArrayAdapter(
                    activity, android.R.layout.simple_list_item_1,
                    mDocumentProperties!!
                ), null
            )
        } else {
            dialog.setTitle(R.string.document_properties_retrieval_failed)
        }
        return dialog.create()
    }
}
