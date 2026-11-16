package com.codexpocket.app;

import java.io.File;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.HashSet;
import org.json.JSONArray;
import org.json.JSONObject;

/** One phone-local fact store; reload under one lock for every operation. */
final class SharedCompanionMemory {
    private static final Object LOCK=new Object();
    private final File file;
    SharedCompanionMemory(File root,File appFiles)throws Exception{
        file=new File(root,"memory.json");
        synchronized(LOCK){
            if(file.isFile()){read();return;}
            if(!root.isDirectory()&&!root.mkdirs())throw new java.io.IOException("无法创建共享记忆目录。");
            JSONObject next=new JSONObject().put("version",1).put("enabled",false).put("epoch",0).put("facts",new JSONArray()).put("forgotten",new JSONArray());
            ArrayList<JSONObject> originals=new ArrayList<>();HashSet<String> forgotten=new HashSet<>();
            for(String directory:new String[]{"companion","companion-chatgpt"}){
                File legacy=new File(appFiles,directory+"/companion.json");if(!legacy.isFile())continue;
                JSONObject data=LocalCompanion.read(legacy);JSONArray facts=data.getJSONArray("facts"),removed=data.getJSONArray("forgotten");
                if(!data.optBoolean("enabled",true))next.put("enabled",false);
                for(int i=0;i<removed.length();i++)forgotten.add(removed.getString(i));
                for(int i=0;i<facts.length();i++)originals.add(facts.getJSONObject(i));
            }
            originals.sort(Comparator.comparing(item->item.optString("reportedAt")));
            LinkedHashMap<String,JSONObject> merged=new LinkedHashMap<>();
            for(JSONObject fact:originals){String key=fact.getString("key");if(forgotten.contains(key+"|"+fact.getString("value")))continue;merged.remove(key);merged.put(key,fact);}
            JSONArray facts=next.getJSONArray("facts");for(JSONObject fact:merged.values())facts.put(fact);while(facts.length()>80)facts.remove(0);
            ArrayList<String> sorted=new ArrayList<>(forgotten);java.util.Collections.sort(sorted);for(String value:sorted)next.getJSONArray("forgotten").put(value);
            if(!forgotten.isEmpty()||!next.getBoolean("enabled"))next.put("epoch",1);
            // The shared file is the migration marker. Both legacy roles are
            // imported together once; deleted facts can never be re-imported.
            LocalCompanion.write(file,next);
        }
    }
    private JSONObject read()throws Exception{
        JSONObject value=LocalCompanion.read(file);value.getJSONArray("facts");value.getJSONArray("forgotten");value.getBoolean("enabled");value.getLong("epoch");return value;
    }
    JSONObject view()throws Exception{synchronized(LOCK){return read();}}
    void record(String user,long time)throws Exception{synchronized(LOCK){JSONObject next=read();if(!next.getBoolean("enabled"))return;LocalCompanion.learn(next,user,time);LocalCompanion.write(file,next);}}
    void update(JSONObject change)throws Exception{
        synchronized(LOCK){
            JSONObject next=read();String action=change.optString("action");boolean invalidate=false;
            if(action.equals("settings")){
                boolean enabled=change.optBoolean("enabled",next.getBoolean("enabled"));invalidate=enabled!=next.getBoolean("enabled");next.put("enabled",enabled);
            }else if(action.equals("clear")||action.equals("forget")){
                JSONArray facts=next.getJSONArray("facts"),forgotten=next.getJSONArray("forgotten");
                for(int i=facts.length()-1;i>=0;i--){JSONObject fact=facts.getJSONObject(i);if(action.equals("clear")||fact.getString("id").equals(change.optString("id"))){forgotten.put(fact.getString("key")+"|"+fact.getString("value"));facts.remove(i);}}
                invalidate=true;
            }else throw new java.io.IOException("不支持的共享记忆操作。");
            if(invalidate)next.put("epoch",next.getLong("epoch")+1);
            LocalCompanion.write(file,next);
        }
    }
}
