package com.codexpocket.app;

import javax.net.ssl.*;
import java.net.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.*;
import org.json.*;

/** Foreground, read-only IMAP over verified implicit TLS. No SMTP or mailbox mutation. */
final class ImapMailbox implements AutoCloseable {
    ImapMailbox(){}
    ImapMailbox(InputStream input,OutputStream output){in=input;out=output;}
    private SSLSocket socket;private InputStream in;private OutputStream out;private int sequence;private volatile boolean cancelled;private boolean selected;private String validity="";
    static final int PREVIEW_BYTES=196608;
    static void validate(String host,int port,String user,String password)throws IOException{
        if(host.isEmpty()||host.length()>253||!host.matches("[A-Za-z0-9](?:[A-Za-z0-9.-]*[A-Za-z0-9])?")||host.contains("..")||port<1||port>65535)throw new IOException("请填写有效 IMAP 服务器和端口");
        if(user.isEmpty()||password.isEmpty()||user.length()>320||password.length()>1024||user.matches("(?s).*[\\r\\n\\x00].*")||password.matches("(?s).*[\\r\\n\\x00].*"))throw new IOException("请填写有效账号及邮箱授权码");
    }
    void connect(String host,int port,String user,String password)throws Exception{
        validate(host,port,user,password);if(cancelled)throw new IOException("邮箱读取已取消");
        socket=(SSLSocket)SSLSocketFactory.getDefault().createSocket();SSLParameters parameters=socket.getSSLParameters();parameters.setEndpointIdentificationAlgorithm("HTTPS");socket.setSSLParameters(parameters);socket.setSoTimeout(20000);socket.connect(new InetSocketAddress(host,port),15000);if(cancelled)throw new IOException("邮箱读取已取消");socket.startHandshake();in=socket.getInputStream();out=socket.getOutputStream();String greeting=line(in);if(!greeting.startsWith("* OK"))throw new IOException("邮箱未提供可用的 IMAP 登录服务");
        command("LOGIN "+quote(user)+" "+quote(password));
    }
    static String quote(String value)throws IOException{if(value.indexOf('\r')>=0||value.indexOf('\n')>=0||value.indexOf('\0')>=0)throw new IOException("邮箱凭据格式无效");return "\""+value.replace("\\","\\\\").replace("\"","\\\"")+"\"";}
    private int examine()throws Exception{
        List<Response> exam=command("EXAMINE INBOX");int count=0;Pattern exists=Pattern.compile("\\* (\\d+) EXISTS",Pattern.CASE_INSENSITIVE),epoch=Pattern.compile("\\[UIDVALIDITY (\\d+)\\]",Pattern.CASE_INSENSITIVE);for(Response r:exam){Matcher m=exists.matcher(r.text);if(m.find())count=Integer.parseInt(m.group(1));m=epoch.matcher(r.text);if(m.find())validity=m.group(1);}selected=true;return count;
    }
    List<JSONObject> list()throws Exception{
        int count=examine();if(count==0)return new ArrayList<>();
        int from=Math.max(1,count-29);List<Response> rows=command("FETCH "+from+":"+count+" (UID FLAGS INTERNALDATE BODY.PEEK[HEADER.FIELDS (FROM SUBJECT DATE MESSAGE-ID)]<0.32768>)");List<JSONObject> messages=new ArrayList<>();
        Pattern uid=Pattern.compile("\\bUID (\\d+)",Pattern.CASE_INSENSITIVE);for(Response row:rows){Matcher m=uid.matcher(row.text);if(!m.find()||row.literals.isEmpty())continue;Map<String,String> h=MailMime.headers(row.literals.get(0));messages.add(new JSONObject().put("uid",m.group(1)).put("uidValidity",validity).put("title",MailMime.decodeHeader(h.getOrDefault("subject","（无主题）"))).put("source",MailMime.decodeHeader(h.getOrDefault("from","未知发件人"))).put("date",h.getOrDefault("date","")).put("body","").put("kind","邮件").put("unread",!row.text.toUpperCase(Locale.ROOT).contains("\\SEEN")));}
        for(JSONObject message:messages){long at=0;String rawDate=message.optString("date");for(String pattern:new String[]{"EEE, d MMM yyyy HH:mm:ss Z","d MMM yyyy HH:mm:ss Z","EEE, d MMM yyyy HH:mm Z","d MMM yyyy HH:mm Z"})try{at=new java.text.SimpleDateFormat(pattern,Locale.US).parse(rawDate.trim()).getTime();break;}catch(Exception ignored){}message.put("at",at);}
        Pattern internal=Pattern.compile("INTERNALDATE \"([^\"]+)\"",Pattern.CASE_INSENSITIVE);for(Response row:rows){Matcher match=internal.matcher(row.text),uidMatch=uid.matcher(row.text);if(match.find()&&uidMatch.find())try{long at=new java.text.SimpleDateFormat("d-MMM-yyyy HH:mm:ss Z",Locale.US).parse(match.group(1).trim()).getTime();for(JSONObject m:messages)if(uidMatch.group(1).equals(m.optString("uid")))m.put("at",at);}catch(Exception ignored){}}
        Collections.reverse(messages);return messages;
    }
    String body(String uid)throws Exception{return body(uid,"");}
    String body(String uid,String expectedValidity)throws Exception{if(!uid.matches("[1-9][0-9]{0,11}"))throw new IOException("邮件编号无效");if(!selected)examine();if(!expectedValidity.isEmpty()&&!expectedValidity.equals(validity))throw new IOException("邮箱目录已变化，请重新读取收件箱");List<Response> result=command("UID FETCH "+uid+" (UID BODY.PEEK[]<0."+PREVIEW_BYTES+">)");Pattern expected=Pattern.compile("\\bUID "+Pattern.quote(uid)+"\\b",Pattern.CASE_INSENSITIVE);for(Response r:result)if(expected.matcher(r.text).find()&&!r.literals.isEmpty()){byte[] bytes=r.literals.get(0);String text=MailMime.preview(bytes);return (text.isEmpty()?"（该邮件没有可显示的文字正文，请在邮箱应用查看原文）":text)+(bytes.length>=PREVIEW_BYTES?"\n（仅获取邮件前 192 KB）":"");}throw new IOException("该邮件已不存在或无法读取");}
    private List<Response> command(String command)throws Exception{
        if(cancelled)throw new IOException("邮箱读取已取消");String tag="P"+(++sequence);out.write((tag+" "+command+"\r\n").getBytes(StandardCharsets.UTF_8));out.flush();return read(in,tag);
    }
    static final class Response {String text="";final List<byte[]> literals=new ArrayList<>();}
    static List<Response> read(InputStream in,String tag)throws Exception{
        List<Response> rows=new ArrayList<>();Response current=null;int total=0;Pattern literal=Pattern.compile("\\{(\\d+)\\+?\\}$");
        while(true){String line=line(in);total+=line.length();if(total>2*1024*1024||rows.size()>1000)throw new IOException("邮箱响应过大");
            if(line.startsWith(tag+" ")){if(current!=null)rows.add(current);if(!line.regionMatches(true,tag.length()+1,"OK",0,2))throw new IOException("邮箱操作未成功，请检查服务器、IMAP 开关及客户端授权码");return rows;}
            if(line.startsWith("* BYE")||line.startsWith("+"))throw new IOException("邮箱服务不支持当前登录方式，请使用客户端授权码");
            if(line.startsWith("* ")||current==null){if(current!=null)rows.add(current);current=new Response();}
            current.text+=line+"\n";Matcher m=literal.matcher(line);if(m.find()){long n=Long.parseLong(m.group(1));if(n>256*1024||n<0||total+n>2*1024*1024)throw new IOException("邮件预览过大");byte[] bytes=new byte[(int)n];int at=0;while(at<bytes.length){int got=in.read(bytes,at,bytes.length-at);if(got<0)throw new EOFException("邮件读取中断");at+=got;}current.literals.add(bytes);total+=bytes.length;}
        }
    }
    static String line(InputStream in)throws IOException{ByteArrayOutputStream b=new ByteArrayOutputStream();int c;while((c=in.read())!=-1){if(c=='\n')return b.toString(StandardCharsets.UTF_8.name()).replaceFirst("\r$","");if(b.size()>=16384)throw new IOException("邮箱响应行过长");b.write(c);}throw new EOFException("邮箱连接已中断");}
    void cancel(){cancelled=true;close();}
    @Override public void close(){if(socket!=null)try{socket.close();}catch(Exception ignored){}}
}
