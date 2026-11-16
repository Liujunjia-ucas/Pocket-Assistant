package com.codexpocket.app;
import android.content.Context;
import android.graphics.*;
import android.graphics.drawable.Drawable;
/** Original theme background; no downloaded character art. */
final class ChatWallpaperDrawable extends Drawable {
    private final Paint paint=new Paint();private final int color;private int opacity=255;
    ChatWallpaperDrawable(Context c,String provider,ThemePalette p){color=p.bg;}
    public void draw(Canvas canvas){paint.setColor(color);paint.setAlpha(opacity);canvas.drawRect(getBounds(),paint);}
    public void setAlpha(int a){opacity=a;invalidateSelf();}
    public void setColorFilter(ColorFilter f){paint.setColorFilter(f);invalidateSelf();}
    public int getOpacity(){return PixelFormat.TRANSLUCENT;}
}
