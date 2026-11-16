package com.codexpocket.app;

import android.content.Context;
import org.json.*;
import java.util.*;

/** Provider-independent registry. Legacy configured accounts migrate without embedding identities. */
final class MailAccounts {
    static synchronized JSONArray list(Context c)throws Exception{
        String raw=SecretStore.load(c,"assistant_mail_accounts");if(!raw.isEmpty())return new JSONArray(raw);
        JSONArray entries=new JSONArray();for(String id:new String[]{"ucas","qq"}){String old=SecretStore.load(c,"assistant_mail_"+id);if(!old.isEmpty()){JSONObject account=new JSONObject(old);entries.put(new JSONObject().put("id",id).put("label",account.optString("user")).put("sync",true));}}
        SecretStore.save(c,"assistant_mail_accounts",entries.toString());return entries;
    }
    static String newId(){return UUID.randomUUID().toString();}
    private static void validateId(String id)throws Exception{if(!id.matches("[a-z0-9-]{2,40}"))throw new java.io.IOException("邮箱编号无效");}
    static synchronized JSONObject get(Context c,String id)throws Exception{validateId(id);String raw=SecretStore.load(c,"assistant_mail_"+id);return raw.isEmpty()?null:new JSONObject(raw);}
    static synchronized void save(Context c,String id,JSONObject account)throws Exception{
        validateId(id);ImapMailbox.validate(account.getString("host"),account.getInt("port"),account.getString("user"),account.getString("password"));JSONArray entries=list(c);int found=-1;for(int i=0;i<entries.length();i++)if(id.equals(entries.getJSONObject(i).optString("id")))found=i;
        if(found<0&&entries.length()>=10)throw new java.io.IOException("最多添加 10 个邮箱");SecretStore.save(c,"assistant_mail_"+id,account.toString());JSONObject meta=new JSONObject().put("id",id).put("label",account.optString("label",account.getString("user"))).put("sync",true);if(found<0)entries.put(meta);else entries.put(found,meta);SecretStore.save(c,"assistant_mail_accounts",entries.toString());
    }
    static synchronized void remove(Context c,String id)throws Exception{JSONArray old=list(c),next=new JSONArray();for(int i=0;i<old.length();i++)if(!id.equals(old.getJSONObject(i).optString("id")))next.put(old.get(i));SecretStore.save(c,"assistant_mail_accounts",next.toString());SecretStore.save(c,"assistant_mail_"+id,"");}
    static String suggestedHost(String user){String domain=user.trim().toLowerCase(Locale.ROOT);int at=domain.lastIndexOf('@');if(at<0)return "";switch(domain.substring(at+1)){case "qq.com":case "foxmail.com":return "imap.qq.com";case "163.com":return "imap.163.com";case "126.com":return "imap.126.com";case "yeah.net":return "imap.yeah.net";case "gmail.com":return "imap.gmail.com";case "outlook.com":case "hotmail.com":case "live.com":return "outlook.office365.com";case "icloud.com":case "me.com":return "imap.mail.me.com";default:return "";}}
}
