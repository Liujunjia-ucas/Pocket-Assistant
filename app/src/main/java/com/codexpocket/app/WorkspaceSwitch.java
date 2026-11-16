package com.codexpocket.app;

import android.content.Context;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Three destinations with a sliding indicator; dragging stays inside the control. */
final class WorkspaceSwitch extends FrameLayout {
    static final int WINDOWS = 0, ANDROID = 1, LABORATORY = 2;
    interface Listener { void onSelected(int destination); }
    private final View indicator;
    private final TextView[] tabs;
    private final ThemePalette palette;
    private final int inset, slop;
    private final Listener listener;
    private int selected;
    private boolean dragging;
    private float downX, downY;

    WorkspaceSwitch(Context context, ThemePalette palette, Listener listener) {
        this(context,palette,listener,new String[]{"Windows","Android","实验室"});
    }
    WorkspaceSwitch(Context context, ThemePalette palette, Listener listener,String[] titles) {
        super(context); this.palette = palette; this.listener = listener;
        if(titles.length<2||titles.length>3)throw new IllegalArgumentException("Two or three sections required");tabs=new TextView[titles.length];
        inset = dp(3); slop = ViewConfiguration.get(context).getScaledTouchSlop();
        setBackground(PocketUi.surface(context,palette.chip,22,palette.border));
        indicator = new View(context); indicator.setBackground(shape(palette.panel, 18)); indicator.setElevation(dp(1));
        LayoutParams marker = new LayoutParams(0, -1); marker.setMargins(inset, inset, inset, inset); addView(indicator, marker);
        LinearLayout labels = new LinearLayout(context); labels.setOrientation(LinearLayout.HORIZONTAL);
        labels.setElevation(dp(2));
        labels.setPadding(inset, inset, inset, inset);
        for (int i = 0; i < tabs.length; i++) {
            final int destination = i;
            tabs[i] = label(titles[i]);
            labels.addView(tabs[i], new LinearLayout.LayoutParams(0, -1, 1));
            tabs[i].setOnClickListener(v -> choose(destination));
        }
        addView(labels, new LayoutParams(-1, -1));
        updateLabels();
    }
    private int dp(int value) { return (int)(value * getResources().getDisplayMetrics().density + .5f); }
    private GradientDrawable shape(int color, int radius) { GradientDrawable shape = new GradientDrawable(); shape.setColor(color); shape.setCornerRadius(dp(radius)); return shape; }
    private TextView label(String title) {
        TextView view = new TextView(getContext()); view.setText(title); view.setTextSize(12); view.setGravity(Gravity.CENTER);
        view.setClickable(true); view.setFocusable(true);view.setBackground(PocketUi.touch(getContext(),android.graphics.Color.TRANSPARENT,18,0,palette.text)); view.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_YES);
        return view;
    }
    void setDestination(int destination, boolean animate) {
        selected = Math.max(WINDOWS, Math.min(tabs.length-1, destination));
        updateLabels(); indicator.animate().cancel();
        float position = selected * segmentWidth();
        if (animate && isLaidOut()) indicator.animate().translationX(position).setDuration(180).start();
        else indicator.setTranslationX(position);
    }
    private float segmentWidth() { return Math.max(0, getWidth() - inset * 2) / (float)tabs.length; }
    private void updateLabels() {
        for (int i = 0; i < tabs.length; i++) {
            boolean active = selected == i;
            tabs[i].setSelected(active);
            tabs[i].setTextColor(active ? palette.text : palette.muted);
            tabs[i].setTypeface(null, active ? Typeface.BOLD : Typeface.NORMAL);
            tabs[i].setContentDescription(tabs[i].getText() + (active ? "，已选择" : ""));
        }
    }
    private void choose(int destination) {
        boolean changed = selected != destination;
        setDestination(destination, true);
        if (changed) listener.onSelected(selected);
    }
    @Override protected void onSizeChanged(int w, int h, int oldW, int oldH) {
        super.onSizeChanged(w, h, oldW, oldH);
        LayoutParams params = (LayoutParams)indicator.getLayoutParams(); params.width = Math.round(segmentWidth()); indicator.setLayoutParams(params);
        setDestination(selected, false);
    }
    @Override public boolean onInterceptTouchEvent(MotionEvent event) {
        if (event.getActionMasked() == MotionEvent.ACTION_DOWN) { downX = event.getX(); downY = event.getY(); dragging = false; }
        if (event.getActionMasked() == MotionEvent.ACTION_MOVE && Math.abs(event.getX() - downX) > slop && Math.abs(event.getX() - downX) > Math.abs(event.getY() - downY)) {
            dragging = true; indicator.animate().cancel(); getParent().requestDisallowInterceptTouchEvent(true); return true;
        }
        return false;
    }
    @Override public boolean onTouchEvent(MotionEvent event) {
        if (!dragging) return super.onTouchEvent(event);
        float segment = segmentWidth(), travel = segment * (tabs.length-1);
        if (event.getActionMasked() == MotionEvent.ACTION_MOVE) {
            float start = selected * segment;
            indicator.setTranslationX(Math.max(0, Math.min(travel, start + event.getX() - downX)));
        } else if (event.getActionMasked() == MotionEvent.ACTION_UP) {
            float distance = event.getX() - downX;
            int steps = segment > 0 ? Math.max(1, Math.round(Math.abs(distance) / segment)) : 1;
            choose(Math.abs(distance) > slop ? Math.max(WINDOWS, Math.min(tabs.length-1, selected + (distance > 0 ? steps : -steps))) : selected); dragging = false;
            if(Math.abs(distance)<=slop)performClick();
            getParent().requestDisallowInterceptTouchEvent(false);
        } else if (event.getActionMasked() == MotionEvent.ACTION_CANCEL) {
            setDestination(selected, true); dragging = false; getParent().requestDisallowInterceptTouchEvent(false);
        }
        return true;
    }
    @Override public boolean performClick() { super.performClick(); return true; }
}
