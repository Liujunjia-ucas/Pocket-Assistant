package com.codexpocket.app;

import org.json.*;
import java.nio.file.*;
import java.util.*;

public final class AssistantRouterTest {
    static void check(boolean v,String why){if(!v)throw new AssertionError(why);}
    static JSONObject result(String kind)throws Exception{return new JSONObject().put("intent",kind).put("confidence",.98);}
    public static void main(String[] args)throws Exception{
        for(String s:new String[]{"明天下午三点开组会","今晚取快递","周五交报告","我需要买牛奶","我计划今天去医院","帮我记一下明天取快递","新增一项代办：交报名表","请帮我记下：周四提交申请","明天下午，三点开会，地点在主楼","明天我要写报告"})check(AssistantRouter.local(s).kind==AssistantRouter.Kind.TASK,"clear future task: "+s);
        check(AssistantRouter.local("周五交报告，晚上取快递").tasks.size()==2,"multiple task-only clauses");
        for(String s:new String[]{"明天张三开会","明天李华去医院","明天开组会，晚上张三取快递","我今天看医生很开心"})check(AssistantRouter.local(s).kind==AssistantRouter.Kind.UNKNOWN,"third-party/retrospective context requires model: "+s);
        for(String s:new String[]{"帮我写明天开会的发言稿","现在帮我写报告","在电脑项目里改代码","帮我编写Python代码","明天开会吗","不要新增待办","取件已经完成了","小林明天去医院","如果我明天开会","把那个改到周五","明天开会，顺便写一份发言稿","提醒我明天取快递，同时帮我搜索路线","明天开会，帮我写发言稿","明天取快递；生成一份报告","明天开会，写一份稿子","我明天告诉妈妈取快递","帮我记住我在国科大上学","明天组会\n补充说明：不是新增待办"})check(AssistantRouter.local(s).kind==AssistantRouter.Kind.UNKNOWN,"semantic interpretation required: "+s);
        for(String kind:new String[]{"android","windows","chat","note","clarify"}){JSONObject r=result(kind).put("text","我在国科大上学").put("question","要在哪个项目修改？");check(AssistantRouter.parse(r.toString(),"帮我记住我在国科大上学").kind.name().equalsIgnoreCase(kind),"supported route "+kind);}
        for(double confidence:new double[]{.5,0,1.1})check(AssistantRouter.parse(result("android").put("confidence",confidence).toString(),"帮我写报告").kind==AssistantRouter.Kind.CLARIFY,"uncertainty never executes");
        check(AssistantRouter.parse(result("shell").toString(),"执行命令").kind==AssistantRouter.Kind.CLARIFY,"unsupported actions rejected");
        check(AssistantRouter.parse(result("task").put("tasks",new JSONArray().put("明天付款500元")).toString(),"下周复查").kind==AssistantRouter.Kind.CLARIFY,"no invented task spans");
        check(AssistantRouter.parse(result("note").put("text","我20岁").toString(),"我在国科大上学").kind==AssistantRouter.Kind.CLARIFY,"no invented personal info");
        JSONArray many=new JSONArray();for(int i=0;i<9;i++)many.put("取快递");check(AssistantRouter.parse(result("task").put("tasks",many).toString(),"取快递").kind==AssistantRouter.Kind.CLARIFY,"batch bounded");
        AssistantStore store=new AssistantStore(Files.createTempDirectory("router340").toFile());String id=store.add("task","明天取快递");JSONArray records=store.list();
        AssistantRouter.Decision d=AssistantRouter.parse(result("complete").put("target",id).toString(),"取件完成了",records);check(d.kind==AssistantRouter.Kind.COMPLETE,"known target validated");store.applyInstruction(d);check(store.list().getJSONObject(0).getBoolean("done"),"completion directly persists");
        check(AssistantRouter.parse(result("delete").put("target","invented").toString(),"删掉取件",records).kind==AssistantRouter.Kind.CLARIFY,"target IDs cannot be invented");
        d=AssistantRouter.parse(result("update").put("target",id).put("text","周五取快递").toString(),"改成周五取快递",store.list());store.update(id,"已经手动修改",false);try{store.applyInstruction(d);throw new AssertionError("stale edit accepted");}catch(java.io.IOException expected){}
        try{AssistantRouter.parse("not json","写报告");throw new AssertionError("invalid JSON accepted");}catch(JSONException expected){}
        System.out.println("PASS: conservative fast paths, work-vs-reminder, mixed/negative instructions, routes, confidence, source evidence, target binding and stale-edit protection.");
    }
}
