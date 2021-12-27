package org.grapheneos.pdfviewer;

import android.annotation.SuppressLint;
import android.content.Context;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;

/*
    The GestureHelper present a simple gesture api for the PdfViewer
*/

class GestureHelper {
    public interface GestureListener {
        boolean onTapUp();
        // Can be replaced with ratio when supported
        void onZoomIn(float value);
        void onZoomOut(float value);
        void onZoomEnd();
    }

    @SuppressLint("ClickableViewAccessibility")
    static void attach(Context context, View gestureView, GestureListener listener) {

        final GestureDetector detector = new GestureDetector(context,
                new GestureDetector.SimpleOnGestureListener() {
                    @Override
                    public boolean onSingleTapUp(MotionEvent motionEvent) {
                        return listener.onTapUp();
                    }
                });

        final ScaleGestureDetector scaleDetector = new ScaleGestureDetector(context,
                new ScaleGestureDetector.SimpleOnScaleGestureListener() {
                    final float SPAN_RATIO = 600;
                    float initialSpan;
                    float prevNbStep = 0;

                    @Override
                    public boolean onScaleBegin(ScaleGestureDetector detector) {
                        initialSpan = detector.getCurrentSpan();
                        return true;
                    }

                    @Override
                    public boolean onScale(ScaleGestureDetector detector) {
                        final float spanDiff = initialSpan - detector.getCurrentSpan();
                        final float curNbStep = spanDiff / SPAN_RATIO;

                        final float stepDiff = curNbStep - prevNbStep;
                        if (stepDiff > 0) {
                            listener.onZoomOut(stepDiff);
                        } else {
                            listener.onZoomIn(Math.abs(stepDiff));
                        }
                        prevNbStep = curNbStep;

                        return true;
                    }

                    @Override
                    public void onScaleEnd(ScaleGestureDetector detector) {
                        listener.onZoomEnd();
                    }
                });

        gestureView.setOnTouchListener((view, motionEvent) -> {
            detector.onTouchEvent(motionEvent);
            scaleDetector.onTouchEvent(motionEvent);
            return false;
        });
    }

}
