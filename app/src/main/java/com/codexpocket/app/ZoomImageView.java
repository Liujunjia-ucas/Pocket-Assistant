package com.codexpocket.app;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;

/** Native bitmap preview with pinch zoom, drag and double-tap reset. */
final class ZoomImageView extends View {
    private final Bitmap bitmap;
    private final Matrix matrix = new Matrix();
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final ScaleGestureDetector scale;
    private final GestureDetector gestures;
    private float fit = 1, current = 1;
    private boolean tapped;
    ZoomImageView(Context context, Bitmap bitmap) {
        super(context); this.bitmap = bitmap; setContentDescription("图片预览，可双指缩放和拖动");
        scale = new ScaleGestureDetector(context, new ScaleGestureDetector.SimpleOnScaleGestureListener() {
            @Override public boolean onScale(ScaleGestureDetector detector) {
                float next = Math.max(fit, Math.min(fit * 8, current * detector.getScaleFactor()));
                matrix.postScale(next / current, next / current, detector.getFocusX(), detector.getFocusY()); current = next; invalidate(); return true;
            }
        });
        gestures = new GestureDetector(context, new GestureDetector.SimpleOnGestureListener() {
            @Override public boolean onDown(MotionEvent e) { return true; }
            @Override public boolean onScroll(MotionEvent a, MotionEvent b, float dx, float dy) {
                if (!scale.isInProgress()) { matrix.postTranslate(-dx, -dy); invalidate(); } return true;
            }
            @Override public boolean onDoubleTap(MotionEvent e) { reset(); return true; }
            @Override public boolean onSingleTapUp(MotionEvent e) { tapped=true; return true; }
        });
    }
    private void reset() {
        fit = Math.min((float)getWidth() / bitmap.getWidth(), (float)getHeight() / bitmap.getHeight()); current = fit;
        matrix.reset(); matrix.postScale(fit, fit); matrix.postTranslate((getWidth() - bitmap.getWidth() * fit) / 2, (getHeight() - bitmap.getHeight() * fit) / 2); invalidate();
    }
    @Override protected void onSizeChanged(int w, int h, int oldW, int oldH) { reset(); }
    @Override protected void onDraw(Canvas canvas) { super.onDraw(canvas); canvas.drawBitmap(bitmap, matrix, paint); }
    @Override public boolean onTouchEvent(MotionEvent event) { tapped=false;scale.onTouchEvent(event);gestures.onTouchEvent(event);if(tapped)performClick();return true; }
    @Override public boolean performClick() { super.performClick(); return true; }
}
