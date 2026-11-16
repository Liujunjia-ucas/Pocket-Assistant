package com.codexpocket.app;

import org.json.*;
import java.util.*;

/** Display projection only. Original events, pickup codes and task identities remain independent. */
final class AssistantInbox {
    static boolean sms(JSONObject e){return e.optString("kind").equals("收到短信")||e.optString("kind").equals("短信通知");}
    static String sender(JSONObject e){
        String phone=e.optString("senderPhone",e.optString("phone"));if(phone.isEmpty()&&e.optString("kind").equals("收到短信"))phone=e.optString("source");phone=phone.replaceAll("[\\s()（）-]","");if(phone.matches("\\+?861[3-9][0-9]{9}"))phone=phone.replaceFirst("^\\+?86","");if(!phone.isEmpty())return "phone:"+phone;
        String name=e.optString("sender");if(name.matches("(?:短信|信息|消息|新消息|通知|[0-9]+条新?(?:短信|信息|消息))"))return "";String number=name.replaceAll("[\\s()（）-]","");if(number.matches("\\+?[0-9]{3,20}")){if(number.matches("\\+?861[3-9][0-9]{9}"))number=number.replaceFirst("^\\+?86","");return "phone:"+number;}if(!name.isEmpty())return "name:"+e.optString("senderApp")+":"+name;return "";
    }
    static List<JSONObject> group(List<JSONObject> events)throws Exception{
        LinkedHashMap<String,List<JSONObject>> groups=new LinkedHashMap<>();List<JSONObject> out=new ArrayList<>();for(JSONObject e:events){String sender=sms(e)?sender(e):"";if(sender.isEmpty()){out.add(e);continue;}groups.computeIfAbsent(sender,k->new ArrayList<>()).add(e);}
        for(Map.Entry<String,List<JSONObject>> entry:groups.entrySet()){List<JSONObject> members=entry.getValue();members.sort(Comparator.comparingLong((JSONObject e)->e.optLong("at")).reversed());JSONObject latest=members.get(0);if(members.size()==1){out.add(latest);continue;}JSONObject row=new JSONObject(latest.toString());JSONArray keys=new JSONArray(),messages=new JSONArray();LinkedHashSet<String> facts=new LinkedHashSet<>();StringBuilder body=new StringBuilder();
            for(JSONObject e:members){keys.put(e.getString("key"));messages.put(new JSONObject(e.toString()));String fact=e.optString("summary");if(!fact.isEmpty())facts.add(fact);if(body.length()<18000){if(body.length()>0)body.append("\n\n");body.append(new java.text.SimpleDateFormat("M月d日 HH:mm",Locale.CHINA).format(new Date(e.optLong("at")))).append("\n").append(e.optString("body"));}}
            String name=latest.optString("sender",latest.optString("source","同一联系人"));StringBuilder summary=new StringBuilder();int n=0;for(String fact:facts){if(n++==3)break;if(summary.length()>0)summary.append("；此前：");else summary.append("最新：");summary.append(AssistantTriage.clip(fact,120));}if(facts.size()>3)summary.append("；还有 ").append(facts.size()-3).append(" 项，点击查看");
            row.put("key","sms-group:"+AssistantTriage.hash(entry.getKey())).put("notificationKey",latest.getString("key")).put("category","sms").put("title",name+"的短信 · "+members.size()+" 条重要信息").put("summary",summary.toString()).put("body",body.toString()).put("groupKeys",keys).put("messages",messages).put("messageCount",members.size());out.add(row);
        }out.sort(Comparator.comparingLong((JSONObject e)->e.optLong("at")).reversed());return out;
    }
}
