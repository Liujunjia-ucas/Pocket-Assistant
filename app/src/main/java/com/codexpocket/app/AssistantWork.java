package com.codexpocket.app;

import android.content.*;
import org.json.*;
import java.io.File;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

final class AssistantWork {
    private static final ExecutorService POLL=Executors.newSingleThreadExecutor();private static final AtomicBoolean POLLING=new AtomicBoolean();
    static AssistantWorkStore store(Context c)throws Exception{return new AssistantWorkStore(new File(c.getFilesDir(),"assistant"));}
    static void changed(Context c){c.sendBroadcast(new Intent(SmartRepository.CHANGED).setPackage(c.getPackageName()));}
    static void update(Context c,String id,String state,String chat,String summary){try{AssistantWorkStore s=store(c);s.update(id,state,chat,summary);changed(c);JSONObject r=s.get(id);if(r!=null)AssistantNotifications.work(c,r);}catch(Exception ignored){}}
    static void remote(Context c,JSONObject snapshot){try{JSONArray rows=store(c).list();for(int i=0;i<rows.length();i++){JSONObject r=rows.getJSONObject(i),result=AssistantRemoteResult.read(r,snapshot);if(result!=null)update(c,r.getString("id"),result.getString("state"),result.getString("chat"),result.getString("summary"));}}catch(Exception ignored){}}
    static void refreshRemote(Context context){Context c=context.getApplicationContext();if(!POLLING.compareAndSet(false,true))return;POLL.execute(()->{try{String token=SecretStore.load(c),base=c.getSharedPreferences("settings",0).getString("base","");if(!BridgeEndpoint.configured(base,token))return;Api api=new Api(base,token);JSONArray rows=store(c).list();for(int i=0;i<rows.length();i++){JSONObject r=rows.getJSONObject(i);if("windows".equals(r.optString("route"))&&!r.optString("chat").isEmpty()&&java.util.Arrays.asList("running","approval").contains(r.optString("state")))remote(c,api.get("/api/threads/"+r.getString("chat")));}}catch(Exception ignored){}finally{POLLING.set(false);}});}
}
