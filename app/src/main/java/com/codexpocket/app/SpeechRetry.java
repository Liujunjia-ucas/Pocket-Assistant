package com.codexpocket.app;

import java.io.EOFException;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;

/** One retry for a temporary link/gateway failure; never for invalid credentials. */
final class SpeechRetry {
    static boolean allowed(int attempt,int http,Exception error){
        if(attempt!=0)return false;
        if(http!=0)return http==502||http==503||http==504;
        return error instanceof SocketException||error instanceof SocketTimeoutException||error instanceof EOFException||error instanceof UnknownHostException;
    }
}
