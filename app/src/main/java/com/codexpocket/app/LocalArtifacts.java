package com.codexpocket.app;

import org.json.JSONArray;
import org.json.JSONObject;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.UUID;

/** Durable app-private files, only published in a chat after a complete local write. */
final class LocalArtifacts {
    interface PdfWriter { void write(File file, String content) throws Exception; }
    private final File directory;
    LocalArtifacts(File directory) throws Exception {
        this.directory = directory;
        if (!directory.isDirectory() && !directory.mkdirs()) throw new IOException("无法创建手机文件目录。");
    }
    JSONArray generate(List<LocalFileSpec> specs, PdfWriter pdf) throws Exception {
        JSONArray result = new JSONArray(); long total = 0;
        try {
            for (LocalFileSpec spec : specs) {
                if (Thread.currentThread().isInterrupted()) throw new IOException("文件生成已停止。");
                String id = UUID.randomUUID().toString(); File target = path(id), part = new File(directory, id + ".part");
                try {
                    if (spec.extension.equals("pdf")) pdf.write(part, spec.content);
                    else LocalDocuments.write(part, spec);
                    total += part.length();
                    if (part.length() == 0 || total > 12L * 1024 * 1024) throw new IOException("生成文件为空或总大小超过12MB。");
                    if (Thread.currentThread().isInterrupted()) throw new IOException("文件生成已停止。");
                    Files.move(part.toPath(), target.toPath(), StandardCopyOption.ATOMIC_MOVE);
                    result.put(new JSONObject().put("id", id).put("name", spec.name).put("mime", mime(spec.extension)).put("size", target.length()));
                } finally { Files.deleteIfExists(part.toPath()); }
            }
            return result;
        } catch (Exception e) { discard(result); throw e; }
    }
    private File path(String id) throws Exception {
        if (!id.matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")) throw new IOException("文件编号无效。");
        return new File(directory, id);
    }
    File resolve(JSONObject descriptor) throws Exception {
        File file = path(descriptor.getString("id"));
        if (!file.isFile() || file.length() != descriptor.getLong("size")) throw new IOException("手机文件已丢失，请重新生成。");
        return file;
    }
    void discard(JSONArray descriptors) {
        for (int i = 0; i < descriptors.length(); i++) try { Files.deleteIfExists(path(descriptors.getJSONObject(i).getString("id")).toPath()); } catch (Exception ignored) {}
    }
    static String mime(String extension) {
        switch (extension) {
            case "docx": return "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
            case "xlsx": return "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
            case "pdf": return "application/pdf";
            case "zip": return "application/zip";
            case "csv": return "text/csv";
            case "json": return "application/json";
            case "html": case "htm": return "text/html";
            case "svg": return "image/svg+xml";
            case "xml": return "application/xml";
            default: return "text/plain";
        }
    }
}
