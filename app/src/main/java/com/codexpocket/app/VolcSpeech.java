package com.codexpocket.app;

import org.json.JSONArray;
import org.json.JSONObject;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.UUID;

/** Phone -> official speech service. Complete reply, complete validated audio. */
final class VolcSpeech {
    static final String ENDPOINT="https://openspeech.bytedance.com/api/v3/tts/unidirectional";
    static final String SWEET="zh_female_tianmeitaozi_uranus_bigtts";
    static final String COOL="zh_female_gaolengyujie_uranus_bigtts";
    static final class Config {
        final String appId,key,resource,sweet,cool;
        Config(String appId,String key,String resource,String sweet,String cool){this.appId=appId.trim();this.key=key.trim();this.resource=resource.trim();this.sweet=sweet.trim();this.cool=cool.trim();}
        void validate() throws IOException {
            if(key.isEmpty())throw new IOException("请填写火山语音服务的 API Key 或 Access Token。");
            if(!appId.isEmpty()&&!appId.matches("[0-9]{1,32}"))throw new IOException("旧版 App ID 应为数字；新版 API Key 无需 App ID。");
            for(String value:new String[]{key,resource,sweet,cool})if(value.indexOf('\r')>=0||value.indexOf('\n')>=0)throw new IOException("配置中不能包含换行。");
            if(!resource.matches("seed-(tts|icl)-[a-zA-Z0-9.\\-]+"))throw new IOException("请填写正确的资源 ID，例如 seed-tts-2.0。");
            if(!sweet.matches("[a-zA-Z0-9_\\-]{1,180}")||!cool.matches("[a-zA-Z0-9_\\-]{1,180}"))throw new IOException("请填写有效的汐月和凌雪音色 ID。");
            if(sweet.equals(cool))throw new IOException("请为汐月和凌雪选择不同音色。");
        }
    }
    private volatile boolean cancelled;
    private volatile HttpURLConnection connection;
    void cancel(){cancelled=true;HttpURLConnection c=connection;if(c!=null)c.disconnect();}
    private void check()throws IOException{if(cancelled||Thread.currentThread().isInterrupted())throw new IOException("语音已取消。");}
    static JSONObject payload(Config config,boolean dragon,String text)throws Exception{
        String direction=dragon?"请用标准普通话、成熟清冷而从容的御姐声音自然聊天，语气有一点傲娇，亲近但克制，句子连贯，重音和停顿自然，不要播音腔，不要刻意压低嗓音，不要台湾腔。":"请用标准普通话、甜美温柔的年轻女声自然聊天，声音轻盈亲切，情绪随语义自然起伏，句子连贯，不要幼儿腔、机械腔或台湾腔。";
        JSONObject audio=new JSONObject().put("format","mp3").put("sample_rate",24000).put("bit_rate",128000).put("speech_rate",dragon?-2:0);
        JSONObject additions=new JSONObject().put("context_texts",new JSONArray().put(direction)).put("explicit_language","zh-cn").put("disable_markdown_filter",true);
        return new JSONObject().put("user",new JSONObject().put("uid","codex-pocket"))
            .put("req_params",new JSONObject().put("text",text).put("speaker",dragon?config.cool:config.sweet).put("audio_params",audio).put("additions",additions.toString()));
    }
    void synthesize(Config config,boolean dragon,String text,File destination)throws Exception{
        config.validate();check();if(text.isEmpty()||text.length()>6000)throw new IOException("语音文本过长，本次请看文字。");
        boolean complete=false;long deadline=System.nanoTime()+60000000000L;
        HttpURLConnection c=(HttpURLConnection)new URL(ENDPOINT).openConnection();connection=c;
        try{
            check();c.setRequestMethod("POST");c.setConnectTimeout(10000);c.setReadTimeout(15000);c.setInstanceFollowRedirects(false);c.setDoOutput(true);
            c.setRequestProperty("Content-Type","application/json; charset=utf-8");c.setRequestProperty("X-Api-Resource-Id",config.resource);c.setRequestProperty("X-Api-Request-Id",UUID.randomUUID().toString());
            if(config.appId.isEmpty())c.setRequestProperty("X-Api-Key",config.key);else{c.setRequestProperty("X-Api-App-Id",config.appId);c.setRequestProperty("X-Api-Access-Key",config.key);}
            byte[] request=payload(config,dragon,text).toString().getBytes(StandardCharsets.UTF_8);c.setFixedLengthStreamingMode(request.length);
            try(OutputStream out=c.getOutputStream()){out.write(request);}
            int status=c.getResponseCode();if(status!=200)throw new IOException(httpError(status));
            int bytes=0;
            try(Reader input=new BufferedReader(new InputStreamReader(c.getInputStream(),StandardCharsets.UTF_8));OutputStream out=new FileOutputStream(destination)){
                String line;
                while((line=readLine(input))!=null){
                    check();if(System.nanoTime()>deadline)throw new IOException("火山语音等待超时，请重试。");
                    if(line.trim().isEmpty())continue;
                    JSONObject entry=new JSONObject(line);int code=entry.optInt("code",Integer.MIN_VALUE);
                    if(code!=0&&code!=20000000)throw new IOException(serviceError(code));
                    String data=entry.optString("data","");
                    if(!entry.isNull("data")&&!data.isEmpty()){
                        byte[] chunk=Base64.getDecoder().decode(data);bytes+=chunk.length;if(bytes>16*1024*1024)throw new IOException("语音文件过大。");out.write(chunk);
                    }
                    if(code==20000000){if(bytes<100)throw new IOException("火山语音没有返回音频，请检查音色权限。");check();complete=true;break;}
                }
            }
            if(!complete)throw new IOException("语音传输中断，请重试。");
        }finally{connection=null;c.disconnect();if(!complete)destination.delete();}
    }
    private static String readLine(Reader input)throws IOException{
        StringBuilder line=new StringBuilder();int value;
        while((value=input.read())!=-1){if(value=='\n')break;if(value!='\r')line.append((char)value);if(line.length()>2*1024*1024)throw new IOException("语音响应过大。");}
        return value==-1&&line.length()==0?null:line.toString();
    }
    static String httpError(int code){return code==401||code==403?"火山语音鉴权失败，请检查语音服务凭据、开通状态和音色授权。":code==429?"火山语音请求过于频繁，请稍后重试。":"火山语音连接失败（HTTP "+code+"）。";}
    static String serviceError(int code){return code==45000000?"火山语音音色未授权或额度不足，请核对音色与资源 ID。":code==40402003?"回复超过火山语音的长度限制，本次请看文字。":"火山语音合成失败（"+code+"），请核对语音服务配置。";}
}
