package com.codexpocket.app;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.widget.Toast;
import org.json.JSONObject;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Android document picker; pending metadata survives Activity/process recreation. */
final class LocalFileSave {
    static final int CREATE_DOCUMENT = 7220;
    private final Activity activity;
    private final Context app;
    private final SharedPreferences prefs;
    private final LocalArtifacts artifacts;
    private final int requestCode;
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private final Handler ui = new Handler(Looper.getMainLooper());
    private boolean saving;
    LocalFileSave(Activity activity, LocalArtifacts artifacts) {
        this(activity,artifacts,CREATE_DOCUMENT,"android_direct");
    }
    LocalFileSave(Activity activity, LocalArtifacts artifacts,int requestCode,String stateName) {
        this.activity = activity; this.app = activity.getApplicationContext(); this.artifacts = artifacts;
        this.requestCode=requestCode;prefs = activity.getSharedPreferences(stateName, Context.MODE_PRIVATE);
    }
    private void toast(String value) { ui.post(() -> Toast.makeText(app, value, Toast.LENGTH_SHORT).show()); }
    void save(JSONObject file) {
        if (saving || !prefs.getString("pending_file", "").isEmpty()) { toast("请先完成当前文件保存。"); return; }
        try {
            artifacts.resolve(file);
            if (!prefs.edit().putString("pending_file", file.toString()).commit()) throw new IOException();
            Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT); intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.setType(file.getString("mime")); intent.putExtra(Intent.EXTRA_TITLE, file.getString("name"));
            activity.startActivityForResult(intent, requestCode);
        } catch (Exception e) { prefs.edit().remove("pending_file").apply(); toast("无法打开保存位置或原文件已丢失，请重新生成。"); }
    }
    boolean result(int request, int result, Intent intent) {
        if (request != requestCode) return false;
        String raw = prefs.getString("pending_file", ""); prefs.edit().remove("pending_file").commit();
        if (result != Activity.RESULT_OK || intent == null || intent.getData() == null || raw.isEmpty()) return true;
        Uri destination = intent.getData(); saving = true; toast("正在保存文件…");
        io.execute(() -> {
            try {
                JSONObject descriptor = new JSONObject(raw); File file = artifacts.resolve(descriptor);
                try (InputStream in = new FileInputStream(file); OutputStream out = app.getContentResolver().openOutputStream(destination, "w")) {
                    if (out == null) throw new IOException(); byte[] buffer = new byte[16384]; int n;
                    while ((n = in.read(buffer)) != -1) out.write(buffer, 0, n);
                    out.flush();
                }
                toast("已保存：" + descriptor.getString("name"));
            } catch (Exception e) {
                try { app.getContentResolver().delete(destination, null, null); } catch (Exception ignored) {}
                toast("保存失败，请检查手机存储空间或重新选择位置。");
            } finally { ui.post(() -> saving = false); }
        }); return true;
    }
    void close() { io.shutdown(); }
}
