package com.codexpocket.app;
public class SpeechTextTest {
    static void check(boolean ok,String msg){if(!ok)throw new AssertionError(msg);}
    public static void main(String[] args){
        check(SpeechText.clean("我 明天 要 做 线性 代数 作业").equals("我明天要做线性代数作业"),"Mandarin token spacing");
        check(SpeechText.clean("明天 3 点 买 2 盒药").equals("明天3点买2盒药"),"numbers preserved");
        check(SpeechText.clean("write Java code").equals("write Java code"),"English word boundaries preserved");
        check(!SpeechText.uncertain(-1)&&!SpeechText.uncertain(Float.NaN)&&SpeechText.uncertain(.4f),"provider confidence only when valid");
        SpeechText good=SpeechText.vosk("{\"text\":\"做 线性 代数 作业\",\"result\":[{\"conf\":0.99},{\"conf\":0.9}]}");check(!good.review&&good.text.equals("做线性代数作业"),"confident result remains unchanged");
        SpeechText poor=SpeechText.vosk("{\"text\":\"做 现行 代数 作业\",\"result\":[{\"conf\":0.99},{\"conf\":0.3}]}");check(poor.review&&poor.text.equals("做现行代数作业"),"uncertain word reviewed; never silently substituted");
        check(!SpeechText.vosk("{\"text\":\"开会\"}").review,"unknown confidence not fabricated");check(SpeechText.vosk("bad").text.isEmpty(),"malformed result does not invent text");
        check(SpeechText.HINTS.contains("线性代数")&&SpeechText.HINTS.contains("取快递"),"open dictation hints include user examples");
        System.out.println("PASS: Chinese spacing, numbers/English, confidence handling, uncertain-word review, no guessed correction, study/task hints.");
    }
}
