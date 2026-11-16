package com.codexpocket.app;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.UUID;
import org.json.*;

/** User tasks and automatically derived tasks share an atomic store and one process lock. */
final class AssistantStore {
    private static final Object LOCK=new Object();
    private final File file;
    AssistantStore(File directory)throws IOException{if(!directory.isDirectory()&&!directory.mkdirs())throw new IOException("无法建立助理资料目录");file=new File(directory,"organizer.json");}
    private JSONObject read()throws Exception{
        if(!file.exists())return new JSONObject().put("version",1).put("items",new JSONArray());
        if(file.length()>2*1024*1024)throw new IOException("助理资料过大，请先整理");JSONObject data=new JSONObject(new String(FileVault.read(file),StandardCharsets.UTF_8));
        if(data.optInt("version")!=1||data.optJSONArray("items")==null)throw new IOException("助理资料无法读取，原文件已保留");return data;
    }
    JSONArray list()throws Exception{synchronized(LOCK){return read().getJSONArray("items");}}
    private void write(JSONObject data)throws Exception{
        byte[] bytes=data.toString().getBytes(StandardCharsets.UTF_8);if(bytes.length>2*1024*1024)throw new IOException("助理资料已满，请先整理");File temp=new File(file.getParentFile(),"organizer.pending");
        try{try(FileOutputStream out=new FileOutputStream(temp)){out.write(FileVault.encrypt(file,bytes));out.getFD().sync();}Files.move(temp.toPath(),file.toPath(),StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);}finally{Files.deleteIfExists(temp.toPath());}
    }
    private static String text(String value)throws IOException{String v=value.trim();if(v.isEmpty()||v.length()>6000)throw new IOException("请输入 1 至 6000 字");return v;}
    String add(String kind,String value)throws Exception{synchronized(LOCK){
        if(!kind.equals("task")&&!kind.equals("note"))throw new IOException("资料类型无效");String v=text(value);JSONObject data=read();JSONArray items=data.getJSONArray("items");if(items.length()>=500)throw new IOException("最多保存 500 条，请先整理");String id=UUID.randomUUID().toString();
        JSONObject item=new JSONObject().put("id",id).put("kind",kind).put("text",v).put("done",false).put("created",System.currentTimeMillis());JSONArray next=new JSONArray().put(item);for(int i=0;i<items.length();i++)next.put(items.get(i));data.put("items",next);write(data);return id;
    }}
    JSONArray addRequestedTasks(java.util.List<String> values)throws Exception{return addRequestedTasks(values,"");}
    JSONArray addRequestedTasks(java.util.List<String> values,String input)throws Exception{synchronized(LOCK){
        if(values.isEmpty()||values.size()>8)throw new IOException("一次最多添加 8 项待办");JSONObject data=read();JSONArray items=data.getJSONArray("items"),created=new JSONArray(),next=new JSONArray();java.util.Set<String> existing=new java.util.HashSet<>();
        for(int i=0;i<items.length();i++){JSONObject row=items.getJSONObject(i);if("task".equals(row.optString("kind"))&&!row.optBoolean("done")){
            JSONObject comparable=new JSONObject(row.toString());if(!row.has("sourceText"))TaskDescription.from(row.optString("text"),row.optLong("created",System.currentTimeMillis())).put(comparable);existing.add(TaskDescription.key(comparable));
        }}
        long now=System.currentTimeMillis();
        for(String value:values){String v=text(value);TaskDescription task=TaskDescription.from(v,now);
            if(values.size()==1&&!input.isEmpty()){TaskDescription full=TaskDescription.from(input,now);if(full.title.equals(task.title))task=full;}
            JSONObject item=new JSONObject().put("id",UUID.randomUUID().toString()).put("kind","task").put("done",false).put("created",now).put("inputIntent",true);task.put(item);if(!input.isEmpty())item.put("inputText",input);if(!existing.add(TaskDescription.key(item)))continue;created.put(item);next.put(item);}
        if(items.length()+created.length()>500)throw new IOException("待办已满，请先整理");for(int i=0;i<items.length();i++)next.put(items.get(i));if(created.length()>0){data.put("items",next);write(data);}return created;
    }}
    void update(String id,String value,boolean done)throws Exception{synchronized(LOCK){
        String v=text(value);JSONObject data=read();JSONArray items=data.getJSONArray("items");for(int i=0;i<items.length();i++){JSONObject item=items.getJSONObject(i);if(id.equals(item.optString("id"))){item.put("text",v).put("done",done).put("edited",true);write(data);return;}}throw new IOException("这条记录已不存在");
    }}
    void updateSchedule(String id,String value,String time,boolean done)throws Exception{synchronized(LOCK){
        String v=text(value),when=time.trim();if(when.length()>200)throw new IOException("时间最多 200 字");JSONObject data=read();JSONArray items=data.getJSONArray("items");
        for(int i=0;i<items.length();i++){JSONObject item=items.getJSONObject(i);if(id.equals(item.optString("id"))){if(!when.equals(item.optString("timeText"))){TaskDescription parsed=TaskDescription.from(when+" "+v,System.currentTimeMillis());item.put("timeText",when).put("scheduledDate",parsed.time.equals(when)?parsed.date:"");}item.put("text",v).put("done",done).put("edited",true);write(data);return;}}throw new IOException("这条记录已不存在");
    }}
    private void replaceTask(String id,String value)throws Exception{
        TaskDescription task=TaskDescription.from(text(value),System.currentTimeMillis());JSONObject data=read();JSONArray items=data.getJSONArray("items");for(int i=0;i<items.length();i++){JSONObject item=items.getJSONObject(i);if(id.equals(item.optString("id"))){String previousTime=item.optString("timeText"),previousDate=item.optString("scheduledDate");task.put(item);if(task.time.isEmpty())item.put("timeText",previousTime).put("scheduledDate",previousDate);item.put("edited",true);write(data);return;}}throw new IOException("这条记录已不存在");
    }
    void applyInstruction(AssistantRouter.Decision decision)throws Exception{synchronized(LOCK){
        JSONObject current=null;JSONArray rows=list();for(int i=0;i<rows.length();i++){JSONObject r=rows.getJSONObject(i);if(decision.target.equals(r.optString("id")))current=r;}
        if(current==null||!decision.expected.equals(current.optString("text")))throw new IOException("记录已经改变，请重新说明要处理哪一条");
        switch(decision.kind){case COMPLETE:if(!"task".equals(current.optString("kind")))throw new IOException("这条不是待办");update(decision.target,current.getString("text"),true);break;case DELETE:delete(decision.target);break;case UPDATE:if("task".equals(current.optString("kind")))replaceTask(decision.target,decision.text);else update(decision.target,decision.text,current.optBoolean("done"));break;default:throw new IOException("不支持的记录操作");}
    }}
    void delete(String id)throws Exception{synchronized(LOCK){
        JSONObject data=read();JSONArray items=data.getJSONArray("items"),kept=new JSONArray(),deleted=data.optJSONArray("autoDeleted");if(deleted==null)deleted=new JSONArray();for(int i=0;i<items.length();i++){JSONObject item=items.getJSONObject(i);if(!id.equals(item.optString("id")))kept.put(item);else if(!item.optString("origin").isEmpty())deleted.put(item.getString("origin"));}
        while(deleted.length()>1000)deleted.remove(0);data.put("items",kept).put("autoDeleted",deleted);write(data);
    }}
    String upsertAutomatic(String origin,String value,long at)throws Exception{synchronized(LOCK){
        if(origin.isEmpty()||origin.length()>200)throw new IOException("自动待办来源无效");String v=text(value);JSONObject data=read();JSONArray items=data.getJSONArray("items"),deleted=data.optJSONArray("autoDeleted");for(int i=0;deleted!=null&&i<deleted.length();i++)if(origin.equals(deleted.optString(i)))return "";
        for(int i=0;i<items.length();i++){JSONObject item=items.getJSONObject(i);if(origin.equals(item.optString("origin"))){if(!item.optBoolean("edited")&&!item.optBoolean("done")){item.put("text",v);write(data);}return item.getString("id");}}
        if(items.length()>=500)throw new IOException("待办已满，请先整理");String id=UUID.randomUUID().toString();JSONObject item=new JSONObject().put("id",id).put("kind","task").put("text",v).put("done",false).put("auto",true).put("origin",origin).put("created",at);JSONArray next=new JSONArray().put(item);for(int i=0;i<items.length();i++)next.put(items.get(i));data.put("items",next);write(data);return id;
    }}
    void completeAutomatic(String origin)throws Exception{synchronized(LOCK){JSONObject data=read();JSONArray items=data.getJSONArray("items");for(int i=0;i<items.length();i++){JSONObject item=items.getJSONObject(i);if(origin.equals(item.optString("origin"))&&!item.optBoolean("done")){item.put("done",true);write(data);return;}}}}
    String plan()throws Exception{synchronized(LOCK){
        JSONArray items=list();StringBuilder out=new StringBuilder("请根据我下面的待办，帮我理清优先级和下一步，做一份简洁、可调整的安排。信息不足时先问我，不要假设已安排日历或已经执行。\n\n");int count=0;for(int i=0;i<items.length()&&count<20;i++){JSONObject item=items.getJSONObject(i);if("task".equals(item.optString("kind"))&&!item.optBoolean("done")){String v=TaskDescription.display(item);out.append("- ").append(v,0,Math.min(800,v.length())).append('\n');count++;}}if(count==0)out.append("我还没有记录待办，请先问我今天最想处理的事情。");return out.toString();
    }}
}
