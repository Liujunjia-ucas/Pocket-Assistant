package com.codexpocket.app;

import org.json.JSONObject;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.net.URLEncoder;

final class Api {
    final String base, token;
    volatile HttpURLConnection polling;
    Api(String base, String token) { this.base = BridgeEndpoint.normalize(base); this.token = token; }
    JSONObject get(String path) throws Exception { return request("GET", path, null, false); }
    JSONObject post(String path, JSONObject body) throws Exception { return request("POST", path, body, false); }
    JSONObject poll(String path) throws Exception { return request("GET", path, null, true); }
    void cancelPoll() { HttpURLConnection connection = polling; if (connection != null) connection.disconnect(); }
    JSONObject upload(Attachment attachment) throws Exception {
        String path = "/api/uploads?name=" + URLEncoder.encode(attachment.name, "UTF-8") + "&kind=" + attachment.kind;
        HttpURLConnection c = (HttpURLConnection) new URL(base + path).openConnection();
        c.setRequestMethod("POST"); c.setConnectTimeout(9000); c.setReadTimeout(120000); c.setInstanceFollowRedirects(false);
        c.setRequestProperty("Authorization", "Bearer " + token); c.setRequestProperty("Content-Type", "application/octet-stream");
        c.setDoOutput(true); c.setFixedLengthStreamingMode(attachment.file.length());
        try {
            try (FileInputStream input = new FileInputStream(attachment.file); java.io.OutputStream output = c.getOutputStream()) { byte[] bytes = new byte[16384]; int n; while ((n = input.read(bytes)) != -1) output.write(bytes,0,n); }
            int code = c.getResponseCode(); InputStream input = code >= 400 ? c.getErrorStream() : c.getInputStream();
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            if(input != null) try(InputStream stream = input) { byte[] b = new byte[4096]; int n; while((n=stream.read(b))!=-1){bytes.write(b,0,n);if(bytes.size()>1024*1024)throw new Exception("上传响应过大");} }
            JSONObject result = new JSONObject(bytes.toString("UTF-8")); if(code != 200) throw new Exception(result.optString("error", "上传失败")); return result;
        } finally { c.disconnect(); }
    }
    void download(String id, File file) throws Exception {
        downloadResource("/api/uploads/" + URLEncoder.encode(id,"UTF-8") + "/content", file, Attachment.MAX);
    }
    void downloadArtifact(String id, File file) throws Exception {
        downloadResource("/api/artifacts/" + URLEncoder.encode(id,"UTF-8") + "/content", file, 100L * 1024 * 1024);
    }
    private void downloadResource(String path, File file, long maximum) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(base + path).openConnection();
        c.setConnectTimeout(9000); c.setReadTimeout(120000); c.setInstanceFollowRedirects(false); c.setRequestProperty("Authorization", "Bearer " + token);
        boolean success = false;
        try {
            if(c.getResponseCode()!=200) throw new Exception("无法读取文件，请确认电脑在线且文件仍然存在");
            long expected = c.getContentLengthLong(); if(expected>maximum)throw new Exception("文件超过下载大小限制");
            try(InputStream input=c.getInputStream(); FileOutputStream output=new FileOutputStream(file)) { byte[] b=new byte[16384];int n;long total=0;while((n=input.read(b))!=-1){total+=n;if(total>maximum)throw new Exception("文件超过下载大小限制");output.write(b,0,n);}if(expected>=0&&total!=expected)throw new Exception("文件传输未完成，请重新下载"); }
            success = true;
        } finally { c.disconnect(); if(!success)file.delete(); }
    }
    private JSONObject request(String method, String path, JSONObject data, boolean isPoll) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL(base + path).openConnection();
        if (isPoll) polling = connection;
        connection.setConnectTimeout(9000); connection.setReadTimeout(isPoll ? 27000 : 70000);
        connection.setInstanceFollowRedirects(false); connection.setRequestMethod(method);
        connection.setRequestProperty("Authorization", "Bearer " + token);
        connection.setRequestProperty("Accept", "application/json");
        try {
            if (data != null) {
                connection.setDoOutput(true); connection.setRequestProperty("Content-Type", "application/json; charset=utf-8");
                byte[] bytes = data.toString().getBytes(StandardCharsets.UTF_8); connection.setFixedLengthStreamingMode(bytes.length);
                try (java.io.OutputStream output = connection.getOutputStream()) { output.write(bytes); }
            }
            int code = connection.getResponseCode();
            InputStream input = code >= 400 ? connection.getErrorStream() : connection.getInputStream();
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            if (input != null) try (InputStream stream = input) { byte[] buffer = new byte[8192]; int n; while ((n = stream.read(buffer)) != -1) { bytes.write(buffer, 0, n); if (bytes.size() > 12 * 1024 * 1024) throw new Exception("会话过大，请新建会话"); } }
            JSONObject result;
            try { result = new JSONObject(bytes.toString("UTF-8")); } catch (Exception e) { throw new Exception("电脑未返回有效数据，请检查地址和服务版本（HTTP " + code + "）"); }
            if (code < 200 || code >= 300) throw new Exception(result.optString("error", "请求失败（HTTP " + code + "）"));
            return result;
        } finally { connection.disconnect(); if (isPoll && polling == connection) polling = null; }
    }
}
