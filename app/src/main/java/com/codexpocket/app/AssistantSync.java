package com.codexpocket.app;

import android.Manifest;
import android.app.job.*;
import android.content.*;
import android.database.ContentObserver;
import android.net.*;
import android.os.*;
import android.provider.*;
import org.json.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

/** Jobs, foreground observers and notification callbacks feed the same durable pipeline. */
final class AssistantSync {
    private static final ExecutorService WORK=Executors.newSingleThreadExecutor();
    private static final AtomicBoolean RUNNING=new AtomicBoolean();
    private static final AtomicBoolean PENDING=new AtomicBoolean(),PENDING_FORCE=new AtomicBoolean();
    private static volatile DirectApi currentApi;private static volatile ImapMailbox currentMail;
    private static final int PERIODIC=7401,SOON=7402;
    static boolean enabled(Context c){return c.getSharedPreferences("assistant_connections",0).getBoolean("auto_sync",false);}
    static void schedule(Context context){Context c=context.getApplicationContext();JobScheduler jobs=(JobScheduler)c.getSystemService(Context.JOB_SCHEDULER_SERVICE);if(jobs==null)return;if(!enabled(c)){jobs.cancel(PERIODIC);jobs.cancel(SOON);return;}jobs.schedule(new JobInfo.Builder(PERIODIC,new ComponentName(c,AssistantSyncJob.class)).setPeriodic(15*60*1000).setPersisted(true).build());}
    static void soon(Context c){if(!enabled(c))return;JobScheduler jobs=(JobScheduler)c.getSystemService(Context.JOB_SCHEDULER_SERVICE);if(jobs!=null)jobs.schedule(new JobInfo.Builder(SOON,new ComponentName(c,AssistantSyncJob.class)).setMinimumLatency(1500).setOverrideDeadline(20000).build());}
    static void request(Context context,boolean force){Context c=context.getApplicationContext();schedule(c);if(!enabled(c))return;if(!force&&System.currentTimeMillis()-c.getSharedPreferences("assistant_connections",0).getLong("last_attempt",0)<30000)return;runAsync(c,()->{},force);}
    static boolean runAsync(Context c,Runnable done,boolean force){
        if(!enabled(c)){done.run();return false;}if(!RUNNING.compareAndSet(false,true)){PENDING.set(true);if(force)PENDING_FORCE.set(true);done.run();return false;}
        WORK.execute(()->{try{sync(c,force);}catch(Exception e){status(c,"engine","整理暂未完成，稍后自动重试");}finally{currentApi=null;currentMail=null;RUNNING.set(false);done.run();if(PENDING.getAndSet(false))runAsync(c,()->{},PENDING_FORCE.getAndSet(false));}});
        return true;
    }
    static void cancel(){DirectApi api=currentApi;if(api!=null)api.cancel();ImapMailbox mail=currentMail;if(mail!=null)mail.cancel();}
    private static void status(Context c,String source,String text){try{SmartRepository.status(c,source,text);}catch(Exception ignored){}}
    private static boolean connected(Context c){ConnectivityManager manager=(ConnectivityManager)c.getSystemService(Context.CONNECTIVITY_SERVICE);Network network=manager==null?null:manager.getActiveNetwork();NetworkCapabilities cap=network==null?null:manager.getNetworkCapabilities(network);return cap!=null&&cap.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET);}
    private static String mailIdentity(JSONObject a){return AssistantTriage.hash(a.optString("host").toLowerCase(Locale.ROOT)+":"+a.optInt("port")+"/"+a.optString("user"));}
    private static boolean currentAccount(Context c,JSONObject record)throws Exception{String id=record.optString("account");if(id.isEmpty())return true;JSONObject a=MailAccounts.get(c,id);return a!=null&&mailIdentity(a).equals(record.optString("accountIdentity"));}
    private static void sync(Context c,boolean force)throws Exception{
        SharedPreferences settings=c.getSharedPreferences("assistant_connections",0);settings.edit().putLong("last_attempt",System.currentTimeMillis()).apply();status(c,"engine","正在自动整理");List<JSONObject> all=new ArrayList<>();
        if(AssistantSources.allowed(c,Manifest.permission.READ_SMS))try{all.addAll(AssistantSources.sms(c));status(c,"sms","已同步");}catch(Exception e){status(c,"sms","短信读取受限，请检查系统权限");}else status(c,"sms","等待短信权限，可使用通知接入");
        if(AssistantSources.allowed(c,Manifest.permission.READ_CALL_LOG))try{List<JSONObject> calls=AssistantSources.calls(c);all.addAll(calls);status(c,"calls",calls.isEmpty()?"读取成功，最近 7 天系统返回 0 条；可在信息来源→电话检查限制":"已同步 · 最近 7 天 "+calls.size()+" 条");}catch(Exception e){status(c,"calls",e instanceof CallHistory.AccessException?e.getMessage():"通话记录读取失败，可在信息来源→电话查看并重新授权");}else status(c,"calls","等待通话记录权限，可使用未接来电通知");
        try{JSONArray notifications=AssistantNotificationStore.list(c);for(int i=0;i<notifications.length();i++){JSONObject n=notifications.getJSONObject(i);if(n.optLong("at")>=System.currentTimeMillis()-7*AssistantTriage.DAY){n.put("id","notification:"+n.optString("id"));all.add(n);}}String listeners=Settings.Secure.getString(c.getContentResolver(),"enabled_notification_listeners");String target=new ComponentName(c,AssistantNotificationListener.class).flattenToString(),shortTarget=new ComponentName(c,AssistantNotificationListener.class).flattenToShortString();boolean allowed=listeners!=null&&Arrays.asList(listeners.split(":")).stream().anyMatch(v->v.equals(target)||v.equals(shortTarget));status(c,"notifications",allowed?"已接入新通知 · 缓存 "+notifications.length()+" 条":"等待系统通知使用权");}catch(Exception e){status(c,"notifications","通知缓存读取失败");}
        boolean online=connected(c);long lastMail=settings.getLong("last_mail_attempt",0);boolean pollMail=force||System.currentTimeMillis()-lastMail>=5*60000;int fetched=0;
        if(online&&pollMail){settings.edit().putLong("last_mail_attempt",System.currentTimeMillis()).apply();JSONArray accounts=MailAccounts.list(c);
            for(int i=0;i<accounts.length()&&enabled(c);i++){JSONObject meta=accounts.getJSONObject(i);if(!meta.optBoolean("sync",true))continue;String id=meta.getString("id");JSONObject account=MailAccounts.get(c,id);if(account==null)continue;
                try(ImapMailbox mail=new ImapMailbox()){currentMail=mail;mail.connect(account.getString("host"),account.getInt("port"),account.getString("user"),account.getString("password"));for(JSONObject r:mail.list()){
                    if(!enabled(c))break;if(r.optLong("at")<System.currentTimeMillis()-7*AssistantTriage.DAY)continue;String identity=mailIdentity(account);r.put("id","mail:"+id+":"+identity+":"+r.optString("uidValidity")+":"+r.optString("uid")).put("account",id).put("accountIdentity",identity).put("headerStamp",AssistantTriage.hash(r.optString("source")+r.optString("title")+r.optString("date")));
                    if(!SmartRepository.needs(c,r,true))continue;if(fetched>=12)break;r.put("body",AssistantTriage.clip(mail.body(r.getString("uid"),r.optString("uidValidity")),6000));all.add(r);fetched++;
                }status(c,"mail:"+id,"已同步 · "+account.optString("user"));}catch(Exception e){status(c,"mail:"+id,"连接未成功，请检查此邮箱的 IMAP 配置或网络");}finally{currentMail=null;}
            }
        }else if(!online)status(c,"mail","当前离线，本机资料照常整理");
        all.sort(Comparator.comparingLong((JSONObject r)->r.optLong("at")));LinkedHashMap<String,JSONObject> candidates=new LinkedHashMap<>();long now=System.currentTimeMillis();
        if(!enabled(c))return;Iterator<JSONObject> checked=all.iterator();while(checked.hasNext())if(!currentAccount(c,checked.next()))checked.remove();SmartRepository.ingest(c,all);SmartState snapshot=new SmartState(SmartRepository.snapshot(c));
        for(JSONObject record:all){if(!enabled(c))return;if(record.optLong("at")<now-7*AssistantTriage.DAY)continue;JSONObject local=AssistantTriage.local(record,now);
            if(local==null&&!AssistantTriage.sensitive(record)&&!AssistantTriage.marketing(record)&&!record.optString("kind").equals("通话记录")&&snapshot.needs(record,true))candidates.put(AssistantTriage.id(record),record);
        }
        if(online&&settings.getBoolean("ai_triage",false)&&!candidates.isEmpty()&&enabled(c)){
            if(force||now-settings.getLong("last_ai_attempt",0)>=120000){settings.edit().putLong("last_ai_attempt",now).apply();try{analyze(c,candidates);status(c,"ai","AI 智能整理已更新");}catch(Exception e){status(c,"ai","AI 暂未完成：请检查模型设置与网络，本机识别继续工作");}}
        }else if(!online&&!candidates.isEmpty())status(c,"ai","等待联网后继续 AI 整理");
        status(c,"engine","已自动整理");settings.edit().putLong("last_sync",System.currentTimeMillis()).apply();AssistantNotifications.important(c);
    }
    private static void analyze(Context c,LinkedHashMap<String,JSONObject> candidates)throws Exception{
        SharedPreferences p=c.getSharedPreferences("android_direct",0);PhoneApiConfig config=PhoneApiConfig.load(c,p.getString("assistant.provider",p.getString("provider","deepseek")));String model=config.model,protocol=config.protocol;
        List<JSONObject> latest=new ArrayList<>(candidates.values());Collections.reverse(latest);LinkedHashMap<String,JSONObject> batch=new LinkedHashMap<>();JSONArray input=new JSONArray();for(JSONObject r:latest){if(batch.size()>=16)break;batch.put(AssistantTriage.id(r),r);input.put(new JSONObject().put("id",AssistantTriage.id(r)).put("kind",r.optString("kind")).put("from",AssistantTriage.clip(r.optString("source"),160)).put("title",AssistantTriage.clip(r.optString("title"),200)).put("body",AssistantTriage.clip(r.optString("body"),2000)));}
        DirectApi api=config.client();currentApi=api;JSONArray messages=new JSONArray().put(new JSONObject().put("role","system").put("content",AssistantTriage.GUIDE)).put(new JSONObject().put("role","user").put("content",input.toString()));String answer=api.chat(model,protocol,messages,3600,t->{});if(!enabled(c)||!c.getSharedPreferences("assistant_connections",0).getBoolean("ai_triage",false))return;
        List<JSONObject> active=new ArrayList<>();for(JSONObject r:batch.values())if(currentAccount(c,r))active.add(r);for(JSONObject insight:AssistantTriage.model(answer,batch)){if(!enabled(c)||!c.getSharedPreferences("assistant_connections",0).getBoolean("ai_triage",false))return;JSONObject r=batch.get(insight.getString("recordId"));if(currentAccount(c,r))SmartRepository.apply(c,r,insight,true);}SmartRepository.markAnalyzed(c,active);
    }
    static ContentObserver observe(Context c){ContentObserver observer=new ContentObserver(new Handler(Looper.getMainLooper())){@Override public void onChange(boolean self){request(c,false);soon(c);}};for(android.net.Uri uri:new android.net.Uri[]{Telephony.Sms.CONTENT_URI,CallLog.Calls.CONTENT_URI})try{c.getContentResolver().registerContentObserver(uri,true,observer);}catch(Exception ignored){}return observer;}
}
