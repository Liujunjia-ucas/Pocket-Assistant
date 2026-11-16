package com.codexpocket.app;

import android.content.Context;
import org.json.*;

/** Bounded encrypted local notification cache; unrelated applications are not retained. */
final class AssistantNotificationStore {
    static synchronized JSONArray list(Context c)throws Exception{String raw=SecretStore.load(c,"assistant_notifications");return raw.isEmpty()?new JSONArray():new JSONArray(raw);}
    static synchronized void put(Context c,JSONObject entry)throws Exception{
        JSONArray old=list(c),next=new JSONArray().put(entry);for(int i=0;i<old.length()&&next.length()<100;i++){JSONObject item=old.getJSONObject(i);if(!item.optString("id").equals(entry.optString("id")))next.put(item);}SecretStore.save(c,"assistant_notifications",next.toString());
    }
    static synchronized void clear(Context c)throws Exception{SecretStore.save(c,"assistant_notifications","");}
}
