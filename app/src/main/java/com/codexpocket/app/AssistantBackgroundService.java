package com.codexpocket.app;

import android.app.*;
import android.content.*;
import android.content.pm.ServiceInfo;
import android.database.ContentObserver;
import android.os.*;

/** Opt-in visible foreground data sync, with periodic jobs retained as a fallback. */
public final class AssistantBackgroundService extends Service {
    private final Handler ui=new Handler(Looper.getMainLooper());private ContentObserver observer;private static volatile boolean running;
    static boolean enabled(Context c){return c.getSharedPreferences("assistant_notifications",0).getBoolean("background",false);}
    static boolean running(){return running;}
    static boolean start(Context c){if(!enabled(c))return false;try{c.startForegroundService(new Intent(c,AssistantBackgroundService.class));return true;}catch(RuntimeException e){return false;}}
    private final Runnable tick=new Runnable(){public void run(){if(!enabled(AssistantBackgroundService.this)){stopSelf();return;}AssistantSync.request(getApplicationContext(),false);AssistantWork.refreshRemote(getApplicationContext());ui.postDelayed(this,60000);}};
    @Override public void onCreate(){super.onCreate();AssistantNotifications.channels(this);Notification n=AssistantNotifications.builder(this,AssistantNotifications.BACKGROUND,"AI助理正在后台运行","自动整理重要事项；点击进入助理。可在设置中关闭。","").setOngoing(true).setAutoCancel(false).setCategory(Notification.CATEGORY_SERVICE).build();if(Build.VERSION.SDK_INT>=29)startForeground(7403,n,ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC);else startForeground(7403,n);running=true;observer=AssistantSync.observe(getApplicationContext());ui.post(tick);}
    @Override public int onStartCommand(Intent intent,int flags,int id){if(!enabled(this)){stopSelf();return START_NOT_STICKY;}return START_STICKY;}
    @Override public void onDestroy(){running=false;ui.removeCallbacksAndMessages(null);if(observer!=null)getContentResolver().unregisterContentObserver(observer);stopForeground(true);super.onDestroy();}
    @Override public IBinder onBind(Intent i){return null;}
}
