package com.codexpocket.app;

import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.regex.*;
import org.json.JSONObject;

/** Conservative, source-preserving cleanup after task intent and provenance are validated. */
final class TaskDescription {
    private static final String N="[0-9零〇一二三四五六七八九十两]+";
    private static final String DAY="(?:大后天|后天|明天|今天|今晚|明早|明晚|今早|(?:下下|下|本|这)周[一二三四五六日天]?|周[一二三四五六日天]|星期[一二三四五六日天]|(?:[0-9]{4}年)?[0-9]{1,2}月[0-9]{1,2}[日号]|[0-9]{1,2}[日号]|每天|每周[一二三四五六日天]|每月[0-9]{1,2}[日号])";
    private static final String PERIOD="(?:早上|上午|中午|下午|晚上|凌晨|傍晚)";
    private static final String CLOCK="(?:"+N+"(?:点|时)(?:半|一刻|三刻|"+N+"分?)?|[0-9]{1,2}[:：][0-9]{2})";
    private static final Pattern TIME=Pattern.compile("^(?:(?:"+DAY+")(?:的)?(?:"+PERIOD+")?(?:"+CLOCK+")?|"+PERIOD+"(?:"+CLOCK+")?|"+CLOCK+")(?:之前|以前|之后|以后|前|后)?");
    private static final Pattern ACTION=Pattern.compile("^(?:做|写|完成|去|取|拿|领|交|提交|开|参加|缴|还|看|体检|复诊|买|打|回|寄|整理|准备|复习|预订|预约|报名|报销|收拾|接|运动|跑步|健身|读|上|考试|送|带|联系|打印|学习|背|练习|吃|喝|检查|处理|办理).+",Pattern.DOTALL);
    final String source,title,time,date;
    private TaskDescription(String source,String title,String time,String date){this.source=source;this.title=title;this.time=time;this.date=date;}
    static TaskDescription from(String source,long at){
        String original=source.trim(),s=AssistantRouter.clean(original);
        // Do not simplify hypothetical, quoted, negative or multi-clause constraints.
        if(s.matches("(?s).*(?:如果|假如|除非|不要|不能|不需要|不是|[“”\"「」；;\\n]).*"))return new TaskDescription(original,original,"","");
        s=s.replaceFirst("^(?:请|麻烦)?(?:帮我|给我|替我)?(?:记得提醒我|到时候提醒我|提醒我|记一下|记下)[：:,，\\s]*","");
        String timing="";
        for(int i=0;i<3;i++){
            s=s.replaceFirst("^我(?=明|今|后天|大后天|下|本周|这周|周|星期|每天|每周|每月|[0-9]|要|得|需要|打算|计划|做|写|去|取|买|交|准备|复习|完成)","");
            s=s.replaceFirst("^(?:打算|计划|需要|要|得)(?=.+)","");
            Matcher m=TIME.matcher(s);
            if(timing.isEmpty()&&m.find()){
                String rest=s.substring(m.end());
                // “明天的作业” and “明天开会的报告” describe an object, not an event time.
                if(rest.startsWith("的"))break;
                timing=m.group();s=rest.replaceFirst("^[,，：:\\s]+","");
            }
        }
        if(!ACTION.matcher(s).matches()||s.matches(".*(?:很开心|真开心|好累|结束了|完成了)$"))return new TaskDescription(original,original,"","");
        return new TaskDescription(original,s,timing,resolve(timing,at));
    }
    private static String resolve(String timing,long at){
        LocalDate today=Instant.ofEpochMilli(at).atZone(ZoneId.systemDefault()).toLocalDate();
        try{
            if(timing.startsWith("每"))return "";
            if(timing.startsWith("大后天"))return today.plusDays(3).toString();
            if(timing.startsWith("后天"))return today.plusDays(2).toString();
            if(timing.startsWith("明"))return today.plusDays(1).toString();
            if(timing.matches("^(?:今天|今晚|今早).*"))return today.toString();
            Matcher week=Pattern.compile("^(下下|下|本|这)?(?:周|星期)([一二三四五六日天])").matcher(timing);
            if(week.find()){
                int day="一二三四五六日天".indexOf(week.group(2))+1;if(day==8)day=7;
                LocalDate monday=today.minusDays(today.getDayOfWeek().getValue()-1);
                String modifier=week.group(1);LocalDate date=monday.plusDays(day-1);
                if("下下".equals(modifier))date=date.plusWeeks(2);else if("下".equals(modifier))date=date.plusWeeks(1);else if(modifier==null&&date.isBefore(today))date=date.plusWeeks(1);
                return date.toString();
            }
            Matcher explicit=Pattern.compile("^(?:([0-9]{4})年)?([0-9]{1,2})月([0-9]{1,2})[日号]").matcher(timing);
            if(explicit.find()){
                LocalDate date=LocalDate.of(explicit.group(1)==null?today.getYear():Integer.parseInt(explicit.group(1)),Integer.parseInt(explicit.group(2)),Integer.parseInt(explicit.group(3)));
                if(explicit.group(1)==null&&date.isBefore(today))date=date.plusYears(1);return date.toString();
            }
        }catch(DateTimeException|NumberFormatException ignored){}
        return ""; // Ambiguous dates remain exact source text; never invent a deadline or alarm.
    }
    static String schedule(JSONObject item){
        String time=item.optString("timeText"),date=item.optString("scheduledDate");if(time.isEmpty())return "";
        if(!date.isEmpty())try{
            String rest=time.replaceFirst("^(?:"+DAY+")(?:的)?","");
            if(time.startsWith("今晚")||time.startsWith("明晚"))rest="晚上"+rest;
            if(time.startsWith("今早")||time.startsWith("明早"))rest="早上"+rest;
            return LocalDate.parse(date).format(DateTimeFormatter.ofPattern("M月d日"))+ (rest.isEmpty()?"":" · "+rest);
        }catch(DateTimeException ignored){}
        return time;
    }
    static String display(JSONObject item){String when=schedule(item);return item.optString("text")+(when.isEmpty()?"":" · "+when);}
    static String key(JSONObject item){return item.optString("text").trim()+"\u0001"+item.optString("scheduledDate")+"\u0001"+item.optString("timeText");}
    void put(JSONObject item)throws Exception{item.put("text",title).put("sourceText",source).put("timeText",time).put("scheduledDate",date);}
}
