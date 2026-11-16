package com.codexpocket.app;

import android.animation.ObjectAnimator;
import android.animation.AnimatorSet;
import android.animation.StateListAnimator;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.*;

/** Native design tokens; no per-frame layout animations or external fonts. */
final class PocketUi {
    static int dp(Context c,float value){return Math.round(value*c.getResources().getDisplayMetrics().density);}
    static GradientDrawable surface(Context c,int color,int radius,int border){GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(c,radius));if(border!=0)d.setStroke(dp(c,1),border);return d;}
    static android.graphics.drawable.Drawable touch(Context c,int color,int radius,int border,int ink){return new RippleDrawable(ColorStateList.valueOf((ink&0xffffff)|0x18000000),surface(c,color,radius,border),surface(c,0xffffffff,radius,0));}
    static void feedback(View v){
        StateListAnimator states=new StateListAnimator();AnimatorSet pressed=new AnimatorSet();pressed.playTogether(ObjectAnimator.ofFloat(v,View.SCALE_X,.975f),ObjectAnimator.ofFloat(v,View.SCALE_Y,.975f));pressed.setDuration(110);
        AnimatorSet normal=new AnimatorSet();normal.playTogether(ObjectAnimator.ofFloat(v,View.SCALE_X,1f),ObjectAnimator.ofFloat(v,View.SCALE_Y,1f));normal.setDuration(170);
        states.addState(new int[]{android.R.attr.state_pressed,android.R.attr.state_enabled},pressed);states.addState(new int[]{},normal);v.setStateListAnimator(states);
    }
    static void button(Button b,ThemePalette p,boolean primary){
        b.setAllCaps(false);b.setTextSize(14);b.setTypeface(Typeface.create("sans-serif-medium",Typeface.NORMAL));b.setTextColor(primary?p.onAccent:p.text);
        b.setBackground(touch(b.getContext(),primary?p.accent:p.chip,22,0,primary?p.onAccent:p.text));b.setMinHeight(0);b.setMinimumHeight(0);b.setMinWidth(0);b.setMinimumWidth(0);b.setPadding(dp(b.getContext(),12),0,dp(b.getContext(),12),0);feedback(b);
    }
    static LinearLayout column(Context c){LinearLayout b=new LinearLayout(c);b.setOrientation(LinearLayout.VERTICAL);return b;}
    static LinearLayout row(Context c){LinearLayout b=new LinearLayout(c);b.setGravity(Gravity.CENTER_VERTICAL);return b;}
    static TextView text(Context c,String value,int size,int color){TextView t=new TextView(c);t.setText(value);t.setTextSize(size);t.setTextColor(color);t.setFontFeatureSettings("kern");t.setLineSpacing(dp(c,3),1);return t;}
    static void gap(LinearLayout parent,int size){parent.addView(new View(parent.getContext()),new LinearLayout.LayoutParams(1,dp(parent.getContext(),size)));}
    static LinearLayout action(Context c,ThemePalette p,String glyph,String title,String subtitle,Runnable click){
        LinearLayout row=row(c);row.setPadding(dp(c,14),dp(c,12),dp(c,12),dp(c,12));row.setBackground(touch(c,p.panel,22,p.border,p.text));
        IconView icon=new IconView(c,glyph,p.accent,title);icon.setBackground(surface(c,p.selected,15,0));row.addView(icon,new LinearLayout.LayoutParams(dp(c,42),dp(c,42)));
        LinearLayout words=column(c);TextView heading=text(c,title,15,p.text);heading.setTypeface(Typeface.create("sans-serif-medium",Typeface.NORMAL));words.addView(heading);
        if(!subtitle.isEmpty()){TextView sub=text(c,subtitle,12,p.muted);sub.setMaxLines(2);words.addView(sub);}
        LinearLayout.LayoutParams wp=new LinearLayout.LayoutParams(0,-2,1);wp.leftMargin=dp(c,12);wp.rightMargin=dp(c,8);row.addView(words,wp);
        row.addView(new IconView(c,"chevron",p.muted,""),new LinearLayout.LayoutParams(dp(c,20),dp(c,24)));row.setOnClickListener(v->click.run());row.setFocusable(true);feedback(row);return row;
    }
    static void section(LinearLayout parent,ThemePalette p,String title){gap(parent,24);TextView t=text(parent.getContext(),title,13,p.muted);t.setTypeface(Typeface.create("sans-serif-medium",Typeface.NORMAL));parent.addView(t);gap(parent,10);}
    static void entry(LinearLayout parent,ThemePalette p,String glyph,String title,String sub,Runnable click){parent.addView(action(parent.getContext(),p,glyph,title,sub,click),new LinearLayout.LayoutParams(-1,-2));gap(parent,10);}
}
