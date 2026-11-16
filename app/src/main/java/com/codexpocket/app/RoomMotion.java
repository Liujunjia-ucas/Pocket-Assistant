package com.codexpocket.app;

import java.util.Arrays;
import java.util.function.LongSupplier;

/** Context gestures vary at boundaries; actual playback controls speaking lifetime. */
final class RoomMotion {
    static final String[] CLIPS={"idle","gaze","sleepy","listening","thinking","speaking","happy","greeting","explain","reassure","ponder","amused","shy","agree","backhands"};
    private final LongSupplier clock;
    private final boolean dragon;
    private String phase="idle",tone="neutral",override="",lastStarted="",reaction="";
    private long lastVoice=-100000,idleSince,lastSleep=-100000,reactionUntil;
    private boolean comfortUsed;
    private boolean active=true;
    private int step;
    RoomMotion(LongSupplier clock){this(clock,false);}
    RoomMotion(LongSupplier clock,boolean dragon){this.clock=clock;this.dragon=dragon;idleSince=clock.getAsLong();}
    void active(boolean value){active=value;if(!value)cancel();else idleSince=clock.getAsLong();}
    void state(String value){if(phase.equals(value))return;phase=value;step=0;if("idle".equals(value))idleSince=clock.getAsLong();else override="";}
    void audio(float level){if(active&&"speaking".equals(phase)&&level>.035f)lastVoice=clock.getAsLong();}
    void expression(String value){tone="sad".equals(value)?"comfort":"happy".equals(value)?"happy":"neutral";}
    void cancel(){lastVoice=-100000;override=reaction="";tone="neutral";phase="idle";step=0;comfortUsed=false;idleSince=clock.getAsLong();}
    private String cue(String text){
        text=text.replaceAll("不难过|不伤心|不害怕|不焦虑|不委屈|不累|没有难过|没有不开心","");
        if(text.matches("(?s).*(不开心|不高兴|难过|难受|委屈|焦虑|害怕|没成功|失败了|失落|孤独|压力好大|好累|有点累).*$"))return "comfort";
        if(text.matches("(?s).*(哈哈|好好笑|逗笑|笑死).*$"))return "amused";
        if(text.matches("(?s).*(可爱|漂亮|喜欢你|好温柔|好看|害羞|想你|抱抱|抱一下).*$"))return "shy";
        if(text.matches("(?s).*(太好了|好耶|成功了|做到了|真棒|太棒|开心).*$"))return "happy";
        if(text.matches("(?s).*(你好|早上好|晚上好|再见|拜拜).*$"))return "greeting";
        if(text.matches("(?s).*(谢谢|明白|理解|没错|确实).*$"))return "agree";
        if(text.matches("(?s).*(请解释|解释一下|讲解|为什么|怎么做|步骤|推导|教我|帮我分析).*$"))return "explain";
        return "neutral";
    }
    void event(String name,String text){
        if(!active)return;
        if("interrupt".equals(name)||"error".equals(name)){cancel();return;}
        if("welcome".equals(name))return;
        if("user".equals(name)){idleSince=clock.getAsLong();tone=cue(text);reaction="";comfortUsed=false;step=0;return;}
        // The user's topic sets the context. Generic assistant phrases such as
        // "陪你" and "因为" must not turn every answer into comfort/explanation.
        if("complete".equals(name)){reaction="shy".equals(tone)?"shy":"amused".equals(tone)?"amused":"happy".equals(tone)?"happy":"agree".equals(tone)?"agree":"neutral".equals(tone)?(dragon?"gaze":"backhands"):dragon&&"comfort".equals(tone)?"reassure":"";reactionUntil=clock.getAsLong()+25000;}
    }
    void gesture(String kind){if(!active)return;if("wave".equals(kind))kind="greeting";if(Arrays.asList(CLIPS).contains(kind))override=kind;}
    private String[] pool(){
        long time=clock.getAsLong();
        if(!override.isEmpty())return new String[]{override};
        if("speaking".equals(phase)){
            if("comfort".equals(tone))return comfortUsed?new String[]{"listening","speaking","backhands"}:new String[]{"reassure","listening","speaking"};
            if("shy".equals(tone))return new String[]{"backhands","shy","gaze","speaking"};
            if("explain".equals(tone))return new String[]{"explain","speaking","agree","gaze"};
            if("amused".equals(tone))return new String[]{"amused","happy","speaking","backhands"};
            if("happy".equals(tone))return new String[]{"happy","speaking","backhands","agree"};
            if("greeting".equals(tone))return step<2?new String[]{"greeting","speaking"}:new String[]{"speaking","backhands","gaze"};
            if("agree".equals(tone))return new String[]{"agree","speaking","backhands","gaze"};
            return new String[]{"idle","speaking","gaze","backhands"};
        }
        if("listening".equals(phase))return "shy".equals(tone)?new String[]{"listening","shy","listening","backhands"}:new String[]{"listening","gaze","agree","backhands"};
        if("thinking".equals(phase))return "shy".equals(tone)?new String[]{"thinking","shy","backhands","ponder"}:new String[]{"thinking","ponder","backhands","gaze"};
        if("idle".equals(phase)&&!reaction.isEmpty()&&time<reactionUntil)return new String[]{reaction};
        if("idle".equals(phase)&&time-idleSince<30000&&"shy".equals(tone))return new String[]{"idle","backhands","gaze","shy"};
        if("idle".equals(phase)&&time-idleSince>=90000&&time-lastSleep>=60000)return new String[]{"sleepy"};
        return new String[]{"idle","idle","gaze","idle"};
    }
    String choose(String current,boolean boundary){
        if(!current.isEmpty()&&!boundary)return current;
        String[] choices=pool();int index=step%choices.length;
        // One stable decision throughout preloading, advanced only by a visible start.
        // Idle is deliberately quiet/repetitive; spoken gestures must not repeat.
        if("speaking".equals(phase)&&override.isEmpty()&&step>0){
            for(int i=0;i<choices.length;i++){String next=choices[(index+i)%choices.length];if(!next.equals(current)&&!next.equals(lastStarted))return next;}
        }
        return choices[index];
    }
    void started(String kind){lastStarted=kind;step++;if("reassure".equals(kind))comfortUsed=true;if(kind.equals(reaction))reaction="";if("sleepy".equals(kind))lastSleep=clock.getAsLong();}
    void completed(String kind){if(kind.equals(override))override="";}
    boolean isActive(){return active;}
    String phase(){return phase;}
    void restartSpeech(){phase="speaking";step=0;override="";}
}
