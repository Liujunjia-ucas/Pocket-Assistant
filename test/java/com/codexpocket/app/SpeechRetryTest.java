package com.codexpocket.app;
import java.io.IOException;
import java.io.EOFException;
import java.net.SocketException;
import java.net.SocketTimeoutException;
public final class SpeechRetryTest {
    static void require(boolean value,String why){if(!value)throw new AssertionError(why);}
    public static void main(String[] args){
        for(int status:new int[]{502,503,504}){require(SpeechRetry.allowed(0,status,new IOException()),"gateway first retry");require(!SpeechRetry.allowed(1,status,new IOException()),"retry bounded");}
        for(int status:new int[]{400,401,403,404,413,429})require(!SpeechRetry.allowed(0,status,new SocketException()),"never retry auth/input/rate limit "+status);
        for(Exception error:new Exception[]{new SocketException(),new SocketTimeoutException(),new EOFException()}){require(SpeechRetry.allowed(0,0,error),"link retry");require(!SpeechRetry.allowed(1,0,error),"link retry bounded");}
        require(!SpeechRetry.allowed(0,0,new IOException("cancelled or bad file")),"no retry arbitrary IO error");
        System.out.println("PASS: one temporary speech retry, no auth/rate-limit retry and no retry loop.");
    }
}
