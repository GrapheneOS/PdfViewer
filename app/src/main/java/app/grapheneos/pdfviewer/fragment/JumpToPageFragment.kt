package app.grapheneos.pdfviewer.fragment

import android.app.Dialog
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.widget.EditText
import android.widget.FrameLayout
import androidx.fragment.app.DialogFragment
import app.grapheneos.pdfviewer.PdfViewer
import app.grapheneos.pdfviewer.R
import com.google.android.material.dialog.MaterialAlertDialogBuilder

class JumpToPageFragment : DialogFragment() {

    companion object {
        const val TAG = "JumpToPageFragment"
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val viewerActivity = requireActivity() as PdfViewer

        val input = EditText(requireContext()).apply {
            inputType = InputType.TYPE_CLASS_NUMBER
            setText(viewerActivity.mPage.toString())
            setSelection(text.length)
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER
            )
        }

        val container = FrameLayout(requireContext()).apply {
            val padding = (16 * resources.displayMetrics.density).toInt()
            setPadding(padding, padding, padding, padding)
            addView(input)
        }

        return MaterialAlertDialogBuilder(requireContext())
            .setTitle(getString(R.string.action_jump_to_page))
            .setView(container)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                val enteredPage = input.text.toString().toIntOrNull()
                if (enteredPage != null &&
                    enteredPage in 1..viewerActivity.mNumPages
                ) {
                    viewerActivity.onJumpToPageInDocument(enteredPage)
                } else {
                    viewerActivity.onJumpToPageInDocument(viewerActivity.mNumPages)
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .create()
    }
}
