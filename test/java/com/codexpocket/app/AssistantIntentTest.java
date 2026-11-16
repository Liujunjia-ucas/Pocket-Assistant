package com.codexpocket.app;

import org.json.*;
import java.nio.file.*;
import java.util.*;

public final class AssistantIntentTest {
    static void check(boolean v,String why){if(!v)throw new AssertionError(why);}
    static JSONObject result(String text)throws Exception{return new JSONObject().put("intent","task").put("confidence",.98).put("tasks",new JSONArray().put(text));}
    public static void main(String[] args)throws Exception{
        for(String input:new String[]{"明天下午三点开组会","今晚取快递","周五交报告","我需要买牛奶","我计划今天去医院","帮我记一下明天取快递","新增一项代办：交报名表","请帮我记下：周四提交申请"}){
            AssistantIntent.Decision d=AssistantIntent.local(input);check(d.kind==AssistantIntent.Kind.TASK&&!d.tasks.isEmpty(),"natural task: "+input);for(String task:d.tasks)check(input.contains(task),"task preserves source text");
        }
        check(AssistantIntent.local("明天下午三点开组会").tasks.get(0).equals("明天下午三点开组会"),"time preserved, no magic prefix required");
        check(AssistantIntent.local("帮我记一下明天取快递").tasks.get(0).equals("明天取快递"),"remove only instruction prefix");
        check(AssistantIntent.local("周五交报告，晚上取快递").tasks.size()==2,"distinct clauses become separate tasks");
        check(AssistantIntent.local("明天下午，三点开会，地点在主楼").tasks.size()==1,"time/place clauses remain attached to action");
        for(String input:new String[]{"你好","谢谢","今天有什么待办","明天开会吗","开组会怎么准备","我已经取完快递了","取消明天开会","不要新增待办","我明天不去开会","把开会改到周四","如果明天开会就好了","比如明天开组会","帮我写明天开会的发言稿","我今天完成了报告","今天几点开会？"})check(AssistantIntent.local(input).kind==AssistantIntent.Kind.CHAT,"question/negation/completed/generation stays chat: "+input);
        check(AssistantIntent.local("小林明天去医院").kind==AssistantIntent.Kind.UNKNOWN,"third-person plans require interpretation, no local automatic task");
        check(AssistantIntent.local("新增一项待办").kind==AssistantIntent.Kind.CHAT,"empty task not created");
        String utterance="下周带小猫打疫苗，然后预约复查";AssistantIntent.Decision task=AssistantIntent.parse(result(utterance).toString(),utterance);check(task.kind==AssistantIntent.Kind.TASK&&task.tasks.get(0).equals(utterance),"model handles varied vocabulary as original extract");
        check(AssistantIntent.parse(result("明天付款500元").toString(),"下周复查").kind==AssistantIntent.Kind.CHAT,"hallucinated task rejected");
        check(AssistantIntent.parse(result(utterance).put("confidence",.5).toString(),utterance).kind==AssistantIntent.Kind.CHAT,"uncertain classification does not create task");
        check(AssistantIntent.parse(result(utterance).put("intent","shell").toString(),utterance).kind==AssistantIntent.Kind.CHAT,"only supported intent can write tasks");
        JSONArray tooMany=new JSONArray();for(int i=0;i<9;i++)tooMany.put(utterance);check(AssistantIntent.parse(result(utterance).put("tasks",tooMany).toString(),utterance).kind==AssistantIntent.Kind.CHAT,"bounded batch");
        check(AssistantIntent.parse(result(utterance).put("tasks",new JSONArray().put(utterance).put(utterance)).toString(),utterance).tasks.size()==1,"deduplicate parsed tasks");
        try{AssistantIntent.parse("malformed json",utterance);throw new AssertionError("malformed response accepted");}catch(JSONException expected){}
        AssistantStore store=new AssistantStore(Files.createTempDirectory("pocket-intent-").toFile());JSONArray created=store.addRequestedTasks(Arrays.asList("明天取快递","周五交报告"));check(created.length()==2&&store.list().length()==2,"task intent directly persists without chat");
        check(store.addRequestedTasks(Arrays.asList("明天取快递")).length()==0,"resubmission cannot duplicate open task");store.update(created.getJSONObject(0).getString("id"),"明天取快递",true);check(store.addRequestedTasks(Arrays.asList("明天取快递")).length()==1,"explicit new input may readd a completed item");
        int before=store.list().length();try{store.addRequestedTasks(Arrays.asList("有效事项"," "));throw new AssertionError("invalid task accepted");}catch(java.io.IOException expected){}check(store.list().length()==before,"batch failure is atomic");
        for(int i=0;i<created.length();i++)store.delete(created.getJSONObject(i).getString("id"));check(store.list().length()==1,"undo removes only tasks created by that input");
        System.out.println("PASS: natural input/voice intent, chat exclusions, original span evidence, confidence, multi-task splitting, direct atomic writes, dedup and undo.");
    }
}
