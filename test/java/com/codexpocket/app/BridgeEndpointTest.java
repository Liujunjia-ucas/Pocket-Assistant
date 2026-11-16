package com.codexpocket.app;
public final class BridgeEndpointTest {
    public static void main(String[] args){
        String token="test-pairing-token-not-a-secret-123456";
        if(BridgeEndpoint.configured("",token)||BridgeEndpoint.configured(null,token)||BridgeEndpoint.configured("http://100.64.0.10:8787","")||BridgeEndpoint.configured("http://100.64.0.10:8787","short")||BridgeEndpoint.configured("https://evil.example",token))throw new AssertionError("Incomplete/invalid pairing must not connect");
        if(!BridgeEndpoint.configured("http://100.64.0.10:8787",token)||!BridgeEndpoint.configured("http://127.0.0.1:8787",token))throw new AssertionError("Explicit saved addresses must remain usable");
        for(String value:new String[]{"http://127.0.0.1:8787","http://100.64.0.10:8787","https://computer.example.ts.net"})BridgeEndpoint.normalize(value);
        for(String value:new String[]{"http://evil.example","http://computer.example.ts.net","http://100.128.0.1","http://100.64.999.1","http://100.64.0.10.evil.example","https://computer.ts.net.evil.example","http://user:pass@100.64.0.10","http://100.64.0.10?token=x","http://100.64.0.10/path","http://100.64.0.10:99999"}){
            try{BridgeEndpoint.normalize(value);throw new AssertionError(value);}catch(IllegalArgumentException expected){}
        }
        System.out.println("BridgeEndpoint: allowed transport, IP bounds, credentials, DNS suffix, port and path checks passed");
    }
}
