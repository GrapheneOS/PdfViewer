package org.grapheneos.pdfviewer.fragment

import android.app.AlertDialog
import android.app.Dialog
import android.os.Bundle
import android.view.Gravity
import android.widget.FrameLayout
import android.widget.NumberPicker
import androidx.fragment.app.DialogFragment
import org.grapheneos.pdfviewer.PdfViewer

private const val STATE_PICKER_CUR = "picker_cur"
private const val STATE_PICKER_MIN = "picker_min"
private const val STATE_PICKER_MAX = "picker_max"

class JumpToPageFragment : DialogFragment() {
    private lateinit var mPicker: NumberPicker

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        mPicker = NumberPicker(context).apply {
            minValue = savedInstanceState?.getInt(STATE_PICKER_MIN)
                ?: 1
            maxValue = savedInstanceState?.getInt(STATE_PICKER_MAX)
                ?: (activity as PdfViewer).mNumPages
            value = savedInstanceState?.getInt(STATE_PICKER_CUR)
                ?: (activity as PdfViewer).mPage
        }

        val layout = FrameLayout(requireContext())
        layout.addView(
            mPicker,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER
            )
        )

        return AlertDialog.Builder(context)
            .setView(layout)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                mPicker.clearFocus()
                (activity as PdfViewer).onJumpToPageInDocument(mPicker.value)
            }
            .setNegativeButton(android.R.string.cancel, null)
            .create()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.apply {
            putInt(STATE_PICKER_MIN, mPicker.minValue)
            putInt(STATE_PICKER_MAX, mPicker.maxValue)
            putInt(STATE_PICKER_CUR, mPicker.value)
        }
    }

    companion object {
        const val TAG = "JumpToPageFragment"
    }
}