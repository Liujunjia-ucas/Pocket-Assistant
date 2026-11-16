package com.codexpocket.app;

import android.app.Activity;
import android.content.SharedPreferences;
import android.widget.*;

/** Independent consent for collection and each automatic cloud-sharing purpose. */
final class PrivacySettings {
    static void show(Activity activity,ThemePalette palette){
        LinearLayout body=new LinearLayout(activity);body.setOrientation(LinearLayout.VERTICAL);
        int pad=PocketUi.dp(activity,20);body.setPadding(pad,pad,pad,pad);
        TextView description=PocketUi.text(activity,
            "聊天、图片和文件会发送到你选择的 API 服务商；语音输入/朗读可能由系统服务或火山语音联网处理。费用与数据保留由服务商决定。\n\n自动接入和私人记录共享默认关闭。撤销开关不能撤回已经发送的数据。结构化记录在本机加密；已导出文件与临时附件不属于加密记录。",14,palette.text);
        body.addView(description);
        Button policy=new Button(activity);policy.setText("完整隐私、安全、风险与许可证说明");PocketUi.button(policy,palette,false);body.addView(policy);
        policy.setOnClickListener(v->new RoundedDialogBuilder(activity).setTitle("公开说明").setItems(new String[]{"隐私说明","安全说明","授权与风险","开源许可证","第三方声明"},(d,n)->document(activity,palette,new String[]{"PRIVACY.md","SECURITY.md","RISK-NOTICE.md","LICENSE","THIRD-PARTY-NOTICES.md"}[n])).show());
        SharedPreferences connections=activity.getSharedPreferences("assistant_connections",0);
        SharedPreferences privacy=activity.getSharedPreferences("privacy",0);
        toggle(activity,body,palette,"自动读取已授权的短信、通话、通知与邮箱",connections.getBoolean("auto_sync",false),on->{
            connections.edit().putBoolean("auto_sync",on).apply();AssistantSync.schedule(activity);
            if(on)AssistantSync.request(activity,true);else AssistantSync.cancel();
        });
        cloudToggle(activity,body,palette,"云端 AI 整理通讯内容",connections,"ai_triage",
            "候选短信、邮件、通知的发送者、标题和正文将发送到你配置的模型平台。内容可能涉及你和他人的隐私，过滤规则不能保证识别全部敏感信息。",()->AssistantSync.request(activity,true));
        cloudToggle(activity,body,palette,"助理请求自动附加私人记录",privacy,"share_context",
            "助理可把重要事项、待办和笔记自动附加到模型请求，指令分类也可附加相关已有记录。只在你信任所配置的平台并有权分享这些资料时开启。",()->{});
        for(String[] entry:new String[][]{{"sms_notifications","接入短信通知"},{"mail_notifications","接入邮件通知"},{"call_notifications","接入未接来电通知"}})
            toggle(activity,body,palette,entry[1],connections.getBoolean(entry[0],false),on->connections.edit().putBoolean(entry[0],on).apply());
        Button system=new Button(activity);system.setText("系统权限与通知访问设置");PocketUi.button(system,palette,false);body.addView(system);
        system.setOnClickListener(v->activity.startActivity(new android.content.Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,android.net.Uri.parse("package:"+activity.getPackageName()))));
        Button clear=new Button(activity);clear.setText("清除本机全部数据");PocketUi.button(clear,palette,false);body.addView(clear);
        clear.setOnClickListener(v->new RoundedDialogBuilder(activity).setTitle("清除本机全部数据？")
            .setMessage("删除 API 密钥、配对信息、邮箱配置、对话、记忆、待办、缓存与应用设置并关闭应用。无法删除你导出到相册/下载目录的文件、电脑端资料或服务商保存的数据。")
            .setPositiveButton("清除",(d,w)->{AssistantSync.cancel();((android.app.ActivityManager)activity.getSystemService(Activity.ACTIVITY_SERVICE)).clearApplicationUserData();})
            .setNegativeButton("取消",null).show());
        ScrollView scroll=new ScrollView(activity);scroll.addView(body);
        new RoundedDialogBuilder(activity).setTitle("隐私与数据").setView(scroll).setPositiveButton("完成",null).show();
    }
    private static void cloudToggle(Activity a,LinearLayout body,ThemePalette p,String label,SharedPreferences prefs,String key,String detail,Runnable enabled){
        Switch control=new Switch(a);control.setText(label);control.setTextColor(p.text);control.setChecked(prefs.getBoolean(key,false));body.addView(control);
        control.setOnCheckedChangeListener((button,on)->{
            if(!on){prefs.edit().putBoolean(key,false).apply();AssistantSync.cancel();return;}
            new RoundedDialogBuilder(a).setTitle("允许分享私人内容？").setMessage(detail+"\n\n可能产生费用，服务商可能保存请求。")
                .setPositiveButton("允许",(d,w)->{prefs.edit().putBoolean(key,true).apply();enabled.run();})
                .setNegativeButton("取消",(d,w)->control.setChecked(false))
                .setOnCancelListener(d->control.setChecked(false)).show();
        });
    }
    private static void toggle(Activity a,LinearLayout body,ThemePalette p,String label,boolean checked,java.util.function.Consumer<Boolean> action){
        Switch control=new Switch(a);control.setText(label);control.setTextColor(p.text);control.setPadding(0,PocketUi.dp(a,10),0,PocketUi.dp(a,10));control.setChecked(checked);body.addView(control);control.setOnCheckedChangeListener((v,on)->action.accept(on));
    }
    private static void document(Activity a,ThemePalette p,String file){
        try(java.io.InputStream input=a.getAssets().open("community-notices/"+file);java.io.ByteArrayOutputStream output=new java.io.ByteArrayOutputStream()){
            byte[] buffer=new byte[4096];int count;while((count=input.read(buffer))!=-1){if(output.size()+count>180000)throw new java.io.IOException("Document too large");output.write(buffer,0,count);}
            TextView text=PocketUi.text(a,output.toString("UTF-8"),13,p.text);int pad=PocketUi.dp(a,20);text.setPadding(pad,pad,pad,pad);text.setTextIsSelectable(true);ScrollView scroll=new ScrollView(a);scroll.addView(text);
            new RoundedDialogBuilder(a).setTitle(file.equals("LICENSE")?"MIT License":file.replace(".md","")).setView(scroll).setPositiveButton("关闭",null).show();
        }catch(Exception e){Toast.makeText(a,"说明文件不可用，请查看项目公开文档。",Toast.LENGTH_LONG).show();}
    }
    private PrivacySettings(){}
}
