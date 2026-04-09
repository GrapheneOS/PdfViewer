package app.grapheneos.pdfviewer;

import android.annotation.SuppressLint;
import android.content.Context;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.concurrent.atomic.AtomicBoolean;

/*
    The GestureHelper present a simple gesture api for the PdfViewer
*/

class GestureHelper {
    public interface GestureListener {
        boolean onTapUp();
        boolean onFling(@Nullable MotionEvent e1, @NonNull MotionEvent e2, float velocityX, float velocityY);
        void onZoom(float scaleFactor, float focusX, float focusY);
        void onZoomEnd();
    }

    @SuppressLint("ClickableViewAccessibility")
    static void attach(Context context, View gestureView, GestureListener listener) {

        AtomicBoolean wasScaling = new AtomicBoolean(false);

        final ScaleGestureDetector scaleDetector = new ScaleGestureDetector(context,
                new ScaleGestureDetector.SimpleOnScaleGestureListener() {
                    @Override
                    public boolean onScale(@NonNull ScaleGestureDetector detector) {
                        listener.onZoom(detector.getScaleFactor(), detector.getFocusX(),
                                detector.getFocusY());

                        return true;
                    }

                    @Override
                    public void onScaleEnd(@NonNull ScaleGestureDetector detector) {
                        listener.onZoomEnd();
                    }
                });

        final GestureDetector detector = new GestureDetector(context,
                new GestureDetector.SimpleOnGestureListener() {
                    @Override
                    public boolean onSingleTapUp(@NonNull MotionEvent motionEvent) {
                        return listener.onTapUp();
                    }

                    @Override
                    public boolean onFling(@Nullable MotionEvent e1, @NonNull MotionEvent e2,
                                           float velocityX, float velocityY) {
                        if (wasScaling.get()) {
                            return false;
                        }

                        return listener.onFling(e1, e2, velocityX, velocityY);
                    }
                });

        gestureView.setOnTouchListener((view, motionEvent) -> {
            int action = motionEvent.getActionMasked();

            if (action == MotionEvent.ACTION_DOWN) {
                wasScaling.set(false);
            }

            detector.onTouchEvent(motionEvent);
            scaleDetector.onTouchEvent(motionEvent);

            // Check after scaleDetector processes the event
            if (scaleDetector.isInProgress()) {
                wasScaling.set(true);
            }

            return false;
        });
    }

}
