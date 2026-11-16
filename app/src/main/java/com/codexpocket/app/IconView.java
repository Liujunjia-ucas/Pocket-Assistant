package com.codexpocket.app;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.view.View;

/** Small code-native icons; no icon font or network assets required. */
final class IconView extends View {
    private final String icon; private final Paint pen = new Paint(Paint.ANTI_ALIAS_FLAG); private final int color;
    private final Path glyph = new Path();
    IconView(Context context, String icon, int color, String description) { super(context); this.icon = icon; this.color = color; prepareGlyph();setContentDescription(description); setFocusable(!description.isEmpty());setBackground(PocketUi.touch(context,android.graphics.Color.TRANSPARENT,16,0,color));PocketUi.feedback(this); }
    // Icon geometry is immutable: build once instead of allocating on each frame.
    private void prepareGlyph() {
        switch(icon) {
            case "inbox": polyline(3,14,8,14,10,17,14,17,16,14,21,14);break;
            case "sms": polyline(6,18,6,22,11,18);break;
            case "assistant": case "spark": polyline(12,2,14.5f,9.5f,22,12,14.5f,14.5f,12,22,9.5f,14.5f,2,12,9.5f,9.5f,12,2);break;
            case "file": polyline(7,3,15,3,20,8,20,21,5,21,5,3,7,3);break;
            case "phone": glyph.moveTo(7,3);glyph.lineTo(3,5);glyph.cubicTo(2,13,11,22,19,21);glyph.lineTo(21,17);glyph.lineTo(16,14);glyph.lineTo(13,17);glyph.cubicTo(10,15,8,13,7,10);glyph.lineTo(10,7);glyph.close();break;
            case "heart": glyph.moveTo(12,21);glyph.cubicTo(-5,11,4,-1,12,7);glyph.cubicTo(20,-1,29,11,12,21);break;
            case "settings": for(int i=0;i<40;i++){double a=i*Math.PI/20-Math.PI/2;float r=(i%5==1||i%5==2)?10:8;float x=12+(float)Math.cos(a)*r,y=12+(float)Math.sin(a)*r;if(i==0)glyph.moveTo(x,y);else glyph.lineTo(x,y);}glyph.close();break;
            case "moon": glyph.moveTo(18.8f,15.7f);glyph.cubicTo(11,19,5,12,9,4);glyph.cubicTo(-1,8,5,25,17,19);glyph.lineTo(18.8f,15.7f);break;
        }
    }
    private void polyline(float... points){glyph.moveTo(points[0],points[1]);for(int i=2;i<points.length;i+=2)glyph.lineTo(points[i],points[i+1]);}
    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas); canvas.save(); float size = Math.min(getWidth(), getHeight()) * .52f;
        canvas.translate((getWidth()-size)/2, (getHeight()-size)/2); canvas.scale(size/24, size/24);
        pen.setColor(color); pen.setStyle(Paint.Style.STROKE); pen.setStrokeWidth(1.8f); pen.setStrokeCap(Paint.Cap.ROUND); pen.setStrokeJoin(Paint.Join.ROUND);
        switch(icon) {
            case "mail": canvas.drawRoundRect(3,5,21,19,3,3,pen);line(canvas,4,7,12,13);line(canvas,12,13,20,7);break;
            case "inbox": canvas.drawRoundRect(3,3,21,21,3,3,pen);canvas.drawPath(glyph,pen);line(canvas,8,7,16,7);line(canvas,8,10,14,10);break;
            case "phone": canvas.drawPath(glyph,pen);break;
            case "sms": canvas.drawRoundRect(3,3,21,18,3,3,pen);canvas.drawPath(glyph,pen);line(canvas,7,8,17,8);line(canvas,7,12,14,12);break;
            case "assistant": case "spark": canvas.drawPath(glyph,pen);break;
            case "grid": for(int x=3;x<=14;x+=11)for(int y=3;y<=14;y+=11)canvas.drawRoundRect(x,y,x+7,y+7,2,2,pen);break;
            case "history": canvas.drawArc(4,4,21,21,-100,315,false,pen);line(canvas,3,4,3,10);line(canvas,3,10,8,10);line(canvas,12,7,12,13);line(canvas,12,13,16,15);break;
            case "note": canvas.drawRoundRect(4,3,20,21,3,3,pen);line(canvas,8,8,16,8);line(canvas,8,12,16,12);line(canvas,8,16,13,16);break;
            case "search": canvas.drawCircle(10.5f,10.5f,6.5f,pen);line(canvas,15.5f,15.5f,21,21);break;
            case "mic": canvas.drawRoundRect(9,2,15,15,3,3,pen);canvas.drawArc(5,6,19,19,0,180,false,pen);line(canvas,12,19,12,22);break;
            case "heart": canvas.drawPath(glyph,pen);break;
            case "settings": canvas.drawPath(glyph,pen);canvas.drawCircle(12,12,3.4f,pen);break;
            case "menu": line(canvas,4,6,20,6); line(canvas,4,12,16,12); line(canvas,4,18,20,18); break;
            case "plus": line(canvas,12,4,12,20); line(canvas,4,12,20,12); break;
            case "send": line(canvas,12,20,12,4); line(canvas,5,11,12,4); line(canvas,12,4,19,11); break;
            case "new": canvas.drawRoundRect(4,5,19,20,3,3,pen); line(canvas,10,14,20,4); line(canvas,17,4,20,4); line(canvas,20,4,20,7); break;
            case "image": canvas.drawRoundRect(3,3,21,21,3,3,pen); canvas.drawCircle(9,8,1.5f,pen); line(canvas,4,17,11,11); line(canvas,11,11,20,20); line(canvas,15,15,19,11); line(canvas,19,11,21,13); break;
            case "file": canvas.drawPath(glyph,pen); line(canvas,14,3,14,9); line(canvas,14,9,20,9); line(canvas,9,13,16,13); line(canvas,9,17,16,17); break;
            case "close": line(canvas,6,6,18,18); line(canvas,6,18,18,6); break;
            case "chevron": line(canvas,9,5,16,12); line(canvas,16,12,9,19); break;
            case "check": line(canvas,5,12,10,17); line(canvas,10,17,20,6); break;
            case "stop": pen.setStyle(Paint.Style.FILL); canvas.drawRoundRect(6,6,18,18,2,2,pen); break;
            case "theme": case "sun": canvas.drawCircle(12,12,4,pen);for(int i=0;i<8;i++){double a=i*Math.PI/4;line(canvas,12+(float)Math.cos(a)*7,12+(float)Math.sin(a)*7,12+(float)Math.cos(a)*9,12+(float)Math.sin(a)*9);}break;
            case "moon": canvas.drawPath(glyph,pen);break;
            case "system": canvas.drawRoundRect(3,4,21,17,2,2,pen);line(canvas,12,17,12,21);line(canvas,8,21,16,21);break;
            case "proxy": canvas.drawArc(4,4,20,20,-45,270,false,pen);line(canvas,12,2,12,11);break;
            default: canvas.drawCircle(12,12,8,pen); line(canvas,12,8,12,12); line(canvas,12,15,12,15.1f);
        }
        canvas.restore();
    }
    private void line(Canvas c,float a,float b,float x,float y){c.drawLine(a,b,x,y,pen);}
}
