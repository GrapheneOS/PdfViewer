package org.grapheneos.pdfviewer;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.util.DisplayMetrics;
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
        void onSwipeEdgeLeft();
        void onSwipeEdgeRight();
    }

    private static final int SPAN_STEP = 150;

    @SuppressLint("ClickableViewAccessibility")
    static void attach(Activity context, View gestureView, GestureListener listener) {
        DisplayMetrics displayMetrics = new DisplayMetrics();
        context.getWindowManager().getDefaultDisplay().getMetrics(displayMetrics);

        final GestureDetector detector = new GestureDetector(context,
                new GestureDetector.SimpleOnGestureListener() {
                    @Override
                    public boolean onSingleTapUp(MotionEvent motionEvent) {
                        return listener.onTapUp();
                    }

                    @Override
                    public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
                        float diffX = e2.getX() - e1.getX();
                        if (diffX > 0 && e1.getX() < 10.0) {
                            listener.onSwipeEdgeRight();
                        } else if (diffX < 0 && e1.getX() > displayMetrics.widthPixels - 10.0) {
                            listener.onSwipeEdgeLeft();
                        } else {
                            return false;
                        }
                        return true;
                    }
                });

        final ScaleGestureDetector scaleDetector = new ScaleGestureDetector(context,
                new ScaleGestureDetector.SimpleOnScaleGestureListener() {
                    // As the zoom value is discrete we listen to scaling step and not scaling ratio
                    float initialSpan;
                    int prevNbStep;

                    @Override
                    public boolean onScaleBegin(ScaleGestureDetector detector) {
                        initialSpan = detector.getCurrentSpan();
                        prevNbStep = 0;
                        return super.onScaleBegin(detector);
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
                });

        gestureView.setOnTouchListener((view, motionEvent) -> {
            detector.onTouchEvent(motionEvent);
            scaleDetector.onTouchEvent(motionEvent);
            return false;
        });
    }

}
