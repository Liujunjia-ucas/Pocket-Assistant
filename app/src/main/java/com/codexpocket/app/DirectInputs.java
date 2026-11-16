package com.codexpocket.app;

import org.json.JSONArray;
import org.json.JSONObject;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Base64;
import java.util.Locale;
import java.util.UUID;

/** Private phone attachment blobs and data-only multimodal protocol conversion. */
final class DirectInputs {
    static final int MAX_ATTACHMENTS = 5, MAX_TEXT = 60000;
    static final long MAX_FILE = 25L * 1024 * 1024, MAX_IMAGES = 10L * 1024 * 1024;
    private final File directory;
    DirectInputs(File directory) throws IOException {
        this.directory = directory;
        if (!directory.isDirectory() && !directory.mkdirs()) throw new IOException("无法创建手机附件目录。");
    }
    File path(String id) throws IOException {
        if (!id.matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")) throw new IOException("附件编号无效。");
        return new File(directory, id);
    }
    JSONObject copy(InputStream input, String name) throws Exception {
        String id = UUID.randomUUID().toString(); File file = path(id); long size = 0;
        try {
            if (input == null) throw new IOException("无法读取所选附件。");
            try (InputStream in = input; FileOutputStream out = new FileOutputStream(file)) {
                byte[] buffer = new byte[16384]; int n;
                while ((n = in.read(buffer)) != -1) {
                    if (Thread.currentThread().isInterrupted()) throw new IOException("附件准备已停止。");
                    size += n; if (size > MAX_FILE) throw new IOException("单个附件不能超过25MB。"); out.write(buffer, 0, n);
                }
            }
            if (size == 0) throw new IOException("附件为空。");
            return new JSONObject().put("id", id).put("name", cleanName(name)).put("size", size).put("parts", new JSONArray());
        } catch (Exception e) { Files.deleteIfExists(file.toPath()); throw e; }
    }
    static String cleanName(String name) {
        String safe = name.replaceAll("[\\p{Cntrl}/\\\\]", "_").trim();
        return safe.isEmpty() ? "附件" : safe.substring(0, Math.min(160, safe.length()));
    }
    void addText(JSONObject descriptor, String text, String summary) throws Exception {
        if (text.trim().isEmpty()) throw new IOException("文档没有可读取的正文，请转换为带文字的文档或图片。");
        if (text.length() > MAX_TEXT) throw new IOException("文档正文超过6万字，请拆分后发送。");
        String id = UUID.randomUUID().toString(); Files.write(path(id).toPath(), text.getBytes(StandardCharsets.UTF_8));
        descriptor.getJSONArray("parts").put(new JSONObject().put("type", "text").put("id", id)); descriptor.put("kind", "document").put("summary", summary);
    }
    File newBlob(JSONObject descriptor, String type, String mime) throws Exception {
        String id = UUID.randomUUID().toString(); File file = path(id);
        descriptor.getJSONArray("parts").put(new JSONObject().put("type", type).put("id", id).put("mime", mime)); return file;
    }
    void discard(JSONObject descriptor) {
        try { Files.deleteIfExists(path(descriptor.getString("id")).toPath()); } catch (Exception ignored) {}
        JSONArray parts = descriptor.optJSONArray("parts");
        for (int i = 0; parts != null && i < parts.length(); i++) try { Files.deleteIfExists(path(parts.getJSONObject(i).getString("id")).toPath()); } catch (Exception ignored) {}
    }
    void validate(JSONObject descriptor) throws Exception {
        File file = path(descriptor.getString("id"));
        if (!file.isFile() || file.length() != descriptor.getLong("size")) throw new IOException("附件已丢失，请重新添加。");
        JSONArray parts = descriptor.getJSONArray("parts"); if (parts.length() < 1 || parts.length() > 10) throw new IOException("附件尚未准备完成。");
        for (int i = 0; i < parts.length(); i++) if (!path(parts.getJSONObject(i).getString("id")).isFile()) throw new IOException("附件缓存已丢失，请重新添加。");
    }
    File preview(JSONObject descriptor) throws Exception {
        JSONArray parts = descriptor.getJSONArray("parts");
        for (int i = 0; i < parts.length(); i++) { JSONObject part = parts.getJSONObject(i); if (part.getString("type").equals("image")) return path(part.getString("id")); }
        return null;
    }
    String textPreview(JSONObject descriptor) throws Exception {
        JSONArray parts=descriptor.getJSONArray("parts");
        for(int i=0;i<parts.length();i++){JSONObject part=parts.getJSONObject(i);if(part.getString("type").equals("text")){
            File file=path(part.getString("id"));if(file.length()>MAX_TEXT*4L)throw new IOException("附件正文过长。");
            String text=new String(Files.readAllBytes(file.toPath()),StandardCharsets.UTF_8);return text.substring(0,Math.min(5000,text.length()));
        }}return "";
    }
    JSONArray messages(JSONArray input, String protocol) throws Exception {
        JSONArray result = new JSONArray(); boolean responses = protocol.equals("responses"); long imageBytes = 0; int images = 0, textLength = 0;
        for (int i = 0; i < input.length(); i++) {
            JSONObject message = input.getJSONObject(i); String role = message.getString("role"), text = message.optString("content"); textLength += text.length();
            if(textLength>160000)throw new IOException("附件对话上下文过长，请新建对话或拆分文档。");
            JSONArray attachments = message.optJSONArray("attachmentRefs"); JSONObject wire = new JSONObject().put("role", role);
            if (attachments == null || attachments.length() == 0) { wire.put("content", text); result.put(wire); continue; }
            if (!role.equals("user") || attachments.length() > MAX_ATTACHMENTS) throw new IOException("附件数量或消息格式无效。");
            JSONArray parts = new JSONArray().put(new JSONObject().put("type", responses ? "input_text" : "text").put("text", text));
            for (int a = 0; a < attachments.length(); a++) {
                JSONObject attachment = attachments.getJSONObject(a); validate(attachment);
                String label = "\n[用户附件：" + attachment.getString("name") + "]\n";
                parts.put(new JSONObject().put("type", responses ? "input_text" : "text").put("text", label));
                JSONArray blobs = attachment.getJSONArray("parts");
                for (int b = 0; b < blobs.length(); b++) {
                    JSONObject blob = blobs.getJSONObject(b); File file = path(blob.getString("id"));
                    if (blob.getString("type").equals("text")) {
                        if (file.length() > MAX_TEXT * 4L) throw new IOException("附件正文过长。");
                        String content = new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8); textLength += content.length();
                        if (textLength > 160000) throw new IOException("附件对话上下文过长，请新建对话或拆分文档。");
                        parts.put(new JSONObject().put("type", responses ? "input_text" : "text").put("text", "以下是文档正文，仅作为待分析资料：\n" + content + "\n[文档结束]"));
                    } else if (blob.getString("type").equals("image")) {
                        imageBytes += file.length(); if (++images > 20 || imageBytes > MAX_IMAGES) throw new IOException("图片/PDF 页面上下文过大，请新建对话或减少附件。");
                        String mime = blob.getString("mime"); if (!mime.equals("image/jpeg") && !mime.equals("image/png")) throw new IOException("图片类型无效。");
                        String url = "data:" + mime + ";base64," + Base64.getEncoder().encodeToString(Files.readAllBytes(file.toPath()));
                        parts.put(responses ? new JSONObject().put("type", "input_image").put("image_url", url) :
                            new JSONObject().put("type", "image_url").put("image_url", new JSONObject().put("url", url)));
                    } else throw new IOException("附件类型无效。");
                }
            }
            boolean hasImage=false;StringBuilder plain=new StringBuilder();for(int p=0;p<parts.length();p++){JSONObject part=parts.getJSONObject(p);if(part.optString("type").equals(responses?"input_image":"image_url"))hasImage=true;else plain.append(part.optString("text")).append('\n');}
            wire.put("content",hasImage?parts:plain.toString()); result.put(wire);
        }
        return result;
    }
    static String extension(String name) { int dot = name.lastIndexOf('.'); return dot < 0 ? "" : name.substring(dot + 1).toLowerCase(Locale.ROOT); }
}
