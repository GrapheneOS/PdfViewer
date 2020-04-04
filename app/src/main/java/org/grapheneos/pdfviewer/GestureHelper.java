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
        void onZoomIn(int steps);
        void onZoomOut(int steps);
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
                    // As the zoom value is discrete we listen to scaling step and not scaling ratio
                    float SPAN_STEP = 150;
                    float initialSpan;
                    int prevNbStep;

                    @Override
                    public boolean onScaleBegin(ScaleGestureDetector detector) {
                        initialSpan = detector.getCurrentSpan();
                        prevNbStep = 0;
                        return true;
                    }

                    @Override
                    public boolean onScale(ScaleGestureDetector detector) {
                        float spanDiff = initialSpan - detector.getCurrentSpan();
                        int curNbStep = (int) (spanDiff/SPAN_STEP);
                        if (curNbStep != prevNbStep) {
                            int stepDiff = curNbStep - prevNbStep;
                            if (stepDiff > 0) {
                                listener.onZoomOut(stepDiff);
                            } else {
                                listener.onZoomIn(Math.abs(stepDiff));
                            }
                            prevNbStep = curNbStep;
                        }
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
