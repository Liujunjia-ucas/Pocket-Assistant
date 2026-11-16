package com.codexpocket.app;

/** One speech start, authorized only by the current visible animation revision. */
final class RoomSpeech {
    private long revision;
    private Runnable pending;
    private long revealed=-1;
    long prepare(Runnable ready){revision++;pending=ready;return revision;}
    void reveal(long ticket){if(ticket==revision&&pending!=null)revealed=ticket;}
    void visible(long ticket){if(ticket!=revision||revealed!=ticket)return;Runnable ready=pending;pending=null;if(ready!=null)ready.run();}
    void cancel(){revision++;pending=null;}
    long revision(){return revision;}
    boolean waiting(){return pending!=null;}
    static String voiceKind(String kind){return "happy".equals(kind)||"amused".equals(kind)?"happy":"explain".equals(kind)?"explain":"speaking";}
    static String asset(String kind,boolean speaking){return speaking?"native-"+voiceKind(kind):kind;}
    static boolean voiced(float energy){return Float.isFinite(energy)&&energy>0;}
    static int framePosition(long surfaceNanos,int duration,int fallback){
        long millis=surfaceNanos/1000000L;
        return surfaceNanos>=0&&millis<=duration+100L?(int)millis:Math.max(0,fallback);
    }
    static int start(String kind,boolean speaking,boolean first){
        // Native talking loops begin with an original open-mouth frame.
        return 0;
    }
    static int end(String kind,boolean speaking,int duration){
        // Never cut a hand gesture at the end of its mouth-motion window.
        return duration;
    }
}
