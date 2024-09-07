package app.grapheneos.pdfviewer;

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
        void onZoom(float scaleFactor, float focusX, float focusY);
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
                    @Override
                    public boolean onScale(ScaleGestureDetector detector) {
                        listener.onZoom(detector.getScaleFactor(), detector.getFocusX(),
                                detector.getFocusY());

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
