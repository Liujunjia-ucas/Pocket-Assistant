package com.codexpocket.app;

import android.content.Context;
import android.graphics.Typeface;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Visual structure for the personal dashboard; all data and actions stay in AssistantHub. */
final class AssistantDashboard {
    private final Context context;
    private final ThemePalette p;
    AssistantDashboard(Context context,ThemePalette palette){this.context=context;p=palette;}
    private int dp(int value){return PocketUi.dp(context,value);}
    private TextView text(String value,int size,int color){return PocketUi.text(context,value,size,color);}
    private int mix(int a,int b,float amount){
        int r=Math.round(((a>>16)&255)*(1-amount)+((b>>16)&255)*amount);
        int g=Math.round(((a>>8)&255)*(1-amount)+((b>>8)&255)*amount);
        int v=Math.round((a&255)*(1-amount)+(b&255)*amount);
        return 0xff000000|(r<<16)|(g<<8)|v;
    }
    int background(){return mix(p.bg,p.dark?0xff050a13:0xffdce4ed,p.dark?.18f:.34f);}
    int outline(){return mix(p.border,p.text,p.dark?.08f:.05f);}
    View masthead(String date){
        LinearLayout header=PocketUi.row(context);header.setPadding(dp(20),dp(15),dp(64),dp(13));
        IconView mark=new IconView(context,"assistant",p.accent,"");mark.setBackground(PocketUi.surface(context,p.selected,16,0));header.addView(mark,new LinearLayout.LayoutParams(dp(44),dp(44)));
        LinearLayout words=PocketUi.column(context);TextView title=text("AI助理",24,p.text);title.setTypeface(Typeface.create("sans-serif-medium",Typeface.NORMAL));words.addView(title);
        TextView day=text(date,11,p.muted);words.addView(day);LinearLayout.LayoutParams wp=new LinearLayout.LayoutParams(0,-2,1);wp.setMarginStart(dp(12));header.addView(words,wp);return header;
    }
    LinearLayout module(LinearLayout parent,String title,String glyph,int count,String action,Runnable click){
        LinearLayout card=PocketUi.column(context);card.setPadding(dp(14),dp(10),dp(14),dp(12));
        card.setBackground(PocketUi.surface(context,p.panel,24,outline()));card.setElevation(dp(1));
        LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(-1,-2);cp.bottomMargin=dp(14);parent.addView(card,cp);
        LinearLayout heading=PocketUi.row(context);heading.setMinimumHeight(dp(52));
        IconView icon=new IconView(context,glyph,p.accent,"");icon.setBackground(PocketUi.surface(context,p.selected,12,0));heading.addView(icon,new LinearLayout.LayoutParams(dp(32),dp(32)));
        TextView label=text(title,16,p.text);label.setTypeface(Typeface.create("sans-serif-medium",Typeface.NORMAL));label.setSingleLine(true);label.setEllipsize(TextUtils.TruncateAt.END);LinearLayout.LayoutParams tp=new LinearLayout.LayoutParams(0,-2,1);tp.setMarginStart(dp(10));heading.addView(label,tp);
        if(count>0){TextView badge=text(count>99?"99+":String.valueOf(count),11,p.accent);badge.setGravity(Gravity.CENTER);badge.setMinWidth(dp(24));badge.setPadding(dp(7),dp(2),dp(7),dp(2));badge.setBackground(PocketUi.surface(context,p.selected,10,0));heading.addView(badge);}
        if(click!=null){
            if("add".equals(action)){IconView add=new IconView(context,"plus",p.accent,"新增"+title);add.setBackground(PocketUi.touch(context,p.selected,17,0,p.accent));LinearLayout.LayoutParams ap=new LinearLayout.LayoutParams(dp(52),dp(52));ap.setMarginStart(dp(10));heading.addView(add,ap);add.setOnClickListener(v->click.run());}
            else{TextView button=text(action,12,p.accent);button.setGravity(Gravity.CENTER);button.setMinWidth(dp(48));button.setPadding(dp(8),0,dp(8),0);button.setBackground(PocketUi.touch(context,android.graphics.Color.TRANSPARENT,14,0,p.accent));heading.addView(button,new LinearLayout.LayoutParams(-2,dp(48)));button.setOnClickListener(v->click.run());button.setFocusable(true);button.setContentDescription(action+title);}
        }
        card.addView(heading);PocketUi.gap(card,8);return card;
    }
    void empty(LinearLayout card,String glyph,String title){
        LinearLayout row=PocketUi.row(context);row.setPadding(dp(10),dp(10),dp(10),dp(10));row.setMinimumHeight(dp(56));row.setBackground(PocketUi.surface(context,p.chip,16,0));
        row.addView(new IconView(context,glyph,p.muted,""),new LinearLayout.LayoutParams(dp(32),dp(32)));TextView label=text(title,13,p.muted);LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,-2,1);lp.setMarginStart(dp(8));row.addView(label,lp);card.addView(row,new LinearLayout.LayoutParams(-1,-2));
    }
    void footer(LinearLayout card,String title,Runnable action){
        TextView button=text(title,12,p.accent);button.setMinHeight(dp(44));button.setGravity(Gravity.CENTER);button.setBackground(PocketUi.touch(context,android.graphics.Color.TRANSPARENT,14,0,p.accent));button.setFocusable(true);button.setOnClickListener(v->action.run());LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.topMargin=dp(4);card.addView(button,lp);
    }
    void informationActions(LinearLayout card,Runnable sources,Runnable all,boolean hasItems){
        LinearLayout row=PocketUi.row(context);footer(row,"信息来源  ›",sources);if(hasItems)footer(row,"全部信息  ›",all);
        for(int i=0;i<row.getChildCount();i++){View child=row.getChildAt(i);LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,-2,1);lp.topMargin=dp(4);child.setLayoutParams(lp);}card.addView(row);
    }
    View row(String glyph,String title,String subtitle,Runnable click){
        LinearLayout row=PocketUi.row(context);row.setPadding(dp(10),dp(11),dp(8),dp(11));row.setBackground(PocketUi.touch(context,p.chip,16,0,p.text));
        row.addView(new IconView(context,glyph,p.accent,""),new LinearLayout.LayoutParams(dp(32),dp(36)));
        LinearLayout words=PocketUi.column(context);TextView name=text(title,14,p.text);name.setMaxLines(2);name.setEllipsize(TextUtils.TruncateAt.END);words.addView(name);
        if(!subtitle.isEmpty()){TextView sub=text(subtitle,12,p.muted);sub.setMaxLines(2);sub.setEllipsize(TextUtils.TruncateAt.END);sub.setPadding(0,dp(4),0,0);words.addView(sub);}
        LinearLayout.LayoutParams wp=new LinearLayout.LayoutParams(0,-2,1);wp.setMargins(dp(8),0,dp(6),0);row.addView(words,wp);row.addView(new IconView(context,"chevron",p.muted,""),new LinearLayout.LayoutParams(dp(18),dp(24)));row.setOnClickListener(v->click.run());row.setFocusable(true);PocketUi.feedback(row);return row;
    }
    void shortcuts(LinearLayout parent,Runnable history,Runnable files,Runnable more){
        LinearLayout card=module(parent,"常用功能","grid",0,"",null),row=PocketUi.row(context);
        String[] labels={"最近对话","我的文件","更多功能"},icons={"history","file","menu"};Runnable[] actions={history,files,more};
        for(int i=0;i<labels.length;i++){
            final Runnable action=actions[i];LinearLayout tile=PocketUi.column(context);tile.setGravity(Gravity.CENTER);tile.setPadding(dp(4),dp(10),dp(4),dp(10));tile.setBackground(PocketUi.touch(context,p.chip,16,0,p.accent));tile.addView(new IconView(context,icons[i],p.accent,""),new LinearLayout.LayoutParams(dp(30),dp(30)));TextView label=text(labels[i],12,p.text);label.setGravity(Gravity.CENTER);label.setPadding(0,dp(6),0,0);tile.addView(label);tile.setOnClickListener(v->action.run());tile.setFocusable(true);tile.setContentDescription(labels[i]);PocketUi.feedback(tile);LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,-2,1);if(i>0)lp.setMarginStart(dp(8));row.addView(tile,lp);
        }
        card.addView(row);
    }
}
