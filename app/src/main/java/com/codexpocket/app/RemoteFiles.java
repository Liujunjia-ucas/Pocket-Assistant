package com.codexpocket.app;

import android.Manifest;
import android.app.Activity;
import android.app.Dialog;
import android.content.ContentValues;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.Bundle;
import android.os.Looper;
import android.provider.MediaStore;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.widget.*;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Files use authenticated native requests; no tokens or local paths in WebViews. */
final class RemoteFiles {
    static final int CREATE_DOCUMENT = 7201, GALLERY_PERMISSION = 7202;
    private final Activity activity;
    private final Handler ui = new Handler(Looper.getMainLooper());
    private final ExecutorService io = Executors.newFixedThreadPool(2);
    private final ThemePalette colors;
    private JSONObject pendingDocument, pendingGallery;
    private Api documentClient, galleryClient;
    private final File cache;
    private volatile boolean closed;
    RemoteFiles(Activity activity, ThemePalette colors) {
        this.activity = activity; this.colors = colors; cache = new File(activity.getCacheDir(), "remote-files"); cache.mkdirs();
    }
    void close() { closed = true; io.shutdownNow(); }
    void saveState(Bundle state) { if(pendingDocument!=null){state.putString("saveDocument",pendingDocument.toString());state.putString("saveDocumentBase",documentClient.base);} }
    void restoreState(Bundle state, Api client) { if(state==null||!client.base.equals(state.getString("saveDocumentBase")))return;try{pendingDocument=new JSONObject(state.getString("saveDocument"));documentClient=client;}catch(Exception ignored){} }
    private int dp(int value) { return (int)(value * activity.getResources().getDisplayMetrics().density + .5f); }
    private LinearLayout column() { LinearLayout body = new LinearLayout(activity); body.setOrientation(LinearLayout.VERTICAL); return body; }
    private TextView text(String value, int size, int color) { TextView view = new TextView(activity); view.setText(value); view.setTextSize(size); view.setTextColor(color); view.setPadding(dp(4), dp(6), dp(4), dp(6)); return view; }
    private Button button(String value) { Button button = new Button(activity); button.setAllCaps(false); button.setText(value); button.setTextColor(colors.text);android.graphics.drawable.GradientDrawable rounded=new android.graphics.drawable.GradientDrawable();rounded.setColor(colors.attachment);rounded.setCornerRadius(dp(16));button.setBackground(rounded);return button; }
    private void toast(String value) { if(!closed)Toast.makeText(activity, value, Toast.LENGTH_LONG).show(); }
    private void post(Runnable action) { ui.post(() -> { if (!closed && !activity.isDestroyed()) action.run(); }); }
    private String name(JSONObject file) { return file.optString("name", "download").replaceAll("[\\\\/\\p{Cntrl}]", "_"); }
    private String size(JSONObject file) { long bytes = file.optLong("size"); return bytes >= 1024 * 1024 ? String.format(Locale.ROOT, "%.1f MB", bytes / (1024.0 * 1024)) : Math.max(1, bytes / 1024) + " KB"; }
    View cards(JSONArray files, Api client) {
        LinearLayout list = column();
        for (int i = 0; i < files.length() && i < 8; i++) {
            JSONObject file = files.optJSONObject(i); if (file == null) continue;
            LinearLayout card = column(); card.setPadding(dp(10), dp(10), dp(10), dp(10));
            android.graphics.drawable.GradientDrawable background = new android.graphics.drawable.GradientDrawable(); background.setColor(colors.panel); background.setCornerRadius(dp(14)); background.setStroke(dp(1), colors.border); card.setBackground(background);
            if (file.optString("kind").equals("image")) {
                FrameLayout frame = new FrameLayout(activity);RoundedDialogBuilder.clip(frame,18);
                TextView loading = text("正在加载图片…", 12, colors.muted); loading.setGravity(Gravity.CENTER); frame.addView(loading, new FrameLayout.LayoutParams(-1, -1));
                ImageView image = new ImageView(activity); image.setScaleType(ImageView.ScaleType.FIT_CENTER); image.setContentDescription(name(file) + "，点击放大"); frame.addView(image, new FrameLayout.LayoutParams(-1, -1)); card.addView(frame, new LinearLayout.LayoutParams(-1, dp(220)));
                Runnable load = () -> loadThumbnail(file, client, image, loading); image.setOnClickListener(v -> preview(file, client)); loading.setOnClickListener(v -> load.run()); load.run();
            }
            card.addView(text(name(file), 13, colors.text)); card.addView(text(size(file), 11, colors.muted));
            LinearLayout actions = new LinearLayout(activity); actions.setOrientation(LinearLayout.HORIZONTAL);
            if (file.optString("kind").equals("image")) {
                Button open = button("放大预览"); actions.addView(open, new LinearLayout.LayoutParams(0, dp(48), 1)); open.setOnClickListener(v -> preview(file, client));
                Button gallery = button("保存到相册"); actions.addView(gallery, new LinearLayout.LayoutParams(0, dp(48), 1)); gallery.setOnClickListener(v -> saveGallery(file, client));
            } else {
                Button download = button("下载到手机"); actions.addView(download, new LinearLayout.LayoutParams(-1, dp(48))); download.setOnClickListener(v -> saveDocument(file, client));
            }
            card.addView(actions); LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2); params.topMargin = dp(10); list.addView(card, params);
        }
        return list;
    }
    private File cached(JSONObject file, Api client) throws Exception {
        String id = file.optString("id"); if (!id.matches("[0-9a-f-]{36}")) throw new IOException("文件编号无效");
        String source = file.optString("source").equals("generated") ? "generated" : "upload";
        byte[] digest = MessageDigest.getInstance("SHA-256").digest((client.base + "|" + source + "|" + id).getBytes(StandardCharsets.UTF_8));
        StringBuilder key = new StringBuilder(); for (byte b : digest) key.append(String.format(Locale.ROOT, "%02x", b));
        File target = new File(cache, key.toString());
        if (target.isFile() && target.length() == file.optLong("size", -1)) return target;
        File part = new File(cache, key + "." + UUID.randomUUID() + ".part");
        try {
            if (source.equals("generated")) client.downloadArtifact(id, part); else client.download(id, part);
            if (file.has("size") && part.length() != file.optLong("size")) throw new IOException("文件传输未完成，请重试");
            if (!part.renameTo(target)) { if (!target.isFile() || target.length() != part.length()) throw new IOException("无法保存文件缓存"); }
            return target;
        } finally { part.delete(); }
    }
    private Bitmap bitmap(File file, int limit) throws IOException {
        BitmapFactory.Options options = new BitmapFactory.Options(); options.inJustDecodeBounds = true; BitmapFactory.decodeFile(file.getPath(), options);
        if (options.outWidth < 1 || options.outHeight < 1) throw new IOException("此图片格式无法预览，可下载文件查看");
        options.inSampleSize = 1; while (options.outWidth / options.inSampleSize > limit || options.outHeight / options.inSampleSize > limit) options.inSampleSize *= 2;
        options.inJustDecodeBounds = false; Bitmap value = BitmapFactory.decodeFile(file.getPath(), options); if (value == null) throw new IOException("无法解码图片"); return value;
    }
    private void loadThumbnail(JSONObject file, Api client, ImageView view, TextView loading) {
        if (closed) return;
        io.execute(() -> { try { Bitmap image = bitmap(cached(file, client), 640); post(() -> { view.setImageBitmap(image); view.setVisibility(View.VISIBLE); loading.setVisibility(View.GONE); }); }
            catch (Exception | OutOfMemoryError e) { post(() -> { loading.setText("图片加载失败 · 点击重试"); view.setVisibility(View.GONE); }); } });
    }
    private void preview(JSONObject file, Api client) {
        toast("正在打开图片…");
        io.execute(() -> { try { Bitmap image = bitmap(cached(file, client), 2048); post(() -> {
            Dialog dialog = new Dialog(activity); dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
            if(dialog.getWindow()!=null)dialog.getWindow().addFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE);
            LinearLayout body = column(); body.setBackgroundColor(colors.bg); body.setPadding(dp(12), dp(12), dp(12), dp(12));
            LinearLayout top = new LinearLayout(activity); TextView title = text(name(file), 13, colors.text); title.setMaxLines(2); top.addView(title, new LinearLayout.LayoutParams(0, -2, 1));
            Button close = button("关闭"); top.addView(close); close.setOnClickListener(v -> dialog.dismiss());if(activity instanceof MainActivity){IconView gear=new IconView(activity,"settings",colors.text,"设置");top.addView(gear,new LinearLayout.LayoutParams(dp(44),dp(44)));gear.setOnClickListener(v->{dialog.dismiss();((MainActivity)activity).showUnifiedSettings();});} body.addView(top);
            ZoomImageView photo = new ZoomImageView(activity, image); body.addView(photo, new LinearLayout.LayoutParams(-1, 0, 1));
            body.addView(text("双指缩放 · 拖动查看 · 双击复位", 11, colors.muted));
            LinearLayout actions = new LinearLayout(activity); Button gallery = button("保存到相册"), download = button("另存为文件");
            actions.addView(gallery, new LinearLayout.LayoutParams(0, dp(50), 1)); actions.addView(download, new LinearLayout.LayoutParams(0, dp(50), 1)); body.addView(actions);
            gallery.setOnClickListener(v -> saveGallery(file, client)); download.setOnClickListener(v -> saveDocument(file, client));
            dialog.setContentView(body); dialog.show(); if(dialog.getWindow()!=null){dialog.getWindow().setLayout(-1,-1);dialog.getWindow().setStatusBarColor(colors.bg);dialog.getWindow().setNavigationBarColor(colors.bg);}
        }); } catch (Exception | OutOfMemoryError e) { post(() -> toast("无法预览图片，请重试或另存文件")); } });
    }
    private void saveDocument(JSONObject file, Api client) {
        if (pendingDocument != null) { toast("请先完成当前文件保存"); return; }
        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT); intent.addCategory(Intent.CATEGORY_OPENABLE); intent.setType(file.optString("mime", "application/octet-stream")); intent.putExtra(Intent.EXTRA_TITLE, name(file));
        pendingDocument = file; documentClient = client;
        try { activity.startActivityForResult(intent, CREATE_DOCUMENT); } catch (Exception e) { pendingDocument = null; toast("无法打开系统文件保存器"); }
    }
    boolean onActivityResult(int code, int result, Intent data) {
        if (code != CREATE_DOCUMENT) return false;
        JSONObject file = pendingDocument; Api client = documentClient; pendingDocument = null; documentClient = null;
        if (result != Activity.RESULT_OK || data == null || data.getData() == null || file == null || client == null) return true;
        Uri destination = data.getData(); toast("正在保存文件…");
        io.execute(() -> { try {
            File source = cached(file, client);
            try (OutputStream output = activity.getContentResolver().openOutputStream(destination, "w")) { if(output==null)throw new IOException("无法写入保存位置"); copy(source, output); }
            post(() -> toast("已保存：" + name(file)));
        } catch (Exception e) { try { activity.getContentResolver().delete(destination, null, null); } catch (Exception ignored) {} post(() -> toast("保存失败，请检查电脑连接、可用空间或重新选择位置")); } });
        return true;
    }
    private void saveGallery(JSONObject file, Api client) {
        if (Build.VERSION.SDK_INT <= 28 && activity.checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            pendingGallery = file; galleryClient = client; activity.requestPermissions(new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, GALLERY_PERMISSION); return;
        }
        toast("正在保存到相册…");
        io.execute(() -> {
            Uri destination = null; File legacy = null;
            try {
                File source = cached(file, client);
                ContentValues values = new ContentValues(); values.put(MediaStore.Images.Media.DISPLAY_NAME, name(file)); values.put(MediaStore.Images.Media.MIME_TYPE, file.optString("mime", "image/png"));
                if (Build.VERSION.SDK_INT >= 29) {
                    values.put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/Pocket Assistant"); values.put(MediaStore.Images.Media.IS_PENDING, 1);
                    destination = activity.getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values); if(destination==null)throw new IOException("相册不可用");
                    try(OutputStream output=activity.getContentResolver().openOutputStream(destination)){if(output==null)throw new IOException("无法写入相册");copy(source,output);}
                    ContentValues complete = new ContentValues(); complete.put(MediaStore.Images.Media.IS_PENDING, 0); activity.getContentResolver().update(destination, complete, null, null);
                } else {
                    File directory = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), "Pocket Assistant"); if(!directory.isDirectory()&&!directory.mkdirs())throw new IOException("无法创建相册目录");
                    legacy = new File(directory, UUID.randomUUID().toString().substring(0,8) + "_" + name(file)); try(OutputStream output=new FileOutputStream(legacy)){copy(source,output);}
                    android.media.MediaScannerConnection.scanFile(activity, new String[]{legacy.getAbsolutePath()}, new String[]{file.optString("mime")}, null);
                }
                post(() -> toast("已保存到相册 · Pictures/Pocket Assistant"));
            } catch (Exception e) { if(destination!=null)try{activity.getContentResolver().delete(destination,null,null);}catch(Exception ignored){} if(legacy!=null)legacy.delete(); post(() -> toast("保存失败，请检查电脑连接和手机剩余空间")); }
        });
    }
    boolean onPermissionsResult(int code, int[] grants) {
        if(code!=GALLERY_PERMISSION)return false;
        JSONObject file=pendingGallery;Api client=galleryClient;pendingGallery=null;galleryClient=null;
        if(grants.length>0&&grants[0]==PackageManager.PERMISSION_GRANTED&&file!=null&&client!=null)saveGallery(file,client);
        else {toast("未获得相册权限，可用文件保存器保存图片");if(file!=null&&client!=null)saveDocument(file,client);}
        return true;
    }
    private void copy(File source, OutputStream output) throws IOException { try(InputStream input=new FileInputStream(source)){byte[] buffer=new byte[16384];int count;while((count=input.read(buffer))!=-1)output.write(buffer,0,count);} }
}
