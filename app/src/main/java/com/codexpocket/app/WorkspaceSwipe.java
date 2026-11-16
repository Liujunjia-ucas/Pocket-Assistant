package com.codexpocket.app;

/** Gesture decisions independent of Android's touch delivery and animation. */
final class WorkspaceSwipe {
    static boolean protectContent(boolean input, boolean slider, boolean horizontalList, boolean text,
                                  boolean overflowingFormula, boolean canScroll) {
        return input || slider || horizontalList || overflowingFormula || (!text && canScroll);
    }
    private final float slop, density;
    private final long longPressMillis;
    private float downX, downY;
    private long downTime;
    private boolean blocked, dragging;

    WorkspaceSwipe(float touchSlop, float density, long longPressMillis) {
        this.slop = Math.max(touchSlop * 2, 16 * density);
        this.density = density;
        this.longPressMillis = longPressMillis;
    }
    void begin(float x, float y, long time, boolean eligible) {
        downX = x; downY = y; downTime = time; blocked = !eligible; dragging = false;
    }
    void block() { blocked = true; dragging = false; }
    float distance(float x) { return x - downX; }
    boolean move(float x, float y, long time, int pointers, int page, int count) {
        if (blocked) return false;
        if (pointers != 1) { block(); return false; }
        if (dragging) return true;
        float dx = x - downX, dy = y - downY;
        if (time - downTime >= longPressMillis ||
            (Math.abs(dy) > slop && Math.abs(dx) <= Math.abs(dy) * 1.6f)) {
            block(); return false;
        }
        if (Math.abs(dx) <= slop || Math.abs(dx) <= Math.abs(dy) * 1.6f) return false;
        int candidate = page + (dx < 0 ? 1 : -1);
        if (candidate < 0 || candidate >= count) { block(); return false; }
        dragging = true; return true;
    }
    int release(float x, float velocity, float width, int page, int count) {
        if (!dragging || blocked || width <= 0) return page;
        float dx = distance(x);
        boolean far = Math.abs(dx) >= Math.min(96 * density, width * .22f);
        boolean fling = Math.abs(dx) >= 24 * density && Math.abs(velocity) >= 550 * density && dx * velocity > 0;
        return far || fling ? Math.max(0, Math.min(count - 1, page + (dx < 0 ? 1 : -1))) : page;
    }
}
