package com.codexpocket.app;

import org.json.*;
import java.util.*;

/** Pure durable inbox state: source fingerprints, stable event identity and user dismissal. */
final class SmartState {
    final JSONObject data;
    SmartState(JSONObject value)throws Exception{data=value;if(!data.has("version"))data.put("version",1).put("events",new JSONArray()).put("ledger",new JSONObject()).put("sources",new JSONObject());if(data.optInt("version")!=1||data.optJSONArray("events")==null||data.optJSONObject("ledger")==null||data.optJSONObject("sources")==null)throw new java.io.IOException("重要信息资料无法读取，原资料保留");}
    boolean needs(JSONObject r,boolean ai)throws Exception{JSONObject seen=data.getJSONObject("ledger").optJSONObject(AssistantTriage.id(r));return seen==null||!AssistantTriage.fingerprint(r).equals(seen.optString("fp"))||(ai&&!seen.optBoolean("ai"));}
    void seen(JSONObject r,boolean ai)throws Exception{
        JSONObject ledger=data.getJSONObject("ledger"),old=ledger.optJSONObject(AssistantTriage.id(r));boolean same=old!=null&&AssistantTriage.fingerprint(r).equals(old.optString("fp"));ledger.put(AssistantTriage.id(r),new JSONObject().put("fp",AssistantTriage.fingerprint(r)).put("ai",ai||(same&&old.optBoolean("ai"))).put("at",r.optLong("at")));
        if(ledger.length()>1000){List<String> keys=new ArrayList<>();Iterator<String> iterator=ledger.keys();while(iterator.hasNext())keys.add(iterator.next());keys.sort(Comparator.comparingLong(k->ledger.optJSONObject(k).optLong("at")));for(int i=0;i<keys.size()-900;i++)ledger.remove(keys.get(i));}
    }
    JSONObject apply(JSONObject insight)throws Exception{
        if(insight.optBoolean("resolved")&&insight.optString("category").equals("call")){
            JSONObject last=null;JSONArray entries=data.getJSONArray("events");for(int i=0;i<entries.length();i++){JSONObject e=entries.getJSONObject(i);if(e.optString("category").equals("call")&&AssistantTriage.samePerson(insight,e)&&insight.optLong("at")>=e.optLong("at")&&insight.optLong("at")-e.optLong("at")<=7*AssistantTriage.DAY){e.put("resolved",true).put("task",false);last=e;}}return last;
        }
        JSONArray events=data.getJSONArray("events");JSONObject target=null;for(int i=0;i<events.length();i++){JSONObject e=events.getJSONObject(i);boolean followup=insight.optBoolean("resolved")&&insight.optString("category").equals("call")&&e.optString("category").equals("call")&&insight.optString("person").equals(e.optString("person"))&&insight.optLong("at")>=e.optLong("at")&&insight.optLong("at")-e.optLong("at")<=7*AssistantTriage.DAY;if(AssistantTriage.same(e,insight)||followup){target=e;break;}}
        if(target!=null&&target.optBoolean("resolved")&&!insight.optBoolean("resolved")&&insight.optLong("at")>target.optLong("at")&&!insight.optString("recordId").equals(target.optString("recordId")))target=null;
        if(insight.optBoolean("resolved")&&target==null)return null;
        JSONObject next=new JSONObject(insight.toString());if(target!=null){next.put("key",target.getString("key")).put("hidden",target.optBoolean("hidden")).put("seen",target.optBoolean("seen"));if(next.optString("phone").isEmpty()&&!target.optString("phone").isEmpty())next.put("phone",target.optString("phone")).put("person",target.optString("person"));if(target.optBoolean("resolved")&&!next.optBoolean("resolved"))next.put("resolved",true);}
        else next.put("key",AssistantTriage.canonical(next)).put("hidden",false).put("seen",false);
        if(next.optBoolean("resolved"))next.put("task",false);
        List<JSONObject> list=new ArrayList<>();list.add(next);for(int i=0;i<events.length();i++){JSONObject e=events.getJSONObject(i);if(target==null||!e.optString("key").equals(target.optString("key")))list.add(e);}list.sort(Comparator.comparingLong((JSONObject e)->e.optLong("at")).reversed());JSONArray result=new JSONArray();for(JSONObject e:list)if(result.length()<240)result.put(e);data.put("events",result);return next;
    }
    void dismiss(String key)throws Exception{if(key.startsWith("sms-group:")){for(JSONObject row:undismissedGroups())if(key.equals(row.optString("key"))){JSONArray members=row.optJSONArray("groupKeys");for(int i=0;members!=null&&i<members.length();i++)dismiss(members.optString(i));return;}}JSONArray events=data.getJSONArray("events");for(int i=0;i<events.length();i++){JSONObject e=events.getJSONObject(i);if(key.equals(e.optString("key")))e.put("hidden",true).put("seen",true);}}
    private List<JSONObject> undismissedGroups()throws Exception{List<JSONObject> rows=new ArrayList<>();JSONArray entries=data.getJSONArray("events");for(int i=0;i<entries.length();i++){JSONObject e=entries.getJSONObject(i);if(!e.optBoolean("hidden")&&!e.optBoolean("resolved"))rows.add(e);}return AssistantInbox.group(rows);}
    void source(String key,String status,long at)throws Exception{data.getJSONObject("sources").put(key,new JSONObject().put("status",status).put("at",at));}
    List<JSONObject> visible(long now)throws Exception{List<JSONObject> out=new ArrayList<>();JSONArray events=data.getJSONArray("events");for(int i=0;i<events.length();i++){JSONObject e=events.getJSONObject(i);if(!e.optBoolean("hidden")&&!e.optBoolean("resolved")&&e.optLong("at")>=now-7*AssistantTriage.DAY)out.add(e);}return AssistantInbox.group(out);}
}
