package app.grapheneos.pdfviewer.fragment

import android.app.Dialog
import android.content.DialogInterface
import android.os.Bundle
import android.view.Gravity
import android.widget.FrameLayout
import android.widget.NumberPicker
import androidx.fragment.app.DialogFragment
import app.grapheneos.pdfviewer.PdfViewer
import com.google.android.material.dialog.MaterialAlertDialogBuilder

class JumpToPageFragment : DialogFragment() {

    companion object {
        const val TAG = "JumpToPageFragment"
        private const val STATE_PICKER_CUR = "picker_cur"
        private const val STATE_PICKER_MIN = "picker_min"
        private const val STATE_PICKER_MAX = "picker_max"
    }

    private val picker: NumberPicker by lazy { NumberPicker(requireActivity()) }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {

        val viewerActivity: PdfViewer = (requireActivity() as PdfViewer)

        if (savedInstanceState != null) {
            picker.minValue = savedInstanceState.getInt(STATE_PICKER_MIN)
            picker.maxValue = savedInstanceState.getInt(STATE_PICKER_MAX)
            picker.value = savedInstanceState.getInt(STATE_PICKER_CUR)
        } else {
            picker.minValue = 1
            picker.maxValue = viewerActivity.viewModel.numPages
            picker.value = viewerActivity.viewModel.page
        }
        val layout = FrameLayout(requireActivity())
        layout.addView(
            picker, FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER
            )
        )
        return MaterialAlertDialogBuilder(requireActivity())
            .setView(layout)
            .setPositiveButton(android.R.string.ok) { _: DialogInterface?, _: Int ->
                picker.clearFocus()
                viewerActivity.onJumpToPageInDocument(picker.value)
            }
            .setNegativeButton(android.R.string.cancel, null)
            .create()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putInt(STATE_PICKER_MIN, picker.minValue)
        outState.putInt(STATE_PICKER_MAX, picker.maxValue)
        outState.putInt(STATE_PICKER_CUR, picker.value)
    }
}
