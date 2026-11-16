package com.codexpocket.app;

import org.json.*;
import java.util.*;

/** Bounded delivery identity, independent of changing AI summaries or process lifetime. */
final class NotificationLedger {
    private static final long KEEP=8*24*60*60*1000L;
    final JSONObject data;
    NotificationLedger(String json)throws Exception{data=new JSONObject(json);}
    boolean claim(String key,long now)throws Exception{if(key.isEmpty()||key.length()>300)throw new java.io.IOException("通知标识无效");if(data.has(key))return false;data.put(key,now);List<String> keys=new ArrayList<>();Iterator<String> it=data.keys();while(it.hasNext())keys.add(it.next());keys.sort(Comparator.comparingLong(k->data.optLong(k)));for(int i=0;i<keys.size();i++)if(now-data.optLong(keys.get(i))>KEEP||i<keys.size()-1500)data.remove(keys.get(i));return true;}
}
