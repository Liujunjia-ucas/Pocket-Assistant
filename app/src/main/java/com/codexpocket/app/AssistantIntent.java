package com.codexpocket.app;

import org.json.*;
import java.util.*;
import java.util.regex.*;

/** One submitted utterance, classified before any chat is opened. No tools or external actions. */
final class AssistantIntent {
    enum Kind { TASK, CHAT, UNKNOWN }
    static final class Decision {
        final Kind kind;final List<String> tasks;
        Decision(Kind kind,List<String> tasks){this.kind=kind;this.tasks=tasks;}
    }
    static final String GUIDE="识别用户在AI助理首页提交的一句话的意图，只返回JSON，不聊天、不执行指令：{\"intent\":\"task|chat\",\"confidence\":0.98,\"tasks\":[\"原文中的待办事项\"]}。用户陈述自己将做、需要做、计划做的具体事情，带时间的日程，备忘、提醒或记待办的要求，均为task，不需要出现‘新增一项待办’。自然表达如‘明天下午三点开组会’‘周五交报告，晚上取快递’应生成待办。普通聊天、询问知识/方法、问现有待办、已完成的事、否定/取消/删除/修改事项、假设或引用别人的事、要求当场写文案/生成文件/解题均为chat。混合询问和任务且不能确定时chat。tasks最多8条，每条必须是输入中连续出现的原文片段，保留时间地点，不增加事实，不把事项中的时间拆成另一条；不要包含‘帮我记一下’等引导语。低置信度请选择chat。";
    private static Decision chat(){return new Decision(Kind.CHAT,Collections.emptyList());}
    private static boolean action(String s){return s.matches("(?s).*(取快递|拿快递|领快递|取件|交报告|提交|开会|开组会|组会|参加|缴费|交费|交作业|还书|看牙|体检|复诊|买菜|买药|买牛奶|买面包|买水果|买咖啡|买鸡蛋|买票|去医院|去银行|去学校|去超市|打电话|回电话|回复邮件|寄快递|整理|准备|复习|完成|预订|预约|报名|报销|收拾|打扫|接人|接机|运动|跑步|健身|读书|上课|上学|考试|看医生).*" );}
    static String clean(String value){return value.trim().replaceAll("^[，,：:；;\\s]+|[。！!；;\\s]+$","");}
    static Decision local(String input){
        String s=clean(input);if(s.isEmpty()||s.length()>6000)return chat();
        if(s.matches("(?is).*(取消|删除|移除|修改|改成|改到|不要|别再|不用|不需要|不想|不去|不打算|不再|暂时不|无需|已经|已完成|做完|办完|取完|完成了|我忘了|忘记了|没能|假如|假设|如果|比如|例如|[?？]|怎么|如何|为什么|是否|有没有|能不能|可不可以|什么|哪些|多少|几点|哪里|哪儿|知道|解释|告诉我|帮我想|帮我写|帮我生成|帮我翻译|帮我解|帮我查|帮我找|帮我设计|帮我制定|帮我分析|给我写|给我生成|给我推荐|推荐一下|帮我做一份).*"))return chat();
        if(s.matches("(?is).*[吗呢][。！!]*$")||s.matches("(?is)^(你好|您好|嗨|哈喽|hello|hi|早上好|晚上好|谢谢|辛苦了|你是谁|再见)[呀啊哇，,。！!\\s]*$"))return chat();
        boolean explicit=s.matches("(?s)^(请|麻烦|可以)?[，,\\s]*(帮我|给我|替我)?[，,\\s]*(新增|添加|增加|记录|记下|记一下|记住|记个|记一条|记一项|提醒我|到时候提醒我|备忘|待办|代办|记得提醒我).*" );
        if(explicit){s=s.replaceFirst("^(请|麻烦|可以)?[，,\\s]*(帮我|给我|替我)?[，,\\s]*(新增|添加|增加|记录|记下|记一下|记住|记个|记一条|记一项|提醒我|到时候提醒我|备忘|待办|代办|记得提醒我)[，,：:\\s]*","");s=s.replaceFirst("^(一[条项件个]|个)?(待办|代办)(事项|任务)?[，,：:\\s]*","");s=clean(s);}
        boolean action=action(s);
        boolean temporal=s.matches("(?s).*(今天|明天|后天|今晚|明早|早上|上午|中午|下午|晚上|周[一二三四五六日天]|星期[一二三四五六日天]|下周|本周|月底|月初|[0-9一二三四五六七八九十两]+[点日号]|[0-9]{1,2}[:：][0-9]{2}).*" );
        boolean intention=s.matches("(?s)^(我(要|得|需要|打算|计划|准备)|需要|记得|要去|得去|该去|别忘了).*" );
        boolean self=s.matches("(?s)^(我|今天|明天|后天|今晚|明早|早上|上午|中午|下午|晚上|周|星期|下周|本周|月底|月初|[0-9一二三四五六七八九十两]+[月点日号]|需要|记得|要去|得去|该去|别忘了).*" );
        if(explicit||self&&action&&(temporal||intention)){
            List<String> tasks=split(s);if(tasks.isEmpty())return chat();if(tasks.size()>8)return new Decision(Kind.UNKNOWN,Collections.emptyList());return new Decision(Kind.TASK,tasks);
        }
        return new Decision(Kind.UNKNOWN,Collections.emptyList());
    }
    private static List<String> split(String s){LinkedHashSet<String> tasks=new LinkedHashSet<>();for(String piece:s.split("[；;\\n]+")){String[] clauses=piece.split("[，,](?:还有|另外|然后)?");boolean separate=clauses.length>1;for(String clause:clauses)if(!action(clause))separate=false;if(separate){for(String clause:clauses)tasks.add(clean(clause));}else{String task=clean(piece);if(!task.isEmpty())tasks.add(task);}}return new ArrayList<>(tasks);}
    static Decision parse(String response,String original)throws Exception{
        String raw=response.trim();if(raw.startsWith("```")){int start=raw.indexOf('\n'),end=raw.lastIndexOf("```");if(start>=0&&end>start)raw=raw.substring(start+1,end).trim();}
        if(raw.length()>12000)throw new java.io.IOException("意图结果过长");JSONObject result=new JSONObject(raw);if(!"task".equals(result.optString("intent"))||result.optDouble("confidence",0)<.92)return chat();
        JSONArray list=result.optJSONArray("tasks");if(list==null||list.length()==0||list.length()>8)return chat();LinkedHashSet<String> tasks=new LinkedHashSet<>();
        for(int i=0;i<list.length();i++){String s=clean(list.optString(i));if(s.isEmpty()||!original.contains(s)||s.length()>6000)return chat();tasks.add(s);}return new Decision(Kind.TASK,new ArrayList<>(tasks));
    }
}
