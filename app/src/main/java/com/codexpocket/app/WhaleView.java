package com.codexpocket.app;
import android.content.Context;
import android.graphics.*;
import android.view.View;
/** Original code-drawn audio visualization. No third-party character artwork. */
final class WhaleView extends View {
    private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final ThemePalette palette;
    private boolean running;
    private float energy;
    private String state="idle";
    WhaleView(Context c,ThemePalette p){super(c);palette=p;setContentDescription("原创语音光环");}
    protected void onDraw(Canvas c){super.onDraw(c);float x=getWidth()/2f,y=getHeight()/2f;
        float radius=Math.min(getWidth(),getHeight())*.22f;
        paint.setColor(palette.accent);paint.setAlpha(22);c.drawCircle(x,y,radius*(1.35f+energy*.15f),paint);
        paint.setAlpha(210);c.drawCircle(x,y,radius*(1+energy*.12f),paint);
        paint.setColor(Color.WHITE);paint.setAlpha(235);paint.setTextSize(Math.max(14,radius*.24f));paint.setTextAlign(Paint.Align.CENTER);
        String label=state.equals("speaking")?"正在回应":state.equals("thinking")?"想一想":state.equals("listening")?"正在聆听":"在这里";
        c.drawText(label,x,y+paint.getTextSize()*.35f,paint);
    }
    void style(String id,float zoom){}
    void active(boolean value){running=value;if(!value)energy=0;invalidate();}
    void close(){running=false;energy=0;}
    void audio(float value,int position){energy=running&&Float.isFinite(value)?Math.max(0,Math.min(1,value)):0;invalidate();}
    void gesture(String name){}
    void event(String name,String text){}
    void expression(String name){}
    void phase(String value){state=value;invalidate();}
    void prepareSpeech(String text,Runnable ready,Runnable unavailable){ready.run();}
    void safeInsets(int top,int bottom){}
    void level(float value){audio(value,0);}
}
