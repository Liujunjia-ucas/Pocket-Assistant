package com.codexpocket.app;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import org.json.*;

/** Durable handoffs: navigation never resubmits a request. */
final class AssistantWorkStore {
    private static final Object LOCK=new Object();private final File file;
    AssistantWorkStore(File directory)throws IOException{if(!directory.isDirectory()&&!directory.mkdirs())throw new IOException("无法保存工作记录");file=new File(directory,"work.json");}
    JSONArray list()throws Exception{synchronized(LOCK){if(!file.exists())return new JSONArray();if(file.length()>2*1024*1024)throw new IOException("工作记录过大");return new JSONArray(new String(FileVault.read(file),StandardCharsets.UTF_8));}}
    private void save(JSONArray rows)throws Exception{byte[] bytes=rows.toString().getBytes(StandardCharsets.UTF_8);if(bytes.length>2*1024*1024)throw new IOException("工作记录已满");File temp=new File(file.getParentFile(),"work.pending");try{try(FileOutputStream out=new FileOutputStream(temp)){out.write(FileVault.encrypt(file,bytes));out.getFD().sync();}Files.move(temp.toPath(),file.toPath(),StandardCopyOption.REPLACE_EXISTING,StandardCopyOption.ATOMIC_MOVE);}finally{Files.deleteIfExists(temp.toPath());}}
    JSONObject get(String id)throws Exception{JSONArray rows=list();for(int i=0;i<rows.length();i++)if(id.equals(rows.getJSONObject(i).optString("id")))return rows.getJSONObject(i);return null;}
    JSONObject create(String route,String prompt)throws Exception{synchronized(LOCK){if(!Arrays.asList("android","windows").contains(route)||prompt.trim().isEmpty()||prompt.length()>6000)throw new IOException("工作内容无效");JSONArray rows=list();for(int i=0;i<rows.length();i++){JSONObject r=rows.getJSONObject(i);if(route.equals(r.optString("route"))&&prompt.equals(r.optString("prompt"))&&Arrays.asList("prepared","running","approval").contains(r.optString("state")))return r;}
        JSONObject next=new JSONObject().put("id",UUID.randomUUID().toString()).put("route",route).put("prompt",prompt).put("state","prepared").put("chat","").put("summary","等待处理").put("updated",System.currentTimeMillis());JSONArray all=new JSONArray().put(next);for(int i=0;i<rows.length();i++){JSONObject r=rows.getJSONObject(i);if(all.length()<100||Arrays.asList("prepared","running","approval").contains(r.optString("state")))all.put(r);}if(all.length()>150)throw new IOException("进行中的工作过多，请先完成已有工作");save(all);return next;
    }}
    void update(String id,String state,String chat,String summary)throws Exception{synchronized(LOCK){if(!Arrays.asList("prepared","running","approval","complete","failed","interrupted").contains(state))throw new IOException("工作状态无效");JSONArray rows=list();for(int i=0;i<rows.length();i++){JSONObject r=rows.getJSONObject(i);if(id.equals(r.optString("id"))){r.put("state",state).put("chat",chat).put("summary",summary.substring(0,Math.min(1500,summary.length()))).put("updated",System.currentTimeMillis());save(rows);return;}}throw new IOException("工作记录不存在");}}
    void bindTurn(String id,String turn)throws Exception{synchronized(LOCK){if(turn.isEmpty()||turn.length()>200)throw new IOException("电脑执行编号无效");JSONArray rows=list();for(int i=0;i<rows.length();i++){JSONObject r=rows.getJSONObject(i);if(id.equals(r.optString("id"))){r.put("turn",turn);save(rows);return;}}throw new IOException("工作记录不存在");}}
    void recoverAndroid()throws Exception{synchronized(LOCK){JSONArray rows=list();boolean changed=false;for(int i=0;i<rows.length();i++){JSONObject r=rows.getJSONObject(i);if("android".equals(r.optString("route"))&&"running".equals(r.optString("state"))){r.put("state","interrupted").put("summary","上次运行已中断，打开会话查看已保存的内容");changed=true;}}if(changed)save(rows);}}
    static String label(String state){return switch(state){case "running"->"处理中";case "approval"->"等待确认";case "complete"->"已完成";case "failed"->"未完成";case "interrupted"->"已中断";default->"待处理";};}
}
