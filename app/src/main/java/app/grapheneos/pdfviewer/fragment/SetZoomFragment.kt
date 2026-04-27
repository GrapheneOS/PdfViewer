package app.grapheneos.pdfviewer.fragment

import android.app.Dialog
import android.content.DialogInterface
import android.os.Bundle
import android.view.Gravity
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import androidx.core.view.marginTop
import androidx.fragment.app.DialogFragment
import app.grapheneos.pdfviewer.PdfViewer
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlin.math.ln
import kotlin.math.pow

class SetZoomFragment(
    private var mCurrentViewerZoomRatio: Double,
    private var mMinZoomRatio: Double,
    private var mMaxZoomRatio: Double,
) : DialogFragment() {

    companion object {
        const val TAG = "SetZoomFragment"
        private const val STATE_SEEKBAR_CUR = "seekbar_cur"
        private const val STATE_SEEKBAR_MIN = "seekbar_min"
        private const val STATE_SEEKBAR_MAX = "seekbar_max"
        private const val STATE_VIEWER_CUR = "viewer_cur"
        private const val STATE_VIEWER_MIN = "viewer_min"
        private const val STATE_VIEWER_MAX = "viewer_max"
        private const val STATE_ZOOM_FOCUSX = "viewer_zoom_focusx"
        private const val STATE_ZOOM_FOCUSY = "viewer_zoom_focusy"
        private const val SEEKBAR_RESOLUTION = 1024
    }

    private val mSeekBar: SeekBar by lazy { SeekBar(requireActivity()) }
    private val mZoomLevelText: TextView by lazy { TextView(requireActivity()) }

    private var mZoomFocusX: Float = 0.0f
    public fun setZoomFocusX(value: Float) {mZoomFocusX = value}
    private var mZoomFocusY: Float = 0.0f
    public fun setZoomFocusY(value: Float) {mZoomFocusY = value}

    private fun progressToZoom(progress: Int): Double {
        val progressClip = progress.coerceAtLeast(0).coerceAtMost(SEEKBAR_RESOLUTION);
        return mMinZoomRatio * (mMaxZoomRatio / mMinZoomRatio).pow(progressClip.toDouble() / SEEKBAR_RESOLUTION)
    }

    private fun zoomToProgress(zoom: Double): Int {
        val zoomClip = zoom.coerceAtLeast(mMinZoomRatio).coerceAtMost(mMaxZoomRatio);
        return (SEEKBAR_RESOLUTION * ln(zoomClip / mMinZoomRatio) / ln(mMaxZoomRatio / mMinZoomRatio)).toInt()
    }

    fun refreshZoomText(progress: Int) {
        mZoomLevelText.text = "${(progressToZoom(progress) * 100).toInt()}%"
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {

        val viewerActivity: PdfViewer = (requireActivity() as PdfViewer)

        if (savedInstanceState != null) {
            val progress = savedInstanceState.getInt(STATE_SEEKBAR_CUR)
            mSeekBar.setMin(savedInstanceState.getInt(STATE_SEEKBAR_MIN))
            mSeekBar.setMax(savedInstanceState.getInt(STATE_SEEKBAR_MAX))
            mSeekBar.progress = progress
            refreshZoomText(progress)
            mCurrentViewerZoomRatio = savedInstanceState.getDouble(STATE_VIEWER_CUR)
            mMinZoomRatio = savedInstanceState.getDouble(STATE_VIEWER_MIN)
            mMaxZoomRatio = savedInstanceState.getDouble(STATE_VIEWER_MAX)
            mZoomFocusX = savedInstanceState.getFloat(STATE_ZOOM_FOCUSX)
            mZoomFocusY = savedInstanceState.getFloat(STATE_ZOOM_FOCUSY)
        } else {
            mSeekBar.setMin(0)
            mSeekBar.setMax(SEEKBAR_RESOLUTION)
            val progress = zoomToProgress(mCurrentViewerZoomRatio)
            mSeekBar.setProgress(progress)
            refreshZoomText(progress)
        }
        mSeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                refreshZoomText(progress)
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })
        val layout = LinearLayout(requireActivity())
        layout.orientation = LinearLayout.VERTICAL
        layout.gravity = Gravity.CENTER
        val textParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT,
        )
        textParams.setMargins(0, 24, 0, 0) // Margin above the text
        layout.addView(mZoomLevelText, textParams)
        layout.addView(
            mSeekBar, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            )
        )
        return MaterialAlertDialogBuilder(requireActivity())
            .setView(layout)
            .setPositiveButton(android.R.string.ok) { _: DialogInterface?, _: Int ->
                mSeekBar.clearFocus()
                val zoom = progressToZoom(mSeekBar.progress)
                viewerActivity.onZoomPage((zoom / mCurrentViewerZoomRatio).toFloat(), mZoomFocusX, mZoomFocusY, true)
            }
            .setNegativeButton(android.R.string.cancel, null)
            .create()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putInt(STATE_SEEKBAR_CUR, mSeekBar.progress)
        outState.putInt(STATE_SEEKBAR_MIN, mSeekBar.min)
        outState.putInt(STATE_SEEKBAR_MAX, mSeekBar.max)
        outState.putDouble(STATE_VIEWER_CUR, mCurrentViewerZoomRatio)
        outState.putDouble(STATE_VIEWER_MIN, mMinZoomRatio)
        outState.putDouble(STATE_VIEWER_MAX, mMaxZoomRatio)
        outState.putFloat(STATE_ZOOM_FOCUSX, mZoomFocusX)
        outState.putFloat(STATE_ZOOM_FOCUSY, mZoomFocusY)
    }
}
