package com.codexpocket.app;

import android.app.*;
import android.content.*;
import android.os.Build;
import android.provider.Settings;
import android.widget.*;

final class AssistantNotificationSettings {
    static void show(Activity a,ThemePalette p){AssistantNotifications.channels(a);android.content.SharedPreferences prefs=a.getSharedPreferences("assistant_notifications",0);LinearLayout panel=PocketUi.column(a);panel.setPadding(PocketUi.dp(a,22),0,PocketUi.dp(a,22),PocketUi.dp(a,16));
        TextView status=PocketUi.text(a,AssistantNotifications.allowed(a)?"应用通知已开启":"应用通知未开启",13,p.muted);panel.addView(status);PocketUi.gap(panel,12);
        toggle(a,p,panel,"重要事项与待办",prefs.getBoolean(AssistantNotifications.IMPORTANT,true),value->prefs.edit().putBoolean(AssistantNotifications.IMPORTANT,value).apply());
        toggle(a,p,panel,"工作结果与确认",prefs.getBoolean(AssistantNotifications.WORK,true),value->prefs.edit().putBoolean(AssistantNotifications.WORK,value).apply());
        Switch background=toggle(a,p,panel,"允许后台运行",AssistantBackgroundService.enabled(a),value->{prefs.edit().putBoolean("background",value).apply();if(value){if(Build.VERSION.SDK_INT>=33&&!AssistantNotifications.allowed(a))a.requestPermissions(new String[]{android.Manifest.permission.POST_NOTIFICATIONS},AssistantNotifications.PERMISSION);if(!AssistantBackgroundService.start(a))Toast.makeText(a,"后台启动受系统限制，请重新打开应用后重试",Toast.LENGTH_LONG).show();}else a.stopService(new Intent(a,AssistantBackgroundService.class));});
        PocketUi.gap(panel,10);panel.addView(PocketUi.text(a,"后台运行时会显示常驻通知，持续整理信息，并继续已开始的手机工作。自动整理不会持续录音。",12,p.muted));PocketUi.gap(panel,12);
        Button permission=new Button(a);permission.setText("系统通知设置");panel.addView(permission);permission.setOnClickListener(v->{if(Build.VERSION.SDK_INT>=33&&!AssistantNotifications.allowed(a))a.requestPermissions(new String[]{android.Manifest.permission.POST_NOTIFICATIONS},AssistantNotifications.PERMISSION);else open(a,new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE,a.getPackageName()));});
        Button battery=new Button(a);battery.setText("后台与电池设置");panel.addView(battery);battery.setOnClickListener(v->open(a,new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,android.net.Uri.parse("package:"+a.getPackageName()))));
        panel.addView(PocketUi.text(a,"小米 / HyperOS：请在系统中允许自启动，并将电池策略设为无限制。系统强行停止、重启恢复限制或省电策略仍可能暂停后台工作。待办目前是清单记录，不等同于定时闹钟。",12,p.muted));
        ScrollView scroll=new ScrollView(a);scroll.addView(panel);new RoundedDialogBuilder(a).setTitle("通知与后台运行").setView(scroll).setNegativeButton("完成",null).show();
    }
    private static Switch toggle(Activity a,ThemePalette p,LinearLayout panel,String title,boolean value,java.util.function.Consumer<Boolean> action){Switch s=new Switch(a);s.setText(title);s.setTextColor(p.text);s.setTextSize(15);s.setMinHeight(PocketUi.dp(a,56));s.setChecked(value);panel.addView(s);s.setOnCheckedChangeListener((b,on)->action.accept(on));return s;}
    private static void open(Activity a,Intent i){try{a.startActivity(i);}catch(Exception e){Toast.makeText(a,"请在系统设置中打开 Pocket Assistant 的权限页面",Toast.LENGTH_LONG).show();}}
}
