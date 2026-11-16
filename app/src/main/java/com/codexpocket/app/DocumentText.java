package com.codexpocket.app;

import org.xml.sax.Attributes;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;
import javax.xml.parsers.SAXParserFactory;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/** Bounded local text extraction for modern Office and UTF-8/GB18030 documents. */
final class DocumentText {
    private static byte[] member(ZipFile zip, String name) throws Exception {
        ZipEntry entry = zip.getEntry(name); if (entry == null) throw new IOException("文档内部格式不完整。");
        if (entry.getSize() > 4 * 1024 * 1024) throw new IOException("文档内容过大，请拆分后发送。");
        try (java.io.InputStream in = zip.getInputStream(entry); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] bytes = new byte[8192]; int n;
            while ((n = in.read(bytes)) != -1) { out.write(bytes, 0, n); if (out.size() > 4 * 1024 * 1024) throw new IOException("文档解压内容过大。"); }
            return out.toByteArray();
        }
    }
    private static void parse(byte[] bytes, DefaultHandler handler) throws Exception {
        if(new String(bytes,StandardCharsets.UTF_8).replace("\u0000","").contains("<!DOCTYPE"))throw new IOException("文档包含不支持的外部或实体声明。");
        SAXParserFactory factory = SAXParserFactory.newInstance(); factory.setNamespaceAware(true);
        for(String feature:new String[]{"http://xml.org/sax/features/external-general-entities","http://xml.org/sax/features/external-parameter-entities"})
            try{factory.setFeature(feature,false);}catch(org.xml.sax.SAXNotRecognizedException|org.xml.sax.SAXNotSupportedException ignored){}
        org.xml.sax.XMLReader reader = factory.newSAXParser().getXMLReader(); reader.setContentHandler(handler);
        reader.setEntityResolver((publicId, systemId) -> { throw new SAXException("不允许读取外部文档。"); });
        reader.parse(new InputSource(new ByteArrayInputStream(bytes)));
    }
    private static void append(StringBuilder out, String text) throws SAXException {
        out.append(text); if (out.length() > DirectInputs.MAX_TEXT) throw new SAXException("文档正文超过6万字，请拆分后发送。");
    }
    static String extract(File file, String extension) throws Exception {
        if (extension.equals("docx") || extension.equals("xlsx") || extension.equals("pptx")) {
            try (ZipFile zip = new ZipFile(file)) {
                if (zip.size() > 3000) throw new IOException("文档结构过于复杂，请转换为 PDF 或文本。");
                return extension.equals("xlsx") ? spreadsheet(zip) : extension.equals("pptx") ? slides(zip) : officeText(member(zip, "word/document.xml"));
            } catch (SAXException e) { throw new IOException("文档结构异常或正文超过6万字，请拆分或转换为文本。"); }
        }
        if (!extension.matches("txt|md|markdown|csv|tsv|json|xml|yaml|yml|html?|py|js|ts|tsx|jsx|java|kt|kts|css|scss|sql|c|cpp|h|hpp|cs|go|rs|sh|bat|cmd|ps1|toml|ini|log|tex|srt|ass"))
            throw new IOException("暂不支持此文档格式，请使用 PDF、DOCX、XLSX、PPTX 或文本。旧版 DOC/XLS 请先转换。");
        if (file.length() > 512 * 1024) throw new IOException("文本文件过大，请拆分后发送。");
        byte[] bytes = Files.readAllBytes(file.toPath()); String text;
        if (bytes.length >= 2 && bytes[0] == (byte)0xff && bytes[1] == (byte)0xfe) text = new String(bytes, 2, bytes.length - 2, StandardCharsets.UTF_16LE);
        else if (bytes.length >= 2 && bytes[0] == (byte)0xfe && bytes[1] == (byte)0xff) text = new String(bytes, 2, bytes.length - 2, StandardCharsets.UTF_16BE);
        else {
            try { text = StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString(); }
            catch (java.nio.charset.CharacterCodingException e) { text = Charset.forName("GB18030").newDecoder().onMalformedInput(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString(); }
        }
        if (text.startsWith("\ufeff")) text = text.substring(1);
        if (text.length() > DirectInputs.MAX_TEXT) throw new IOException("文档正文超过6万字，请拆分后发送。");
        if (text.codePoints().anyMatch(c -> c < 32 && c != '\t' && c != '\n' && c != '\r')) throw new IOException("此附件不是可读取的文本文件。");
        return text;
    }
    private static String officeText(byte[] bytes) throws Exception {
        StringBuilder result = new StringBuilder();
        parse(bytes, new DefaultHandler() {
            boolean text;
            @Override public void startElement(String uri, String local, String q, Attributes attrs) throws SAXException {
                if (local.equals("t")) text = true;
                if (local.equals("tab")) append(result, "\t");
                if (local.equals("br")) append(result, "\n");
            }
            @Override public void characters(char[] c, int start, int len) throws SAXException { if (text) append(result, new String(c, start, len)); }
            @Override public void endElement(String uri, String local, String q) throws SAXException {
                if (local.equals("t")) text = false;
                if (local.equals("p") || local.equals("tr")) append(result, "\n");
                if (local.equals("tc")) append(result, "\t");
            }
        }); return result.toString();
    }
    private static String slides(ZipFile zip) throws Exception {
        List<String> slides = new ArrayList<>(); java.util.Enumeration<? extends ZipEntry> members = zip.entries();
        while (members.hasMoreElements()) { String name = members.nextElement().getName(); if (name.matches("ppt/slides/slide[0-9]+\\.xml")) slides.add(name); }
        if (slides.isEmpty() || slides.size() > 80) throw new IOException("PPTX 最多支持80页，且需要包含正文。");
        slides.sort(java.util.Comparator.comparingInt(name -> Integer.parseInt(name.replaceAll("[^0-9]", ""))));
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < slides.size(); i++) append(result, "\n[幻灯片 " + (i + 1) + "]\n" + officeText(member(zip, slides.get(i))));
        return result.toString();
    }
    private static String spreadsheet(ZipFile zip) throws Exception {
        List<String> strings = new ArrayList<>();
        if (zip.getEntry("xl/sharedStrings.xml") != null) {
            parse(member(zip, "xl/sharedStrings.xml"), new DefaultHandler() {
                StringBuilder value = new StringBuilder(); boolean text; int total;
                @Override public void startElement(String u,String local,String q,Attributes a) { if (local.equals("si")) value.setLength(0); if (local.equals("t")) text=true; }
                @Override public void characters(char[] c,int start,int len) throws SAXException { if(text){append(value,new String(c,start,len));total+=len;if(total>200000)throw new SAXException("表格文本过多。");} }
                @Override public void endElement(String u,String local,String q) throws SAXException { if(local.equals("t"))text=false;if(local.equals("si")){strings.add(value.toString());if(strings.size()>30000)throw new SAXException("表格单元格过多。");} }
            });
        }
        List<String[]> sheets = new ArrayList<>(); Map<String,String> rels = new HashMap<>();
        parse(member(zip,"xl/workbook.xml"),new DefaultHandler(){
            @Override public void startElement(String u,String local,String q,Attributes a){if(local.equals("sheet"))sheets.add(new String[]{a.getValue("name"),a.getValue("http://schemas.openxmlformats.org/officeDocument/2006/relationships","id")});}
        });
        parse(member(zip,"xl/_rels/workbook.xml.rels"),new DefaultHandler(){
            @Override public void startElement(String u,String local,String q,Attributes a){if(local.equals("Relationship")&&!"External".equals(a.getValue("TargetMode")))rels.put(a.getValue("Id"),a.getValue("Target"));}
        });
        if(sheets.size()>20)throw new IOException("Excel 最多支持20张工作表，请拆分。");
        StringBuilder result = new StringBuilder();
        for(String[] sheet:sheets){
            String target=rels.get(sheet[1]);if(target==null)throw new IOException("工作表关系不完整。");
            String path=target.startsWith("/xl/")?target.substring(1):"xl/"+target;
            if(!path.matches("xl/worksheets/[A-Za-z0-9_.-]+\\.xml"))throw new IOException("工作表路径无效。");
            append(result,"\n[工作表："+sheet[0]+"]\n");
            parse(member(zip,path),new DefaultHandler(){
                String type="",address="",part="";StringBuilder value=new StringBuilder(),formula=new StringBuilder();
                @Override public void startElement(String u,String local,String q,Attributes a){
                    if(local.equals("c")){type=a.getValue("t");address=a.getValue("r");value.setLength(0);formula.setLength(0);}
                    if(local.equals("v")||local.equals("t")||local.equals("f"))part=local;
                }
                @Override public void characters(char[] c,int start,int len)throws SAXException{if(part.equals("f"))append(formula,new String(c,start,len));else if(!part.isEmpty())append(value,new String(c,start,len));}
                @Override public void endElement(String u,String local,String q)throws SAXException{
                    if(local.equals("v")||local.equals("t")||local.equals("f"))part="";
                    if(local.equals("c")){
                        String cell=value.toString();
                        if("s".equals(type)){try{cell=strings.get(Integer.parseInt(cell));}catch(Exception e){throw new SAXException("共享单元格索引无效。");}}
                        if("b".equals(type))cell=cell.equals("1")?"true":"false";
                        if(cell.isEmpty()&&formula.length()>0)cell="[公式未计算] ="+formula;
                        append(result,address+"="+cell+"\t");
                    }
                    if(local.equals("row"))append(result,"\n");
                }
            });
        } return result.toString();
    }
    private DocumentText() {}
}
