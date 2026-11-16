package com.codexpocket.app;

import android.animation.ValueAnimator;
import android.content.Context;
import android.view.Gravity;
import android.view.View;
import android.widget.*;

/** Three main features only; transport choices live at the top of Codex. */
final class BottomFeatureBar extends FrameLayout {
    private final LinearLayout content;
    private ValueAnimator animator;private float fraction=1;private boolean expanded=true;
    BottomFeatureBar(Context c,ThemePalette p,WorkspaceSwitch sections){
        super(c);setClipChildren(true);content=PocketUi.column(c);content.setGravity(Gravity.CENTER_HORIZONTAL);content.setPadding(0,dp(4),0,dp(8));addView(content,new LayoutParams(-1,-2,Gravity.BOTTOM));
        content.addView(sections,new LinearLayout.LayoutParams(dp(300),dp(42)));
    }
    private int dp(int n){return PocketUi.dp(getContext(),n);}
    void setSection(int section){}
    void setExpanded(boolean value,boolean animate){
        float target=value?1:0;if(expanded==value&&(animator!=null||fraction==target))return;expanded=value;if(animator!=null){animator.cancel();animator=null;}
        if(!animate||!isLaidOut()){fraction=target;apply();return;}
        setVisibility(VISIBLE);animator=ValueAnimator.ofFloat(fraction,target);animator.setDuration(220);animator.setInterpolator(new android.view.animation.DecelerateInterpolator());animator.addUpdateListener(a->{fraction=(float)a.getAnimatedValue();apply();});animator.addListener(new android.animation.AnimatorListenerAdapter(){@Override public void onAnimationEnd(android.animation.Animator a){if(animator==a)animator=null;}});animator.start();
    }
    private void apply(){int height=dp(54);content.setTranslationY(height*(1-fraction));content.setAlpha(fraction);android.view.ViewGroup.LayoutParams lp=getLayoutParams();if(lp!=null){lp.height=Math.round(height*fraction);setLayoutParams(lp);}setVisibility(fraction==0?GONE:VISIBLE);}
    @Override protected void onDetachedFromWindow(){if(animator!=null){animator.cancel();animator=null;}super.onDetachedFromWindow();}
}
