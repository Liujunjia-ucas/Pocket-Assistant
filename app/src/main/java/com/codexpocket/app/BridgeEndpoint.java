package com.codexpocket.app;
import java.net.URI;
/** Restrict bearer-token HTTP to literal loopback or Tailscale CGNAT addresses. */
final class BridgeEndpoint {
    static String normalize(String value){
        try{
            URI uri=new URI(value.trim());String host=uri.getHost();
            if(host==null||uri.getUserInfo()!=null||uri.getQuery()!=null||uri.getFragment()!=null||
                !(uri.getPath()==null||uri.getPath().isEmpty()||uri.getPath().equals("/")))throw new Exception();
            String scheme=uri.getScheme();boolean literal=host.equals("127.0.0.1");
            if(host.matches("100\\.[0-9]{1,3}\\.[0-9]{1,3}\\.[0-9]{1,3}")){
                String[] octets=host.split("\\.");int b=Integer.parseInt(octets[1]);
                literal=b>=64&&b<=127&&Integer.parseInt(octets[2])<=255&&Integer.parseInt(octets[3])<=255;
            }
            boolean secure="https".equalsIgnoreCase(scheme)&&host.toLowerCase(java.util.Locale.ROOT).endsWith(".ts.net");
            if(!secure&&!(literal&&("http".equalsIgnoreCase(scheme)||"https".equalsIgnoreCase(scheme))))throw new Exception();
            if(uri.getPort()==0||uri.getPort()>65535)throw new Exception();
            return new URI(scheme.toLowerCase(java.util.Locale.ROOT),null,host,uri.getPort(),null,null,null).toString();
        }catch(Exception e){throw new IllegalArgumentException("电脑地址仅支持 127.0.0.1、100.64–100.127 的合法 IP，或 HTTPS 的 .ts.net 域名。",e);}
    }
    private BridgeEndpoint(){}
}
