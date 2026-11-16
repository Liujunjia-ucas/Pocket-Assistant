package com.codexpocket.app;

import android.content.Context;
import android.net.Uri;
import java.io.*;
import java.util.UUID;
import org.json.JSONObject;

final class Attachment {
    static final long MAX = 25L * 1024 * 1024;
    final String localId, name, kind;
    File file; long size; boolean preparing = true;
    String uploadId = "", uploadBase = "";
    Attachment(String name, String kind) { this.localId = UUID.randomUUID().toString(); this.name = name; this.kind = kind; }
    private Attachment(JSONObject value, Context context) throws Exception {
        localId = value.getString("localId"); name = value.getString("name"); kind = value.getString("kind");
        file = new File(new File(context.getFilesDir(), "attachments"), localId);
        size = file.length(); uploadId = value.optString("uploadId"); uploadBase = value.optString("uploadBase"); preparing = false;
    }
    static Attachment restore(JSONObject value, Context context) throws Exception { Attachment a = new Attachment(value, context); if (!a.file.isFile()) throw new IOException("附件缓存已失效"); return a; }
    void copy(Context context, Uri uri) throws Exception {
        File directory = new File(context.getFilesDir(), "attachments"); if (!directory.exists() && !directory.mkdirs()) throw new IOException("无法保存附件");
        file = new File(directory, localId);
        try (InputStream input = context.getContentResolver().openInputStream(uri); OutputStream output = new FileOutputStream(file)) {
            if (input == null) throw new IOException("无法读取此文件");
            byte[] buffer = new byte[16384]; int n;
            while ((n = input.read(buffer)) != -1) { size += n; if (size > MAX) throw new IOException("单个附件不能超过 25 MB"); output.write(buffer, 0, n); }
        } catch (Exception e) { file.delete(); throw e; }
        preparing = false;
    }
    JSONObject json() throws Exception { return new JSONObject().put("localId", localId).put("name", name).put("kind", kind).put("uploadId", uploadId).put("uploadBase", uploadBase); }
    String sizeText() { return size >= 1024*1024 ? String.format(java.util.Locale.ROOT,"%.1f MB",size/(1024.0*1024)) : Math.max(1, size/1024) + " KB"; }
}
