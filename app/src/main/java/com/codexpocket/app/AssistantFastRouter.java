package com.codexpocket.app;

import org.json.*;
import java.util.*;
import java.util.regex.*;

/** High precision shortcuts; uncertain and mixed commands still use semantic routing. */
final class AssistantFastRouter {
    private static final Pattern NOTE=Pattern.compile("^(?:请|麻烦)?(?:帮我|替我|给我)?(?:新增|添加|记下|记录|记)?(?:一条|一则)?随手记[：:\\s]*(.+)$",Pattern.DOTALL);
    private static final Pattern MEMORY=Pattern.compile("^(?:请|麻烦)?(?:帮我|替我)?(?:记住|记下|记一下|记录)[：:\\s]*(我(?:在|是|叫|今年|住|就读|的生日|喜欢|不喜欢|偏好).+)$",Pattern.DOTALL);
    private static final Pattern TASK=Pattern.compile("^(?:请|麻烦)?(?:帮我|替我|给我)?(?:新增|添加|增加|记)(?:一项|一条|一个|个)?(?:待办|代办)(?:事项|任务)?[：:\\s]*(.+)$",Pattern.DOTALL);
    private static boolean has(String s,String re){return Pattern.compile(re,Pattern.CASE_INSENSITIVE).matcher(s).find();}
    static boolean references(String input){return has(input,"删除|移除|取消|改成|改为|改到|标记|完成|划掉");}
    private static AssistantRouter.Decision note(String text){return new AssistantRouter.Decision(AssistantRouter.Kind.NOTE,Collections.emptyList(),"",AssistantRouter.clean(text),"","");}
    static AssistantRouter.Decision local(String input,JSONArray records){
        String s=AssistantRouter.clean(input);if(s.isEmpty()||s.length()>6000)return AssistantRouter.local(input);
        // Explicit note content is data, so a preference such as “我不喜欢咖啡” remains a note.
        Matcher m=NOTE.matcher(s);if(m.matches())return note(m.group(1));
        if(has(s,"顺便|同时|并且|另外|然后|以及|还要|还得|并(?:帮我|提醒我|新增|添加|记住)|补充说明|假如|假设|如果|比如|例如|不要|别再|不用|不需要|暂时不|无需|[；;\\n]|[，,].*(?:帮我|给我|替我|提醒|新增|添加|随手记|删除|修改|生成|搜索|明天|今晚|晚上|周[一二三四五六日天])"))return AssistantRouter.local(input).kind==AssistantRouter.Kind.TASK?AssistantRouter.local(input):AssistantRouter.of(AssistantRouter.Kind.UNKNOWN);
        m=MEMORY.matcher(s);if(m.matches()&&!has(m.group(1),"明天|后天|下周|计划|打算|需要|要去"))return note(m.group(1));
        m=TASK.matcher(s);if(m.matches()&&!has(m.group(1),"[?？]|吗$|呢$"))return new AssistantRouter.Decision(AssistantRouter.Kind.TASK,Collections.singletonList(AssistantRouter.clean(m.group(1))));
        if(references(s)){
            String target="",replacement="";AssistantRouter.Kind kind=null;
            m=Pattern.compile("^(?:请|帮我)?(?:把|将)?(.+?)(?:标记为|标记|设为|划为)(?:已)?完成$",Pattern.DOTALL).matcher(s);if(m.matches()){target=m.group(1);kind=AssistantRouter.Kind.COMPLETE;}
            if(kind==null){m=Pattern.compile("^(?:请|帮我)?(?:删除|移除|取消)(?:待办|代办|随手记|记录)?[：:\\s]*(.+)$",Pattern.DOTALL).matcher(s);if(m.matches()){target=m.group(1);kind=AssistantRouter.Kind.DELETE;}}
            if(kind==null){m=Pattern.compile("^(?:请|帮我)?(?:把|将)(.+?)(?:改成|改为)(.+)$",Pattern.DOTALL).matcher(s);if(m.matches()){target=m.group(1);replacement=AssistantRouter.clean(m.group(2));kind=AssistantRouter.Kind.UPDATE;}}
            if(kind!=null){target=target.replaceAll("^[「『“\\\"]|[」』”\\\"]$","").trim();JSONObject found=null;for(int i=0;i<records.length();i++){JSONObject r=records.optJSONObject(i);if(r!=null&&target.equals(r.optString("text"))&&(kind!=AssistantRouter.Kind.COMPLETE||"task".equals(r.optString("kind")))){if(found!=null)return AssistantRouter.clarify("有多条同名记录，请在列表中选择要处理的一条。");found=r;}}
                if(found!=null){if(kind==AssistantRouter.Kind.UPDATE&&"task".equals(found.optString("kind"))&&AssistantRouter.local(replacement).kind!=AssistantRouter.Kind.TASK)return AssistantRouter.of(AssistantRouter.Kind.UNKNOWN);return new AssistantRouter.Decision(kind,Collections.emptyList(),found.optString("id"),replacement,"",found.optString("text"));}
            }
        }
        AssistantRouter.Decision previous=AssistantRouter.local(input);if(previous.kind!=AssistantRouter.Kind.UNKNOWN)return previous;
        if(has(s,"[“”「」『』\\\"]|不想|不去|不打算|已经|做完|办完|取完|完成了"))return previous;
        String command=s.replaceFirst("^(?:请|麻烦)[，,\\s]*","").replaceFirst("^(?:帮我|替我|给我)[，,\\s]*","").replaceFirst("^(?:现在|立即|直接)[，,\\s]*","").replaceFirst("^(?:帮我|替我|给我)[，,\\s]*","");
        boolean request=!command.equals(s);
        if(has(command,"^(?:查看|列出|看看|整理|总结|安排|回顾).*(?:待办|代办|随手记|未接来电|短信|邮件|今天|今日)$")||has(s,"^(?:我)?(?:今天|明天|最近)?有(?:什么|哪些|多少).*(?:待办|代办|未接来电|重要消息|重要事项|随手记)[?？]?$"))return AssistantRouter.of(AssistantRouter.Kind.CHAT);
        if(request&&has(command,"^(?:回复邮件|回邮件|发邮件|发送邮件|发短信|发送短信|打电话|拨号|回电话)"))return AssistantRouter.of(AssistantRouter.Kind.CHAT);
        boolean computer=has(command,"^(?:在|到|用)(?:我的)?(?:电脑|Windows|本地项目|项目目录|终端|命令行|[A-Z]:[\\\\/])")||has(command,"^(?:修改|修复|检查|打开|运行|删除|移动|安装|执行|控制|编辑|整理).{0,16}(?:电脑上|电脑里|Windows中|Windows上|本地项目|项目目录)");
        if(computer&&has(command,"修改|修复|检查|打开|运行|删除|移动|安装|执行|控制|编辑|整理|写|生成"))return AssistantRouter.of(AssistantRouter.Kind.WINDOWS);
        if(request&&has(command,"^(?:写|生成|编写|制作|搜索|查找|搜一下|翻译|分析|总结|解答|计算|设计|研究|解释|帮我写).+")&&!has(command,"^.*(?:待办|代办|随手记).*(?:新增|删除|修改|完成)"))return AssistantRouter.of(AssistantRouter.Kind.ANDROID);
        return previous;
    }
    /** Include only relevant records for a referential instruction, not the whole organizer. */
    static JSONArray context(String input,JSONArray all)throws Exception{
        JSONArray out=new JSONArray();if(!references(input))return out;List<JSONObject> candidates=new ArrayList<>();for(int i=0;i<all.length();i++){JSONObject r=all.getJSONObject(i);if(!r.optBoolean("done")||input.contains(r.optString("text")))candidates.add(r);}
        candidates.sort(Comparator.comparingInt((JSONObject r)->score(input,r.optString("text"))).reversed());int chars=0;for(JSONObject r:candidates){String text=r.optString("text");if(out.length()>=16||chars+text.length()>3500)continue;chars+=text.length();out.put(new JSONObject().put("id",r.getString("id")).put("kind",r.optString("kind")).put("text",text).put("timeText",r.optString("timeText")).put("scheduledDate",r.optString("scheduledDate")).put("done",r.optBoolean("done")));}return out;
    }
    private static int score(String input,String text){if(input.contains(text))return 100000;int n=0;for(int i=0;i+1<input.length();i++)if(text.contains(input.substring(i,i+2)))n++;return n;}
    /** Stream completion marker, respecting braces inside escaped JSON strings. */
    static String completedJson(String text){int start=text.indexOf('{');if(start<0||text.length()>12000)return null;int depth=0;boolean quoted=false,escape=false;for(int i=start;i<text.length();i++){char c=text.charAt(i);if(quoted){if(escape)escape=false;else if(c=='\\')escape=true;else if(c=='"')quoted=false;}else if(c=='"')quoted=true;else if(c=='{')depth++;else if(c=='}'&&--depth==0)return text.substring(start,i+1);}return null;}
}
