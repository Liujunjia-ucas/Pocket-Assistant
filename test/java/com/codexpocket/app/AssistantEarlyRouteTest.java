package com.codexpocket.app;

import org.json.*;
import com.sun.net.httpserver.*;
import java.net.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

/** Loopback fixture, no remote account or paid request. */
public final class AssistantEarlyRouteTest {
    static void check(boolean value,String reason){if(!value)throw new AssertionError(reason);}
    public static void main(String[] args)throws Exception{
        HttpServer server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);CountDownLatch release=new CountDownLatch(1);AtomicBoolean sent=new AtomicBoolean();String json="{\"intent\":\"android\",\"confidence\":0.99}";
        server.createContext("/v1/chat/completions",exchange->{try{exchange.getRequestBody().readAllBytes();exchange.getResponseHeaders().set("Content-Type","text/event-stream");exchange.sendResponseHeaders(200,0);try(OutputStream out=exchange.getResponseBody()){String payload=new JSONObject().put("choices",new JSONArray().put(new JSONObject().put("delta",new JSONObject().put("content",json)))).toString();out.write(("data: "+payload+"\n\n").getBytes(StandardCharsets.UTF_8));out.flush();sent.set(true);release.await(4,TimeUnit.SECONDS);out.write("data: [DONE]\n\n".getBytes(StandardCharsets.UTF_8));}}catch(Exception ignored){}finally{exchange.close();}});server.start();
        try{DirectApi api=new DirectApi("http://127.0.0.1:"+server.getAddress().getPort()+"/v1","fixture-key",2000);AtomicInteger decisions=new AtomicInteger();long start=System.nanoTime();try{api.chat("fixture","chat",new JSONArray().put(new JSONObject().put("role","user").put("content","帮我写报告")),512,text->{String complete=AssistantFastRouter.completedJson(text);if(complete!=null)try{check(AssistantRouter.parse(complete,"帮我写报告").kind==AssistantRouter.Kind.ANDROID,"validated route from actual transport");decisions.incrementAndGet();api.cancel();}catch(RuntimeException e){throw e;}catch(Exception e){throw new RuntimeException(e);}});}catch(IOException expected){}
            double elapsed=(System.nanoTime()-start)/1e6;check(sent.get()&&decisions.get()==1,"single validated decision before stream trailer");check(elapsed<3000,"does not wait for four-second stream tail");System.out.printf(java.util.Locale.ROOT,"PASS: production SSE transport delivers validated routing JSON once and cancels trailing wait (loopback %.1f ms; not live API latency).%n",elapsed);
        }finally{release.countDown();server.stop(0);}
    }
}
