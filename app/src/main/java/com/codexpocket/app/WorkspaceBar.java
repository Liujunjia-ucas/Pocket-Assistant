package com.codexpocket.app;

import android.animation.ValueAnimator;
import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.content.Context;
import android.view.Gravity;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;

/** Slide the navigation up while returning its height to the conversation. */
final class WorkspaceBar extends FrameLayout {
    private final View control;
    private final int fullHeight;
    private float fraction = 1;
    private boolean expanded = true;
    private ValueAnimator animation;
    WorkspaceBar(Context context, View control) {
        super(context); this.control = control; fullHeight = dp(48); setClipChildren(true);
        LayoutParams params = new LayoutParams(dp(258), dp(38), Gravity.TOP | Gravity.CENTER_HORIZONTAL);
        params.topMargin = dp(6); addView(control, params);
    }
    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
    void setExpanded(boolean value, boolean animate) {
        if (expanded == value && animation != null && animate) return;
        float target = value ? 1 : 0;
        if (expanded == value && fraction == target) return;
        expanded = value;
        if (animation != null) { animation.cancel(); animation = null; }
        if (!animate || !isLaidOut()) { fraction = target; apply(); return; }
        setVisibility(VISIBLE);
        animation = ValueAnimator.ofFloat(fraction, target); animation.setDuration(220); animation.setInterpolator(new DecelerateInterpolator());
        animation.addListener(new AnimatorListenerAdapter(){@Override public void onAnimationEnd(Animator animator){if(animation==animator)animation=null;}});
        animation.addUpdateListener(frame -> { fraction = (float)frame.getAnimatedValue(); apply(); }); animation.start();
    }
    private void apply() {
        control.setTranslationY(-fullHeight * (1 - fraction)); control.setAlpha(fraction);
        android.view.ViewGroup.LayoutParams params = getLayoutParams();
        if (params != null) { params.height = Math.round(fullHeight * fraction); setLayoutParams(params); }
        setVisibility(fraction == 0 ? GONE : VISIBLE);
    }
    @Override protected void onDetachedFromWindow() { if (animation != null) { animation.cancel(); animation=null; } fraction=expanded?1:0;apply();super.onDetachedFromWindow(); }
}
