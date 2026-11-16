package com.codexpocket.app;

import android.app.AlertDialog;
import android.content.Context;
import android.content.ContextWrapper;
import android.graphics.Outline;
import android.graphics.drawable.GradientDrawable;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewOutlineProvider;
import android.view.Window;
import android.view.Gravity;
import android.view.WindowManager;
import android.widget.*;

/** Shared rounded frame for native menus, settings and confirmations in either theme. */
final class RoundedDialogBuilder extends AlertDialog.Builder {
    private CharSequence heading;
    RoundedDialogBuilder(Context context){super(context);}
    @Override public RoundedDialogBuilder setTitle(CharSequence title){heading=title;super.setTitle(title);return this;}
    @Override public RoundedDialogBuilder setTitle(int title){return setTitle(getContext().getText(title));}
    private MainActivity activity(){Context c=getContext();while(c instanceof ContextWrapper){if(c instanceof MainActivity)return (MainActivity)c;Context next=((ContextWrapper)c).getBaseContext();if(next==c)break;c=next;}return c instanceof MainActivity?(MainActivity)c:null;}
    @Override public AlertDialog create(){
        MainActivity owner=activity();IconView gear=null;
        if(owner!=null&&heading!=null&&!"设置".contentEquals(heading)){
            TypedValue color=new TypedValue();getContext().getTheme().resolveAttribute(android.R.attr.textColorPrimary,color,true);int ink=color.resourceId==0?color.data:getContext().getColorStateList(color.resourceId).getDefaultColor();
            LinearLayout row=PocketUi.row(getContext());row.setPadding(PocketUi.dp(getContext(),22),PocketUi.dp(getContext(),12),PocketUi.dp(getContext(),12),PocketUi.dp(getContext(),6));TextView title=PocketUi.text(getContext(),heading.toString(),19,ink);title.setTypeface(null,android.graphics.Typeface.BOLD);row.addView(title,new LinearLayout.LayoutParams(0,-2,1));gear=new IconView(getContext(),"settings",ink,"设置");row.addView(gear,new LinearLayout.LayoutParams(PocketUi.dp(getContext(),44),PocketUi.dp(getContext(),44)));super.setCustomTitle(row);
        }
        AlertDialog dialog=super.create();Window window=dialog.getWindow();
        if(window!=null)window.addFlags(WindowManager.LayoutParams.FLAG_SECURE);
        if(gear!=null)gear.setOnClickListener(v->{dialog.dismiss();owner.showUnifiedSettings();});
        if(window!=null){TypedValue color=new TypedValue();getContext().getTheme().resolveAttribute(android.R.attr.colorBackground,color,true);GradientDrawable background=new GradientDrawable();background.setColor(color.data);background.setCornerRadius(28*getContext().getResources().getDisplayMetrics().density);window.setBackgroundDrawable(background);clip(window.getDecorView(),28);window.setGravity(Gravity.BOTTOM);window.setWindowAnimations(R.style.PocketSheetAnimation);window.setDimAmount(.32f);window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);}
        return dialog;
    }
    @Override public AlertDialog show(){AlertDialog dialog=create();dialog.show();return dialog;}
    static void clip(View view,int radius){float rounded=radius*view.getResources().getDisplayMetrics().density;view.setOutlineProvider(new ViewOutlineProvider(){@Override public void getOutline(View v,Outline outline){outline.setRoundRect(0,0,v.getWidth(),v.getHeight(),rounded);}});view.setClipToOutline(true);}
}
