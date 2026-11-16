package com.codexpocket.app;

import android.Manifest;
import android.app.AppOpsManager;
import android.content.Context;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.database.sqlite.SQLiteException;
import android.os.Process;
import android.provider.CallLog;
import java.io.IOException;
import java.util.*;
import org.json.*;

/** Shared provider reader for foreground and background, including vendor column fallback. */
final class CallHistory {
    static final class AccessException extends IOException {final String reason;AccessException(String reason,String message){super(message);this.reason=reason;}}
    private static String value(Cursor c,String column){int i=c.getColumnIndex(column);if(i<0)return "";String s=c.getString(i);return s==null?"":s;}
    private static void access(Context c)throws AccessException{
        if(c.checkSelfPermission(Manifest.permission.READ_CALL_LOG)!=PackageManager.PERMISSION_GRANTED)throw new AccessException("permission","尚未获得“读取通话记录”权限。短信权限不能代替这项权限，请在系统应用权限中单独允许。");
        try{AppOpsManager ops=(AppOpsManager)c.getSystemService(Context.APP_OPS_SERVICE);if(ops!=null){int mode=ops.checkOpNoThrow(AppOpsManager.OPSTR_READ_CALL_LOG,Process.myUid(),c.getPackageName());if(mode==AppOpsManager.MODE_IGNORED||mode==AppOpsManager.MODE_ERRORED)throw new AccessException("appops","应用权限显示已允许，但系统仍拦截读取通话记录。请在 HyperOS 安全中心的权限管理中检查 Pocket Assistant 的通话记录权限。");}}catch(SecurityException e){throw new AccessException("appops","系统拒绝检查通话记录访问状态，请在系统权限管理中重新允许通话记录访问。");}
    }
    static List<JSONObject> read(Context c,long since,int limit)throws Exception{
        access(c);String[] full={"_id","number","cached_name","date","duration","type"},core={"_id","number","date","duration","type"};String selection=since>0?"date >= ?":null;String[] args=since>0?new String[]{String.valueOf(since)}:null;Cursor cursor;
        try{try{cursor=c.getContentResolver().query(CallLog.Calls.CONTENT_URI,full,selection,args,"date DESC");}catch(IllegalArgumentException|SQLiteException e){cursor=c.getContentResolver().query(CallLog.Calls.CONTENT_URI,core,selection,args,"date DESC");}}
        catch(SecurityException e){throw new AccessException("provider","系统已拦截通话记录数据库访问。请重新检查通话记录权限和安全中心限制；也可接入未接来电通知。");}
        catch(IllegalArgumentException|SQLiteException e){throw new AccessException("schema","系统通话记录的兼容字段查询仍失败。请在系统电话应用确认记录可查看，再重新读取。");}
        catch(RuntimeException e){throw new AccessException("service","系统通话记录服务暂时无法响应，请打开系统电话应用后重试读取。");}
        if(cursor==null)throw new AccessException("provider","系统通话记录服务没有返回可读取的数据，不能据此判断没有通话记录。请检查安全中心授权后重试。");
        List<JSONObject> rows=new ArrayList<>();try(Cursor data=cursor){while(data.moveToNext()&&rows.size()<Math.min(100,Math.max(1,limit))){int type=data.getInt(data.getColumnIndexOrThrow("type"));long at=data.getLong(data.getColumnIndexOrThrow("date")),duration=data.getLong(data.getColumnIndexOrThrow("duration"));if(since>0&&at<since)continue;String number=value(data,"number"),name=value(data,"cached_name");String direction=type==3?"未接来电":type==2?"去电":type==5?"已拒接":type==6?"已拦截":type==4?"语音信箱":"来电";rows.add(new JSONObject().put("id","call:"+value(data,"_id")).put("kind","通话记录").put("source",number).put("phone",number).put("caller",name.isEmpty()?number:name).put("title",name.isEmpty()?number:name).put("body",direction+" · 时长 "+duration+" 秒").put("callType",type).put("duration",duration).put("at",at).put("date",new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm",Locale.CHINA).format(new Date(at))));}}catch(SecurityException e){throw new AccessException("provider","读取过程中通话记录权限被系统拒绝，请重新检查授权。");}catch(RuntimeException e){throw new AccessException("schema","通话记录已连接，但返回的记录字段无法读取。请重新读取并检查系统电话应用状态。");}return rows;
    }
}
