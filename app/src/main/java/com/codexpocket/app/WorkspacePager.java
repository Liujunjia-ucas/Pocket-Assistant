package com.codexpocket.app;

import android.animation.ValueAnimator;
import android.content.Context;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.widget.AbsSeekBar;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.TextView;

/** Keeps all three pages alive and arbitrates swipes before scrollable children. */
final class WorkspacePager extends FrameLayout {
    interface Listener { void onSelected(int destination); }
    private final Listener listener;
    private final WorkspaceSwipe gesture;
    private final float edgeInset;
    private VelocityTracker velocity;
    private ValueAnimator animation;
    private int selected;
    private float position, downX, downY;
    private boolean dragging, consuming;
    private Runnable swipeActivity = () -> {};
    private boolean swipingEnabled=true;
    void setSwipingEnabled(boolean enabled){swipingEnabled=enabled;cancelGesture();}
    void onSwipeActivity(Runnable action) { swipeActivity = action; }

    WorkspacePager(Context context, Listener listener) {
        super(context); this.listener = listener;
        float density = getResources().getDisplayMetrics().density;
        gesture = new WorkspaceSwipe(ViewConfiguration.get(context).getScaledTouchSlop(), density, ViewConfiguration.getLongPressTimeout());
        edgeInset = 20 * density;
        setClipChildren(true); setClipToPadding(true);
    }
    void setDestination(int destination, boolean animate) {
        int target = Math.max(0, Math.min(getChildCount() - 1, destination));
        stopAnimation(); selected = target;
        if (!animate || !isLaidOut() || Math.abs(position - target) < .001f) {
            position = target; render(); return;
        }
        animation = ValueAnimator.ofFloat(position, target);
        animation.setDuration(220);
        animation.setInterpolator(new DecelerateInterpolator());
        animation.addUpdateListener(value -> { position = (float)value.getAnimatedValue(); render(); });
        animation.start();
    }
    private void stopAnimation() { if (animation != null) { animation.cancel(); animation = null; } }
    private void render() {
        for (int i = 0; i < getChildCount(); i++) {
            View page = getChildAt(i);
            page.setTranslationX((i - position) * getWidth());
            boolean visible = Math.abs(i - position) < .9999f;
            page.setVisibility(visible ? VISIBLE : INVISIBLE);
            page.setImportantForAccessibility(i == selected ? IMPORTANT_FOR_ACCESSIBILITY_AUTO : IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
        }
    }
    private void recycleVelocity() { if (velocity != null) { velocity.recycle(); velocity = null; } }
    void cancelGesture() {
        gesture.block(); dragging = consuming = false; recycleVelocity();
        stopAnimation(); position = selected; render();
    }
    @Override protected void onSizeChanged(int w, int h, int oldW, int oldH) {
        super.onSizeChanged(w, h, oldW, oldH);
        if (w != oldW) cancelGesture(); else render();
    }
    @Override protected void onDetachedFromWindow() { cancelGesture(); super.onDetachedFromWindow(); }

    // Observe here so a vertical ScrollView cannot suppress later horizontal detection.
    // Once vertical scrolling or a protected child wins, the entire gesture stays theirs.
    @Override public boolean dispatchTouchEvent(MotionEvent event) {
        if(!swipingEnabled)return super.dispatchTouchEvent(event);
        int action = event.getActionMasked();
        if (action == MotionEvent.ACTION_DOWN) {
            cancelGesture(); downX = event.getX(); downY = event.getY();
            boolean eligible = getChildCount() > 1 && downX >= edgeInset && downX <= getWidth() - edgeInset &&
                !protectedAt(getChildAt(selected), downX, downY, 0, false);
            gesture.begin(downX, downY, event.getEventTime(), eligible);
            velocity = VelocityTracker.obtain(); velocity.addMovement(event);
        } else if (velocity != null) velocity.addMovement(event);

        if (action == MotionEvent.ACTION_POINTER_DOWN) {
            gesture.block();
            if (dragging) { dragging = false; consuming = true; setDestination(selected, true); }
        }
        if (action == MotionEvent.ACTION_MOVE && !consuming) {
            if (!dragging && protectedAt(getChildAt(selected), downX, downY, (int)-gesture.distance(event.getX()), true)) gesture.block();
            if (gesture.move(event.getX(), event.getY(), event.getEventTime(), event.getPointerCount(), selected, getChildCount())) {
                if (!dragging) {
                    MotionEvent cancel = MotionEvent.obtain(event); cancel.setAction(MotionEvent.ACTION_CANCEL);
                    super.dispatchTouchEvent(cancel); cancel.recycle();
                    dragging = true;
                    swipeActivity.run();
                    if (getParent() != null) getParent().requestDisallowInterceptTouchEvent(true);
                }
                position = Math.max(0, Math.min(getChildCount() - 1, selected - gesture.distance(event.getX()) / Math.max(1, getWidth())));
                render(); return true;
            }
        }
        if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
            boolean owned = dragging || consuming;
            if (dragging) {
                float speed = 0;
                if (velocity != null) { velocity.computeCurrentVelocity(1000); speed = velocity.getXVelocity(); }
                int destination = action == MotionEvent.ACTION_CANCEL ? selected : gesture.release(event.getX(), speed, getWidth(), selected, getChildCount());
                if (destination == selected) setDestination(selected, true);
                else listener.onSelected(destination);
            }
            gesture.block(); dragging = consuming = false; recycleVelocity();
            if (getParent() != null) getParent().requestDisallowInterceptTouchEvent(false);
            if (owned) return true;
        }
        if (consuming) return true;
        // Consume blank page space too, so a down there continues to receive moves.
        super.dispatchTouchEvent(event); return true;
    }
    private boolean protectedAt(View view, float x, float y, int direction, boolean checkScroll) {
        // TextView's temporary selection/horizontal-scroll state must not veto paging.
        // Long presses are already locked to the child by WorkspaceSwipe.
        boolean math = view instanceof MathReplyView;
        if (WorkspaceSwipe.protectContent(view instanceof EditText, view instanceof AbsSeekBar,
            view instanceof HorizontalScrollView, view instanceof TextView || math,
            math && ((MathReplyView)view).protectsHorizontalAt(x, y),
            checkScroll && direction != 0 && view.canScrollHorizontally(direction))) return true;
        if (math) return false; // Do not inspect private Chromium child views.
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup)view;
            for (int i = group.getChildCount() - 1; i >= 0; i--) {
                View child = group.getChildAt(i);
                if (child.getVisibility() != VISIBLE) continue;
                float cx = x + group.getScrollX() - child.getLeft() - child.getTranslationX();
                float cy = y + group.getScrollY() - child.getTop() - child.getTranslationY();
                if (cx >= 0 && cy >= 0 && cx < child.getWidth() && cy < child.getHeight() && protectedAt(child, cx, cy, direction, checkScroll)) return true;
            }
        }
        return false;
    }
}
