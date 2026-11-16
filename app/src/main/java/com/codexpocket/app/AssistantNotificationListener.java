package com.codexpocket.app;

import android.app.Notification;
import android.os.Bundle;
import android.provider.Telephony;
import android.service.notification.*;
import java.util.*;
import org.json.*;

/** Optional fallback for devices restricting SMS/history. Only opted-in mail/SMS apps. */
public final class AssistantNotificationListener extends NotificationListenerService {
    private static final Set<String> MAIL=new HashSet<>(Arrays.asList("com.tencent.androidqqmail","com.netease.mail","com.netease.mobimail","com.google.android.gm","com.microsoft.office.outlook","com.huawei.email","com.android.email","com.samsung.android.email.provider","com.miui.mail"));
    @Override public void onListenerConnected(){AssistantSync.request(this,true);AssistantSync.soon(this);}
    @Override public void onNotificationPosted(StatusBarNotification sbn){
        if(!AssistantSync.enabled(this))return;
        try{
            android.content.SharedPreferences p=getSharedPreferences("assistant_connections",MODE_PRIVATE);String pkg=sbn.getPackageName(),kind="";
            boolean sms=pkg.equals(Telephony.Sms.getDefaultSmsPackage(this))||pkg.equals("com.android.mms")||pkg.equals("com.google.android.apps.messaging")||pkg.equals("com.miui.mms");
            Notification notification=sbn.getNotification();if((notification.flags&Notification.FLAG_GROUP_SUMMARY)!=0)return;Bundle extras=notification.extras;String title=clamp(extras.getCharSequence(Notification.EXTRA_TITLE),240);CharSequence big=extras.getCharSequence(Notification.EXTRA_BIG_TEXT);String body=clamp(big!=null?big:extras.getCharSequence(Notification.EXTRA_TEXT),1600);if(title.isEmpty()&&body.isEmpty())return;
            String dialer=null;try{android.telecom.TelecomManager telecom=(android.telecom.TelecomManager)getSystemService(TELECOM_SERVICE);dialer=telecom==null?null:telecom.getDefaultDialerPackage();}catch(Exception ignored){}boolean phone=pkg.equals(dialer)||Arrays.asList("com.android.dialer","com.google.android.dialer","com.android.phone","com.samsung.android.dialer","com.miui.phone").contains(pkg);
            if(sms&&p.getBoolean("sms_notifications",false))kind="短信通知";else if(MAIL.contains(pkg)&&p.getBoolean("mail_notifications",false))kind="邮件通知";else if(phone&&Notification.CATEGORY_MISSED_CALL.equals(notification.category)&&p.getBoolean("call_notifications",false))kind="未接来电通知";else return;
            String app;try{app=getPackageManager().getApplicationLabel(getPackageManager().getApplicationInfo(pkg,0)).toString();}catch(Exception e){app=pkg;}
            AssistantNotificationStore.put(this,new JSONObject().put("id",sbn.getKey()).put("kind",kind).put("source",app).put("package",pkg).put("title",title).put("body",body).put("at",sbn.getPostTime()).put("date",new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm",Locale.CHINA).format(new Date(sbn.getPostTime()))));
            AssistantSync.request(this,false);AssistantSync.soon(this);
        }catch(Exception ignored){/* Do not log private notification text or credentials. */}
    }
    private static String clamp(CharSequence s,int n){return s==null?"":s.subSequence(0,Math.min(n,s.length())).toString();}
}
