package com.codexpocket.app;

/** Separate feature destination from the computer/phone mode inside Codex. */
final class AppNavigation {
    static final int CODEX=0,ASSISTANT=1,LAB=2;
    static int section(int value){return Math.max(CODEX,Math.min(LAB,value));}
    static int mode(int value){return value==1?1:0;}
    static int migrateSection(int legacy){return legacy==2?LAB:CODEX;}
    static int migrateMode(int legacy){return legacy==1?1:0;}
}
