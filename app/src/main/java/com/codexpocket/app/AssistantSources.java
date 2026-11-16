package com.codexpocket.app;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.provider.*;
import org.json.*;
import java.util.*;

/** Bounded, incremental read of granted phone providers. Does not request roles or permissions. */
final class AssistantSources {
    static boolean allowed(Context c,String permission){return c.checkSelfPermission(permission)==PackageManager.PERMISSION_GRANTED;}
    private static String string(Cursor c,String name){String value=c.getString(c.getColumnIndexOrThrow(name));return value==null?"":value;}
    static List<JSONObject> sms(Context c)throws Exception{
        List<JSONObject> rows=new ArrayList<>();if(!allowed(c,Manifest.permission.READ_SMS))return rows;long since=System.currentTimeMillis()-7*AssistantTriage.DAY;
        try(Cursor cursor=c.getContentResolver().query(Telephony.Sms.CONTENT_URI,new String[]{"_id","address","body","date","type"},"date >= ? AND type = ?",new String[]{String.valueOf(since),"1"},"date DESC")){
            while(cursor!=null&&cursor.moveToNext()&&rows.size()<100){long at=cursor.getLong(cursor.getColumnIndexOrThrow("date"));String from=string(cursor,"address");rows.add(new JSONObject().put("id","sms:"+string(cursor,"_id")).put("kind","收到短信").put("source",from).put("phone",from).put("title",from).put("body",AssistantTriage.clip(string(cursor,"body"),4000)).put("at",at));}
        }return rows;
    }
    static List<JSONObject> calls(Context c)throws Exception{return CallHistory.read(c,System.currentTimeMillis()-7*AssistantTriage.DAY,100);}
}
