package app.grapheneos.pdfviewer;

import android.annotation.SuppressLint;
import android.content.Context;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;

import androidx.annotation.NonNull;

/*
    The GestureHelper present a simple gesture api for the PdfViewer
*/

class GestureHelper {
    public interface GestureListener {
        boolean onTapUp();

        void onSwipeLeft();
        void onSwipeRight();

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

                    // inspired by https://www.geeksforgeeks.org/how-to-detect-swipe-direction-in-android/
                    @Override
                    public boolean onFling(@NonNull MotionEvent e1, @NonNull MotionEvent e2, float velocityX, float velocityY) {
                        final int swipeThreshold = 100;
                        final int swipeVelocityThreshold = 100;

                        final float diffX = e2.getX() - e1.getX();
                        final float absDiffX = Math.abs(diffX);
                        final float diffY = e2.getY() - e1.getY();
                        final float absDiffY = Math.abs(diffY);

                        if (absDiffX > absDiffY // only handle horizontal swipe
                                && absDiffX > swipeThreshold
                                && Math.abs(velocityX) > swipeVelocityThreshold) {
                            if (diffX > 0) {
                                listener.onSwipeRight();
                            } else {
                                listener.onSwipeLeft();
                            }
                            return true;
                        }

                        return false;
                    }
                });

        final ScaleGestureDetector scaleDetector = new ScaleGestureDetector(context,
                new ScaleGestureDetector.SimpleOnScaleGestureListener() {
                    final float SPAN_RATIO = 600;
                    float initialSpan;
                    float prevNbStep;

                    @Override
                    public boolean onScaleBegin(ScaleGestureDetector detector) {
                        initialSpan = detector.getCurrentSpan();
                        prevNbStep = 0;
                        return true;
                    }

                    @Override
                    public boolean onScale(ScaleGestureDetector detector) {
                        float spanDiff = initialSpan - detector.getCurrentSpan();
                        float curNbStep = spanDiff / SPAN_RATIO;

                        float stepDiff = curNbStep - prevNbStep;
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
