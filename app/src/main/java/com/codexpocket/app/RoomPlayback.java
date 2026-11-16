package com.codexpocket.app;

/** Gesture speed affects silent character video only, never the voice clock. */
final class RoomPlayback {
    static final int TRANSITION_MS=72;
    static float speed(String kind){return "idle".equals(kind)?1f:"gaze".equals(kind)||"sleepy".equals(kind)?1.1f:1.22f;}
    static int remaining(int duration,int position,float speed){return Math.max(0,Math.round((duration-position)/Math.max(1,speed)));}
}
