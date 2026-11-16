package com.codexpocket.app;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.json.*;

public class VolcSpeechTest {
    static Mock next;
    static class Mock extends HttpURLConnection {
        String response;int status=200;Map<String,String> headers=new HashMap<>();ByteArrayOutputStream body=new ByteArrayOutputStream();boolean disconnected;
        Mock(String response)throws Exception{super(new URL("https://openspeech.bytedance.com"));this.response=response;}
        public void connect(){}public boolean usingProxy(){return false;}public void disconnect(){disconnected=true;}
        public void setRequestProperty(String key,String value){headers.put(key,value);}public OutputStream getOutputStream(){return body;}
        public int getResponseCode(){return status;}public InputStream getInputStream(){return new ByteArrayInputStream(response.getBytes(StandardCharsets.UTF_8));}
    }
    static void check(boolean value,String why){if(!value)throw new AssertionError(why);}
    static VolcSpeech.Config config(String app){return new VolcSpeech.Config(app,"test-secret","seed-tts-2.0",VolcSpeech.SWEET,VolcSpeech.COOL);}
    static void failure(VolcSpeech request,File out,String why)throws Exception{try{request.synthesize(config(""),false,"你好",out);throw new AssertionError(why);}catch(IOException expected){check(!out.exists(),"failed response must not leave partial audio: "+why);check(!expected.getMessage().contains("test-secret"),"no secret in errors");}}
    public static void main(String[] args)throws Exception{
        URL.setURLStreamHandlerFactory(protocol->"https".equals(protocol)?new URLStreamHandler(){protected URLConnection openConnection(URL url){return next;}}:null);
        byte[] first=new byte[180],second=new byte[220];Arrays.fill(first,(byte)42);Arrays.fill(second,(byte)21);
        String stream=new JSONObject().put("code",0).put("data",Base64.getEncoder().encodeToString(first))+"\n"+new JSONObject().put("code",0).put("data",JSONObject.NULL).put("sentence",new JSONObject())+"\n"+new JSONObject().put("code",0).put("data",Base64.getEncoder().encodeToString(second))+"\n{\"code\":20000000,\"data\":null}\n";
        File out=File.createTempFile("voice-test-",".mp3");out.delete();
        next=new Mock(stream);new VolcSpeech().synthesize(config(""),false,"今天有一点想你。",out);
        byte[] result=java.nio.file.Files.readAllBytes(out.toPath());check(result.length==400&&result[0]==42&&result[399]==21,"complete chunk concatenation");out.delete();
        check(next.headers.get("X-Api-Key").equals("test-secret")&&!next.headers.containsKey("X-Api-App-Id"),"new auth only");
        JSONObject sweet=new JSONObject(next.body.toString("UTF-8")).getJSONObject("req_params");check(sweet.getString("speaker").equals(VolcSpeech.SWEET),"sweet real neural speaker");
        check(!sweet.has("context_texts")&&new JSONObject(sweet.getString("additions")).getJSONArray("context_texts").getString(0).contains("温柔"),"direction belongs under serialized additions");
        next=new Mock(stream);new VolcSpeech().synthesize(config("123456"),true,"过来，听我说。",out);out.delete();
        check(!next.headers.containsKey("X-Api-Key")&&next.headers.get("X-Api-App-Id").equals("123456"),"legacy auth");
        check(new JSONObject(next.body.toString("UTF-8")).getJSONObject("req_params").getString("speaker").equals(VolcSpeech.COOL),"different cool speaker");
        next=new Mock(stream.substring(0,stream.indexOf("{\"code\":20000000")));failure(new VolcSpeech(),out,"truncated success cannot play partial sentence");
        next=new Mock("{\"code\":45000000,\"message\":\"test-secret\"}\n");failure(new VolcSpeech(),out,"service permission failure");
        next=new Mock(stream);next.status=403;failure(new VolcSpeech(),out,"http auth failure");
        VolcSpeech cancelled=new VolcSpeech();cancelled.cancel();next=new Mock(stream);failure(cancelled,out,"cancel before connect");
        try{new VolcSpeech.Config("","test-secret","seed-tts-2.0",VolcSpeech.COOL,VolcSpeech.COOL).validate();throw new AssertionError("same voice");}catch(IOException expected){}
        try{new VolcSpeech.Config("","test-secret\r\nOops: bad","seed-tts-2.0",VolcSpeech.SWEET,VolcSpeech.COOL).validate();throw new AssertionError("header injection");}catch(IOException expected){}
        System.out.println("PASS: complete phone TTS, two real voices, new/legacy auth, directions, metadata, truncation, permissions, cancellation and secret-safe errors.");
    }
}
