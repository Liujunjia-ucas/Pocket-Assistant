package com.codexpocket.app;

import org.json.*;
import java.util.*;

public final class AssistantFastRouterTest {
    static void check(boolean v,String reason){if(!v)throw new AssertionError(reason);}
    static AssistantRouter.Kind route(String s){return AssistantFastRouter.local(s,new JSONArray()).kind;}
    public static void main(String[] args)throws Exception{
        String[] notes={"随手记：今天想到一个新的课题","帮我记住我在国科大上学","记一下我今年20岁","记住我不喜欢咖啡"};for(String s:notes)check(route(s)==AssistantRouter.Kind.NOTE,"local note: "+s);
        for(String s:new String[]{"帮我写一份报告","帮我生成Word文档","请帮我搜索今天的天气","现在帮我编写Python代码","请翻译这段话","帮我解释Windows的原理","帮我写一个在Windows运行的程序"})check(route(s)==AssistantRouter.Kind.ANDROID,"local phone work: "+s);
        for(String s:new String[]{"帮我在电脑上修改配置文件","在本地项目里运行测试","帮我修改电脑上的代码","请在Windows中打开记事本"})check(route(s)==AssistantRouter.Kind.WINDOWS,"explicit computer work: "+s);
        for(String s:new String[]{"今天有什么待办","列出未接来电","帮我安排今天","帮我回复邮件"})check(route(s)==AssistantRouter.Kind.CHAT,"daily query/actions: "+s);
        for(String s:new String[]{"新增待办：给实验室送样品","明天三点开组会","明天我要写报告"})check(route(s)==AssistantRouter.Kind.TASK,"future tasks: "+s);
        for(String s:new String[]{"不要帮我写报告","如果在电脑上修改配置怎么办","提醒我取快递，同时帮我写报告","明天张三开会","我今天看医生很开心","帮我写报告，然后提醒我周五交","把那个改到周五","我明天不去上课"})check(route(s)==AssistantRouter.Kind.UNKNOWN,"uncertainty preserved: "+s);
        for(String s:new String[]{"帮我写报告，提醒我取快递","帮我写报告并提醒我交作业","帮我在电脑上修改配置，生成一份报告"})check(route(s)==AssistantRouter.Kind.UNKNOWN,"no partial execution of mixed instructions: "+s);
        JSONArray records=new JSONArray().put(new JSONObject().put("id","task-1").put("kind","task").put("text","明天取快递"));
        check(AssistantFastRouter.local("把明天取快递标记完成",records).target.equals("task-1"),"exact completion target");check(AssistantFastRouter.local("删除待办：明天取快递",records).kind==AssistantRouter.Kind.DELETE,"exact deletion");
        check(AssistantFastRouter.local("把明天取快递改为周五取快递",records).text.equals("周五取快递"),"full replacement preserves task");check(AssistantFastRouter.local("把明天取快递改为周五",records).kind==AssistantRouter.Kind.UNKNOWN,"date-only edit cannot erase action");
        records.put(new JSONObject(records.getJSONObject(0).toString()).put("id","task-2"));check(AssistantFastRouter.local("把明天取快递标记完成",records).kind==AssistantRouter.Kind.CLARIFY,"ambiguous duplicate targets are not edited");
        JSONArray all=new JSONArray();for(int i=0;i<100;i++)all.put(new JSONObject().put("id","r"+i).put("kind","task").put("text",i==80?"明天取快递":"日常任务"+i));JSONArray selected=AssistantFastRouter.context("把明天取快递改到周五",all);check(selected.length()<=16&&selected.getJSONObject(0).getString("id").equals("r80"),"relevant context prioritized and bounded");check(AssistantFastRouter.context("帮我写报告",all).length()==0,"unrelated notes never inflate work classification");
        check(AssistantFastRouter.completedJson("```json\n{\"text\":\"带有}和\\\"的文字\"")==null,"partial quoted object not accepted");check(AssistantFastRouter.completedJson("```json\n{\"intent\":\"chat\"}\n").equals("{\"intent\":\"chat\"}"),"finished JSON delivered without waiting for stream trailer");
        for(int i=0;i<30;i++)for(String s:notes)route(s);long[] times=new long[200];for(int i=0;i<times.length;i++){long before=System.nanoTime();route(i%2==0?"帮我生成Word文档":"明天三点开组会");times[i]=System.nanoTime()-before;}Arrays.sort(times);double p95=times[189]/1e6;check(p95<1000,"local routing within one second in JVM fixture");System.out.printf(Locale.ROOT,"PASS: fast local instruction routes, exact edits, mixed-command guard, compact context, early JSON; local JVM routing p95 %.3f ms (not device/end-to-end latency).%n",p95);
    }
}
