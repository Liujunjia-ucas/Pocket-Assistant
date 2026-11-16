package com.codexpocket.app;
import java.time.*;
import java.nio.file.*;
import java.util.*;
import org.json.*;

public class TaskDescriptionTest {
    static void check(boolean ok,String message){if(!ok)throw new AssertionError(message);}
    static final long AT=LocalDate.of(2026,11,15).atTime(10,0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
    static TaskDescription title(String source,String expected){TaskDescription t=TaskDescription.from(source,AT);check(t.title.equals(expected),source+" -> "+t.title+" expected "+expected);check(t.source.equals(source),"exact provenance");return t;}
    public static void main(String[] args)throws Exception{
        TaskDescription t=title("我明天要做线性代数作业","做线性代数作业");check(t.time.equals("明天")&&t.date.equals("2026-11-16"),"requested example preserves tomorrow separately");
        title("明天我要写高等数学作业","写高等数学作业");title("我需要买牛奶","买牛奶");title("我计划今天去医院","去医院");
        t=title("我明天下午三点要去主楼找李老师交2份报告","去主楼找李老师交2份报告");check(t.time.equals("明天下午三点"),"time, name, place, quantity preserved");
        t=title("提醒我周五交报告","交报告");check(t.date.equals("2026-11-20"),"unqualified weekday resolves into future");
        t=title("下周一我要复习线性代数","复习线性代数");check(t.date.equals("2026-11-16"),"next calendar week");
        t=title("每天晚上九点复习英语","复习英语");check(t.date.isEmpty()&&t.time.equals("每天晚上九点"),"recurrence text retained, no invented one-off deadline");
        title("我明天要去医院给妈妈买2盒药","去医院给妈妈买2盒药");
        for(String source:new String[]{"如果明天不下雨就去跑步","不要买含糖饮料","明天的线性代数作业","写明天开会的发言稿","买3个苹果和2盒牛奶","明天之前交报告；不要交错版本"})check(TaskDescription.from(source,AT).source.equals(source)&&TaskDescription.from(source,AT).title.equals(source),"constraints must survive: "+source);
        t=title("明天开会，地点在主楼","开会，地点在主楼");check(t.time.equals("明天"),"place remains in task, time kept separately");
        t=title("明天晚上九点前提交报告","提交报告");JSONObject row=new JSONObject();t.put(row);check(TaskDescription.schedule(row).equals("11月16日 · 晚上九点前"),"deadline display retains qualifier");
        t=title("今晚做物理作业","做物理作业");row=new JSONObject();t.put(row);check(TaskDescription.schedule(row).equals("11月15日 · 晚上"),"tonight does not lose evening");
        check(AssistantFastRouter.local("我明天要做线性代数作业",new JSONArray()).kind==AssistantRouter.Kind.TASK,"example uses local fast route");
        for(String input:new String[]{"我明天不做线性代数作业","我已经做完线性代数作业","如果明天要做线性代数作业","明天张三要做线性代数作业","帮我做线性代数作业"})check(AssistantFastRouter.local(input,new JSONArray()).kind!=AssistantRouter.Kind.TASK,"no guessed intent: "+input);
        AssistantStore store=new AssistantStore(Files.createTempDirectory("tasks360").toFile());
        JSONArray added=store.addRequestedTasks(Collections.singletonList("做线性代数作业"),"我明天要做线性代数作业");row=added.getJSONObject(0);String id=row.getString("id");
        check(row.getString("text").equals("做线性代数作业")&&row.getString("timeText").equals("明天")&&row.getString("sourceText").equals("我明天要做线性代数作业"),"single extracted body recovers original timing");
        check(store.addRequestedTasks(Collections.singletonList("我明天要做线性代数作业")).length()==0,"normalized task deduplicated");
        check(store.addRequestedTasks(Collections.singletonList("我后天要做线性代数作业")).length()==1,"different dates stay independent");
        store.update(id,"做线性代数作业",true);JSONObject current=null;for(int i=0;i<store.list().length();i++)if(store.list().getJSONObject(i).getString("id").equals(id))current=store.list().getJSONObject(i);check(current!=null&&!current.optString("scheduledDate").isEmpty(),"checkbox retains schedule");
        store.updateSchedule(id,"复习线性代数","周五下午三点",false);check(store.plan().contains("下午三点")&&store.plan().contains("复习线性代数"),"edited schedule used in planning");
        store.updateSchedule(id,"复习线性代数","",false);for(int i=0;i<store.list().length();i++)if(store.list().getJSONObject(i).getString("id").equals(id))check(store.list().getJSONObject(i).optString("scheduledDate").isEmpty(),"clearing time clears date");
        AssistantRouter.Decision change=new AssistantRouter.Decision(AssistantRouter.Kind.UPDATE,Collections.emptyList(),id,"周五取快递","","复习线性代数");store.applyInstruction(change);for(int i=0;i<store.list().length();i++)if(store.list().getJSONObject(i).getString("id").equals(id)){JSONObject r=store.list().getJSONObject(i);check(r.getString("text").equals("取快递")&&r.getString("timeText").equals("周五"),"spoken full replacement updates schedule alongside title");}
        store.add("note","我明天要做线性代数作业");check(store.list().getJSONObject(0).getString("text").equals("我明天要做线性代数作业"),"notes not rewritten");
        store.add("task","我明天要做线性代数作业");check(store.list().getJSONObject(0).getString("text").equals("我明天要做线性代数作业"),"manual authoring not rewritten");
        System.out.println("PASS: concise task descriptions, original provenance, dates/deadlines/recurrence, details and constraints, local homework routing, persistence, dedup, schedule editing, notes and manual text.");
    }
}
