package com.codexpocket.app;
public final class BridgeEndpointTest {
    public static void main(String[] args){
        for(String value:new String[]{"http://127.0.0.1:8787","http://100.64.0.10:8787","https://computer.example.ts.net"})BridgeEndpoint.normalize(value);
        for(String value:new String[]{"http://evil.example","http://computer.example.ts.net","http://100.128.0.1","http://100.64.999.1","http://100.64.0.10.evil.example","https://computer.ts.net.evil.example","http://user:pass@100.64.0.10","http://100.64.0.10?token=x","http://100.64.0.10/path","http://100.64.0.10:99999"}){
            try{BridgeEndpoint.normalize(value);throw new AssertionError(value);}catch(IllegalArgumentException expected){}
        }
        System.out.println("BridgeEndpoint: allowed transport, IP bounds, credentials, DNS suffix, port and path checks passed");
    }
}
