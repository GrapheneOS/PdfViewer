package app.grapheneos.pdfviewer

import android.annotation.SuppressLint
import android.content.Context
import android.view.GestureDetector
import android.view.GestureDetector.SimpleOnGestureListener
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.ScaleGestureDetector.SimpleOnScaleGestureListener
import android.view.View
import kotlin.math.abs

/*
   The GestureHelper present a simple gesture api for the PdfViewer
*/
internal object GestureHelper {
    @SuppressLint("ClickableViewAccessibility")
    fun attach(context: Context?, gestureView: View, listener: GestureListener) {
        val detector = GestureDetector(context,
            object : SimpleOnGestureListener() {
                override fun onSingleTapUp(motionEvent: MotionEvent): Boolean {
                    return listener.onTapUp()
                }
            })

        val scaleDetector = ScaleGestureDetector(
            context!!,
            object : SimpleOnScaleGestureListener() {
                val SPAN_RATIO: Float = 600f
                var initialSpan: Float = 0f
                var prevNbStep: Float = 0f

                override fun onScaleBegin(detector: ScaleGestureDetector): Boolean {
                    initialSpan = detector.currentSpan
                    prevNbStep = 0f
                    return true
                }

                override fun onScale(detector: ScaleGestureDetector): Boolean {
                    val spanDiff = initialSpan - detector.currentSpan
                    val curNbStep = spanDiff / SPAN_RATIO

                    val stepDiff = curNbStep - prevNbStep
                    if (stepDiff > 0) {
                        listener.onZoomOut(stepDiff)
                    } else {
                        listener.onZoomIn(abs(stepDiff.toDouble()).toFloat())
                    }
                    prevNbStep = curNbStep

                    return true
                }

                override fun onScaleEnd(detector: ScaleGestureDetector) {
                    listener.onZoomEnd()
                }
            })

        gestureView.setOnTouchListener { view: View?, motionEvent: MotionEvent? ->
            detector.onTouchEvent(motionEvent!!)
            scaleDetector.onTouchEvent(motionEvent)
            false
        }
    }

    interface GestureListener {
        fun onTapUp(): Boolean

        // Can be replaced with ratio when supported
        fun onZoomIn(value: Float)
        fun onZoomOut(value: Float)
        fun onZoomEnd()
    }
}
