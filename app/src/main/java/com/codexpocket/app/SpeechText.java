package com.codexpocket.app;

import org.json.*;

/** Formatting and uncertainty only: never guess a different name, course, date or number. */
final class SpeechText {
    final String text;final boolean review;
    SpeechText(String text,boolean review){this.text=clean(text);this.review=review;}
    static String clean(String text){return text==null?"":text.trim().replaceAll("(?<=[\\p{IsHan}0-9])\\s+(?=[\\p{IsHan}0-9])","");}
    static boolean uncertain(float confidence){return Float.isFinite(confidence)&&confidence>=0&&confidence<.72f;}
    static SpeechText vosk(String json){
        try{
            JSONObject r=new JSONObject(json);JSONArray words=r.optJSONArray("result");double total=0;int count=0,low=0;
            for(int i=0;words!=null&&i<words.length();i++){JSONObject word=words.optJSONObject(i);if(word==null)continue;double c=word.optDouble("conf",-1);if(Double.isFinite(c)&&c>=0&&c<=1){total+=c;count++;if(c<.45)low++;}}
            return new SpeechText(r.optString("text"),count>0&&(total/count<.78||low>0));
        }catch(JSONException ignored){return new SpeechText("",true);}
    }
    static final java.util.ArrayList<String> HINTS=new java.util.ArrayList<>(java.util.Arrays.asList("线性代数","高等数学","概率论","数理统计","大学物理","英语","作业","复习","待办","随手记","取快递","开组会"));
}
