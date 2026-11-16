package com.codexpocket.app;

/** Full-bleed room; overlay controls never shrink the scene into a rectangle. */
final class RoomFraming {
    static final float WIDTH=720,HEIGHT=1280;
    final float scale,x,y;
    private RoomFraming(float scale,float x,float y){this.scale=scale;this.x=x;this.y=y;}
    static RoomFraming fit(float width,float height,float top,float bottom,float zoom){
        width=Math.max(1,width);height=Math.max(1,height);
        float scale=Math.max(width/WIDTH,height/HEIGHT)*Math.max(1,Math.min(1.3f,zoom));
        float x=(width-WIDTH*scale)/2;
        float y=(height-HEIGHT*scale)/2;
        return new RoomFraming(scale,x,y);
    }
}
