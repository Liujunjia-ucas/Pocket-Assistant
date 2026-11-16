package com.codexpocket.app;

import com.sun.net.httpserver.HttpServer;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/** Synthetic documents and real local HTTP; no user files, credentials or paid calls. */
public final class DirectInputsTest {
    interface Work { void run() throws Exception; }
    static void require(boolean value,String why){if(!value)throw new AssertionError(why);}
    static void rejects(Work work,String why)throws Exception{boolean failed=false;try{work.run();}catch(Exception e){failed=true;}require(failed,why);}
    static File zip(File root,String name,Map<String,String> members)throws Exception{
        File file=new File(root,name);
        try(ZipOutputStream out=new ZipOutputStream(new FileOutputStream(file))){for(Map.Entry<String,String> member:members.entrySet()){out.putNextEntry(new ZipEntry(member.getKey()));out.write(member.getValue().getBytes(StandardCharsets.UTF_8));out.closeEntry();}}return file;
    }
    static JSONObject document(DirectInputs store,String name,String text)throws Exception{
        JSONObject ref=store.copy(new ByteArrayInputStream(text.getBytes(StandardCharsets.UTF_8)),name);store.addText(ref,text,"TXT");return ref;
    }
    static void checkParts(JSONArray messages,boolean responses)throws Exception{
        JSONObject user=messages.getJSONObject(messages.length()-1);JSONArray parts=user.getJSONArray("content");
        require(parts.getJSONObject(0).getString("text").equals("对照图片和报告"),"question in same content array");
        boolean image=false,document=false;
        for(int i=0;i<parts.length();i++){JSONObject part=parts.getJSONObject(i);String type=part.getString("type");
            if(type.equals(responses?"input_image":"image_url")){
                String url=responses?part.getString("image_url"):part.getJSONObject("image_url").getString("url");
                byte[] decoded=Base64.getDecoder().decode(url.substring(url.indexOf(',')+1));require(decoded.length>60,"image payload survives base64");image=true;
            }else{require(type.equals(responses?"input_text":"text"),"matching protocol text type");if(part.getString("text").contains("报告正文🐳"))document=true;}
        }
        require(image&&document,"image and document together");require(!user.has("attachmentRefs"),"local metadata never forwarded");
    }
    public static void main(String[] args)throws Exception{
        File root=Files.createTempDirectory("pocket-inputs-test-").toFile();
        try{
            DirectInputs store=new DirectInputs(new File(root,"blobs"));
            JSONObject image=store.copy(new ByteArrayInputStream(new byte[]{1,2,3}),"鲸鱼.png");
            byte[] png=Base64.getDecoder().decode("iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+a7f8AAAAASUVORK5CYII=");
            File imageBlob=store.newBlob(image,"image","image/png");Files.write(imageBlob.toPath(),png);image.put("kind","image");
            JSONObject doc=document(store,"报告.txt","报告正文🐳\n总数：42");
            JSONArray refs=new JSONArray().put(image).put(doc);
            DirectChats chats=new DirectChats(new File(root,"chats"));JSONObject chat=chats.create("deepseek","https://api.deepseek.com/v1","deepseek-flash","chat");
            JSONArray neutral=DirectChats.context(chat,"对照图片和报告",refs);
            for(String protocol:new String[]{"chat","responses"}){JSONArray docs=store.messages(DirectChats.context(chat,"读报告",new JSONArray().put(doc)),protocol);Object content=docs.getJSONObject(0).get("content");require(content instanceof String&&((String)content).contains("报告正文🐳")&&((String)content).contains("报告.txt"),"text-only document uses widely supported string content, preserves source label");}
            for(String protocol:new String[]{"chat","responses"}){
                JSONArray wire=store.messages(LocalFileSpec.withGuide(neutral),protocol);checkParts(wire,protocol.equals("responses"));
                require(!wire.toString().contains(root.getPath())&&!wire.toString().contains(image.getString("id")),"no private paths or IDs sent");
            }
            chat.getJSONArray("messages").put(new JSONObject().put("role","user").put("text","上轮").put("attachments",refs))
                .put(new JSONObject().put("role","assistant").put("text","读到了").put("state","complete"));
            chat.put("draftAttachments",refs);chats.save(chat);JSONObject reloaded=chats.load(chat.getString("id"));
            JSONArray history=store.messages(DirectChats.context(reloaded,"继续"),"chat");
            require(history.getJSONObject(0).getJSONArray("content").length()==5,"complete attachment history restored");
            require(reloaded.getJSONArray("draftAttachments").length()==2,"draft references restored");
            chat.getJSONArray("messages").getJSONObject(1).put("state","failed");
            require(DirectChats.context(chat,"继续").length()==1,"failed attachment pair excluded");
            rejects(()->store.path("../outside"),"traversal rejected");
            rejects(()->store.copy(new ByteArrayInputStream(new byte[0]),"empty.txt"),"empty rejected");
            int before=new File(root,"blobs").list().length;
            rejects(()->store.copy(new InputStream(){long left=DirectInputs.MAX_FILE+1;@Override public int read(){return left-->0?0:-1;}@Override public int read(byte[] bytes,int off,int len){if(left<=0)return -1;int n=(int)Math.min(left,len);left-=n;Arrays.fill(bytes,off,off+n,(byte)0);return n;}},"large.txt"),"copy size cap");
            require(new File(root,"blobs").list().length==before,"failed copy rolls back");
            rejects(()->store.addText(doc,"x".repeat(60001),"too long"),"document length cap");
            JSONArray tooMany=new JSONArray();for(int i=0;i<6;i++)tooMany.put(doc);
            rejects(()->store.messages(DirectChats.context(chat,"too many",tooMany),"chat"),"attachment count cap");
            JSONArray imageRefs=new JSONArray().put(image);JSONArray manyPages=new JSONArray();for(int i=0;i<21;i++)manyPages.put(new JSONObject().put("role","user").put("content","page").put("attachmentRefs",imageRefs));
            rejects(()->store.messages(manyPages,"chat"),"image/page context count cap");
            rejects(()->store.messages(new JSONArray().put(new JSONObject().put("role","user").put("content","x".repeat(160001))),"chat"),"whole request text cap");
            JSONObject largeImage=store.copy(new ByteArrayInputStream(new byte[]{1}),"large.png");File largeBlob=store.newBlob(largeImage,"image","image/png");
            try(RandomAccessFile raw=new RandomAccessFile(largeBlob,"rw")){raw.setLength(DirectInputs.MAX_IMAGES+1);}
            rejects(()->store.messages(DirectChats.context(chat,"large",new JSONArray().put(largeImage)),"responses"),"image bytes capped before base64");store.discard(largeImage);
            File word=zip(root,"sample.docx",Map.of("word/document.xml","<w:document xmlns:w='urn:w'><w:p><w:r><w:t>中文报告🐳</w:t></w:r></w:p><w:tbl><w:tr><w:tc><w:p><w:r><w:t>表格</w:t></w:r></w:p></w:tc></w:tr></w:tbl></w:document>"));
            String wordText=DocumentText.extract(word,"docx");require(wordText.contains("中文报告🐳")&&wordText.contains("表格"),"Word paragraphs/table text");
            JSONArray generatedFiles=new JSONArray(Files.readString(new File("state/local-file-fixtures/manifest.json").toPath()));
            for(int i=0;i<generatedFiles.length();i++){JSONObject file=generatedFiles.getJSONObject(i);String ext=DirectInputs.extension(file.getString("name"));if(ext.equals("docx")||ext.equals("xlsx"))require(!DocumentText.extract(new File(file.getString("path")),ext).trim().isEmpty(),"extract actual app-generated "+ext);}
            Map<String,String> sheets=new HashMap<>();
            sheets.put("xl/workbook.xml","<workbook xmlns:r='http://schemas.openxmlformats.org/officeDocument/2006/relationships'><sheets><sheet name='统计' r:id='rId1'/></sheets></workbook>");
            sheets.put("xl/_rels/workbook.xml.rels","<Relationships><Relationship Id='rId1' Target='worksheets/sheet1.xml'/></Relationships>");
            sheets.put("xl/sharedStrings.xml","<sst><si><r><t>苹果</t></r><r><t>🐳</t></r></si></sst>");
            sheets.put("xl/worksheets/sheet1.xml","<worksheet><sheetData><row><c r='A1' t='s'><v>0</v></c><c r='B1'><v>42</v></c><c r='C1' t='b'><v>1</v></c><c r='D1' t='inlineStr'><is><t>行内</t></is></c><c r='E1'><f>SUM(B1)</f></c></row></sheetData></worksheet>");
            String sheet=DocumentText.extract(zip(root,"sample.xlsx",sheets),"xlsx");
            for(String expected:new String[]{"统计","A1=苹果🐳","B1=42","C1=true","D1=行内","[公式未计算] =SUM(B1)"})require(sheet.contains(expected),"Excel: "+expected);
            Map<String,String> slides=new HashMap<>();for(int i:new int[]{10,2,1})slides.put("ppt/slides/slide"+i+".xml","<slide><p><t>第"+i+"页</t></p></slide>");
            String ppt=DocumentText.extract(zip(root,"sample.pptx",slides),"pptx");require(ppt.indexOf("第1页")<ppt.indexOf("第2页")&&ppt.indexOf("第2页")<ppt.indexOf("第10页"),"numeric slide order");
            File text=new File(root,"gb.txt");Files.write(text.toPath(),"中文编码".getBytes(Charset.forName("GB18030")));require(DocumentText.extract(text,"txt").equals("中文编码"),"GB18030 decoded");
            Files.write(text.toPath(),new byte[]{(byte)255,(byte)254,45,78,(byte)135,101});require(DocumentText.extract(text,"txt").equals("中文"),"UTF16 BOM decoded");
            Files.writeString(text.toPath(),"x".repeat(60001));rejects(()->DocumentText.extract(text,"txt"),"text not silently truncated");
            Files.write(text.toPath(),new byte[]{0,1,2});rejects(()->DocumentText.extract(text,"txt"),"binary disguised as text rejected");rejects(()->DocumentText.extract(word,"doc"),"legacy doc conversion required");
            File dtd=zip(root,"dtd.docx",Map.of("word/document.xml","<!DOCTYPE document [<!ENTITY x 'boom'>]><document><t>&x;</t></document>"));rejects(()->DocumentText.extract(dtd,"docx"),"DTD rejected");
            File bomb=zip(root,"oversized.docx",Map.of("word/document.xml","x".repeat(4*1024*1024+1)));rejects(()->DocumentText.extract(bomb,"docx"),"inflated XML cap");
            AtomicInteger requests=new AtomicInteger();AtomicReference<Throwable> failure=new AtomicReference<>();
            HttpServer server=HttpServer.create(new java.net.InetSocketAddress("127.0.0.1",0),0);
            server.createContext("/v1/",exchange->{try{
                requests.incrementAndGet();JSONObject body=new JSONObject(new String(exchange.getRequestBody().readAllBytes(),StandardCharsets.UTF_8));boolean responses=exchange.getRequestURI().getPath().endsWith("/responses");
                checkParts(body.getJSONArray(responses?"input":"messages"),responses);
                String response=responses?"{\"status\":\"completed\",\"output\":[{\"type\":\"message\",\"content\":[{\"type\":\"output_text\",\"text\":\"已读取附件\"}]}]}":"{\"choices\":[{\"message\":{\"content\":\"已读取附件\"},\"finish_reason\":\"stop\"}]}";
                byte[] bytes=response.getBytes(StandardCharsets.UTF_8);exchange.getResponseHeaders().set("Content-Type","application/json");exchange.sendResponseHeaders(200,bytes.length);try(OutputStream out=exchange.getResponseBody()){out.write(bytes);}
            }catch(Throwable error){failure.set(error);exchange.close();}});server.start();
            try{
                for(String protocol:new String[]{"chat","responses"})require(new DirectApi("http://127.0.0.1:"+server.getAddress().getPort()+"/v1","fake-local-input-key").chat("test",protocol,store.messages(neutral,protocol),value->{}).equals("已读取附件"),"real HTTP mixed inputs "+protocol);
                if(failure.get()!=null)throw new AssertionError(failure.get());require(requests.get()==2,"exactly one call per protocol");
                Files.delete(imageBlob.toPath());rejects(()->{JSONArray wire=store.messages(neutral,"chat");new DirectApi("http://127.0.0.1:"+server.getAddress().getPort()+"/v1","fake-local-input-key").chat("test","chat",wire,value->{});},"missing cache aborts before network");require(requests.get()==2,"no silently dropped attachments or retry");
            }finally{server.stop(0);}
            store.discard(image);store.discard(doc);require(new File(root,"blobs").list().length==0,"original and derived private blobs cleaned");
            System.out.println("PASS: mixed image/document HTTP on both protocols, private refs/history/drafts, bounded copy/text, DOCX/XLSX/PPTX/encodings, malformed/DTD/ZIP limits, no dropped inputs and cleanup.");
        }finally{try(java.util.stream.Stream<java.nio.file.Path> paths=Files.walk(root.toPath())){paths.sorted(Comparator.reverseOrder()).forEach(path->{try{Files.delete(path);}catch(IOException e){throw new java.io.UncheckedIOException(e);}});}}
    }
}
