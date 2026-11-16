package com.codexpocket.app;

import java.io.*;
import java.nio.charset.*;
import java.util.*;
import java.util.regex.*;

/** Bounded text preview of MIME; attachments and active HTML are never opened. */
final class MailMime {
    private static final Pattern WORD=Pattern.compile("=\\?([^?]+)\\?([bBqQ])\\?([^?]*)\\?=");
    static Map<String,String> headers(byte[] raw){
        String text=new String(raw,StandardCharsets.ISO_8859_1).replace("\r\n","\n");int end=text.indexOf("\n\n");if(end>=0)text=text.substring(0,end);
        text=text.replaceAll("\n[ \\t]+"," ");Map<String,String> out=new LinkedHashMap<>();for(String line:text.split("\n")){int colon=line.indexOf(':');if(colon>0)out.putIfAbsent(line.substring(0,colon).toLowerCase(Locale.ROOT),line.substring(colon+1).trim());}return out;
    }
    private static Charset charset(String name){try{return Charset.forName(name);}catch(Exception e){return StandardCharsets.UTF_8;}}
    static String decodeHeader(String value){
        // RFC 2047 ignores whitespace separating adjacent encoded words.
        value=value.replaceAll("(\\?=)[ \\t]+(?==\\?)","$1");Matcher m=WORD.matcher(value);StringBuffer out=new StringBuffer();while(m.find()){String decoded=m.group();try{byte[] bytes=m.group(2).equalsIgnoreCase("b")?Base64.getDecoder().decode(m.group(3)):quoted(m.group(3).replace('_',' ').getBytes(StandardCharsets.US_ASCII));decoded=new String(bytes,charset(m.group(1)));}catch(Exception ignored){}m.appendReplacement(out,Matcher.quoteReplacement(decoded));}m.appendTail(out);return out.toString();
    }
    private static String parameter(String header,String key,String fallback){Matcher m=Pattern.compile("(?:^|;)\\s*"+Pattern.quote(key)+"\\s*=\\s*(?:\"([^\"]*)\"|([^;\\s]+))",Pattern.CASE_INSENSITIVE).matcher(header);return m.find()?(m.group(1)==null?m.group(2):m.group(1)):fallback;}
    static byte[] quoted(byte[] input){ByteArrayOutputStream out=new ByteArrayOutputStream();for(int i=0;i<input.length;i++){int c=input[i]&255;if(c=='='&&i+1<input.length){if(input[i+1]=='\n'){i++;continue;}if(input[i+1]=='\r'&&i+2<input.length&&input[i+2]=='\n'){i+=2;continue;}if(i+2<input.length){int a=Character.digit((char)input[i+1],16),b=Character.digit((char)input[i+2],16);if(a>=0&&b>=0){out.write(a*16+b);i+=2;continue;}}}out.write(c);}return out.toByteArray();}
    static String preview(byte[] raw)throws IOException{if(raw.length>256*1024)throw new IOException("邮件预览过大");return part(raw,0).trim();}
    private static String part(byte[] raw,int depth)throws IOException{
        if(depth>6)return "";Map<String,String> h=headers(raw);String type=h.getOrDefault("content-type","text/plain"),disposition=h.getOrDefault("content-disposition","");
        if(disposition.toLowerCase(Locale.ROOT).startsWith("attachment")||!parameter(type,"name","").isEmpty())return "";
        String all=new String(raw,StandardCharsets.ISO_8859_1);int start=all.indexOf("\r\n\r\n"),skip=4;if(start<0){start=all.indexOf("\n\n");skip=2;}if(start<0)return "";byte[] body=Arrays.copyOfRange(raw,start+skip,raw.length);
        if(type.toLowerCase(Locale.ROOT).startsWith("multipart/")){
            String boundary=parameter(type,"boundary","");if(boundary.isEmpty()||boundary.length()>200)return "";
            String encoded=new String(body,StandardCharsets.ISO_8859_1),plain="",html="";String[] parts=encoded.split("(?m)^--"+Pattern.quote(boundary)+"(?:--)?[ \\t]*\\r?$",42);int n=0;
            for(int i=1;i<parts.length&&n++<40;i++){String child=parts[i].replaceFirst("^\\r?\\n","");byte[] bytes=child.getBytes(StandardCharsets.ISO_8859_1);String text=part(bytes,depth+1);if(text.isEmpty())continue;String ct=headers(bytes).getOrDefault("content-type","text/plain").toLowerCase(Locale.ROOT);if(ct.startsWith("text/html"))html=append(html,text);else plain=append(plain,text);}
            return plain.isEmpty()?html:plain;
        }
        String lower=type.toLowerCase(Locale.ROOT);if(!lower.startsWith("text/plain")&&!lower.startsWith("text/html"))return "";
        String encoding=h.getOrDefault("content-transfer-encoding","").trim();try{if(encoding.equalsIgnoreCase("base64"))body=Base64.getMimeDecoder().decode(body);else if(encoding.equalsIgnoreCase("quoted-printable"))body=quoted(body);}catch(IllegalArgumentException e){return "（邮件正文编码不完整，请在邮箱应用查看原文）";}
        String text=new String(body,charset(parameter(type,"charset","UTF-8")));if(lower.startsWith("text/html"))text=text.replaceAll("(?is)<(script|style)\\b[^>]*>.*?</\\1\\s*>","").replaceAll("(?i)<(?:br|/p|/div|/li)\\b[^>]*>","\n").replaceAll("(?s)<[^>]*>","").replace("&nbsp;"," ").replace("&lt;","<").replace("&gt;",">").replace("&quot;","\"").replace("&amp;","&");
        return cap(text.replaceAll("\n[ \\t]+","\n").replaceAll("\n{4,}","\n\n"),12000);
    }
    private static String append(String a,String b){return cap(a.isEmpty()?b:a+"\n\n"+b,12000);}
    private static String cap(String s,int n){return s.length()>n?s.substring(0,n)+"\n（预览已截取）":s;}
}
