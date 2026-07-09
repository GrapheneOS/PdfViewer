package app.grapheneos.pdfviewer

import android.annotation.SuppressLint
import android.content.Context
import android.view.InputDevice
import android.view.GestureDetector
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import kotlin.math.roundToInt

/*
 * The GestureHelper present a simple gesture api for the PdfViewer
 */

object GestureHelper {
    interface GestureListener {
        fun onTapUp(): Boolean
        fun onFling(e1: MotionEvent?, e2: MotionEvent, velocityX: Float, velocityY: Float): Boolean
        fun onZoom(scaleFactor: Float, focusX: Float, focusY: Float)
        fun onCtrlMouseWheelZoom(zoomIn: Boolean, focusX: Float, focusY: Float)
        fun onZoomEnd()
    }

    @SuppressLint("ClickableViewAccessibility")
    fun attach(context: Context, gestureView: View, listener: GestureListener) {
        var wasScaling = false
        var wheelTickRemainder = 0f

        val scaleDetector = ScaleGestureDetector(
            context,
            object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
                override fun onScale(detector: ScaleGestureDetector): Boolean {
                    listener.onZoom(detector.scaleFactor, detector.focusX, detector.focusY)
                    return true
                }

                override fun onScaleEnd(detector: ScaleGestureDetector) {
                    listener.onZoomEnd()
                }
            })

        val detector = GestureDetector(
            context,
            object : GestureDetector.SimpleOnGestureListener() {
                override fun onSingleTapUp(e: MotionEvent): Boolean {
                    return listener.onTapUp()
                }

                override fun onFling(
                    e1: MotionEvent?,
                    e2: MotionEvent,
                    velocityX: Float,
                    velocityY: Float
                ): Boolean {
                    if (wasScaling) return false
                    return listener.onFling(e1, e2, velocityX, velocityY)
                }
            })

        gestureView.setOnTouchListener { _, event ->
            if (event.actionMasked == MotionEvent.ACTION_DOWN) {
                wasScaling = false
            }

            detector.onTouchEvent(event)
            scaleDetector.onTouchEvent(event)

            if (scaleDetector.isInProgress) {
                wasScaling = true
            }

            false
        }

        gestureView.setOnGenericMotionListener { _, event ->
            if (!isCtrlPhysicalMouseWheelEvent(event)) {
                return@setOnGenericMotionListener false
            }

            wheelTickRemainder += event.getAxisValue(MotionEvent.AXIS_VSCROLL)
            val wholeTickDelta = wheelTickRemainder.roundToInt()
            wheelTickRemainder -= wholeTickDelta
            if (wholeTickDelta != 0) {
                listener.onCtrlMouseWheelZoom(
                    zoomIn = wholeTickDelta > 0,
                    focusX = event.x,
                    focusY = event.y
                )
            }

            true
        }
    }

    private fun isCtrlPhysicalMouseWheelEvent(event: MotionEvent): Boolean {
        return event.actionMasked == MotionEvent.ACTION_SCROLL &&
                event.pointerCount > 0 &&
                (event.metaState and KeyEvent.META_CTRL_ON) != 0 &&
                (event.source and InputDevice.SOURCE_MOUSE) == InputDevice.SOURCE_MOUSE &&
                event.getToolType(0) == MotionEvent.TOOL_TYPE_MOUSE &&
                event.getAxisValue(MotionEvent.AXIS_VSCROLL) != 0f
    }
}
