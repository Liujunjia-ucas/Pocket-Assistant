package com.codexpocket.app;

import org.json.JSONArray;
import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Phone-only HTTPS transport. Does not reference the computer bridge or pairing token. */
final class DirectApi {
    interface Update { void text(String completeText); }
    static final class Failure extends IOException {
        final int status;
        Failure(int status, String message) { super(message); this.status = status; }
    }
    private final String base, key;
    private final int readTimeout;
    private volatile HttpURLConnection current;
    private volatile boolean cancelled;
    DirectApi(String base, String key) {this(base,key,180000);}
    DirectApi(String base,String key,int readTimeout){this.base=base;this.key=key;this.readTimeout=readTimeout;}
    void cancel() { cancelled = true; HttpURLConnection c = current; if (c != null) c.disconnect(); }
    private void check() throws IOException { if (cancelled || Thread.currentThread().isInterrupted()) throw new IOException("请求已停止"); }
    private HttpURLConnection open(String path, JSONObject body) throws Exception {
        check();
        if (key.trim().isEmpty() || key.matches(".*\\s.*")) throw new IOException("API Key 为空或含有空白字符，请重新粘贴。");
        HttpURLConnection c = (HttpURLConnection)new URL(base + path).openConnection(); current = c;
        check();
        c.setConnectTimeout(15000); c.setReadTimeout(readTimeout); c.setInstanceFollowRedirects(false);
        c.setRequestProperty("Authorization", "Bearer " + key);
        c.setRequestProperty("Accept", body == null || path.startsWith("/tools/") ? "application/json" : "text/event-stream, application/json");
        if (body != null) {
            c.setRequestMethod("POST"); c.setDoOutput(true); c.setRequestProperty("Content-Type", "application/json; charset=utf-8");
            byte[] bytes = body.toString().getBytes(StandardCharsets.UTF_8); c.setFixedLengthStreamingMode(bytes.length);
            try (java.io.OutputStream out = c.getOutputStream()) { check(); out.write(bytes); }
        }
        return c;
    }
    private String read(InputStream input, int max) throws Exception {
        if (input == null) return "";
        try (InputStream in = input; ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192]; int n;
            while ((n = in.read(buffer)) != -1) { check(); out.write(buffer, 0, n); if (out.size() > max) throw new IOException("平台响应过大，请新建对话。"); }
            return out.toString("UTF-8");
        }
    }
    private void requireSuccess(HttpURLConnection c) throws Exception {
        int status = c.getResponseCode(); if (status >= 200 && status < 300) return;
        // Do not surface upstream response bodies: some gateways echo credentials or submitted text.
        String message = status == 401 || status == 403 ? "密钥无效、权限不足或该模型不在令牌分组中。" :
            status == 402 ? "平台余额不足，请检查账户额度。" :
            status == 404 ? "接口或模型不存在，请检查 API 地址、模型 ID 和接口格式。" :
            status == 429 ? "平台限流或额度不足，请稍后重试。" :
            status >= 500 ? "模型平台暂时不可用，请稍后重试。" :
            status >= 300 && status < 400 ? "API 地址发生跳转，请填写平台提供的最终 HTTPS 接口地址。" :
            "平台拒绝了请求，请检查模型和接口格式。";
        throw new Failure(status, message + "（HTTP " + status + "）");
    }
    /** The same cancellable transport as chat, with no redirect or automatic retries. */
    JSONObject web(String path, JSONObject body) throws Exception {
        if (!path.equals("/tools/search") && !path.equals("/tools/fetch")) throw new IOException("网页工具地址无效。");
        HttpURLConnection c = null;
        try {
            c = open(path, body); c.setReadTimeout(45000); requireSuccess(c);
            JSONObject result = new JSONObject(read(c.getInputStream(), 4 * 1024 * 1024)); check();
            if (result.has("error") && !result.isNull("error")) throw new IOException("网页服务返回错误，请检查搜索权限与账户额度。");
            return result;
        } finally { if (c != null) c.disconnect(); else if (current != null) current.disconnect(); current = null; }
    }
    List<String> models() throws Exception {
        HttpURLConnection c = null;
        try {
            c = open("/models", null); requireSuccess(c);
            JSONArray data = new JSONObject(read(c.getInputStream(), 2 * 1024 * 1024)).optJSONArray("data");
            if (data == null) throw new IOException("平台未返回模型列表，可手动填写模型 ID。");
            List<String> result = new ArrayList<>();
            for (int i = 0; i < data.length() && result.size() < 2000; i++) {
                JSONObject item = data.optJSONObject(i); String id = item == null ? "" : item.optString("id");
                if (!id.isEmpty() && id.length() <= 200 && !result.contains(id)) result.add(id);
            }
            Collections.sort(result); if (result.isEmpty()) throw new IOException("模型列表为空，请检查令牌分组或手动填写模型 ID。");
            return result;
        } finally { if (c != null) c.disconnect(); else if (current != null) current.disconnect(); current = null; }
    }
    String chat(String model, String protocol, JSONArray messages, Update update) throws Exception {
        return chat(model,protocol,messages,0,update);
    }
    String chat(String model, String protocol, JSONArray messages, int maxTokens, Update update) throws Exception {
        boolean responses = "responses".equals(protocol);
        JSONObject body = new JSONObject().put("model", model).put("stream", true);
        if (responses) body.put("input", messages).put("store", false);
        else body.put("messages", messages);
        if(maxTokens>0)body.put(responses?"max_output_tokens":"max_tokens",maxTokens);
        HttpURLConnection c = null;
        try {
            c = open(responses ? "/responses" : "/chat/completions", body); requireSuccess(c);
            String type = c.getContentType();
            if (type != null && type.toLowerCase(java.util.Locale.ROOT).contains("application/json")) {
                String text = complete(new JSONObject(read(c.getInputStream(), 4 * 1024 * 1024)), responses);
                check(); if (text.trim().isEmpty()) throw new IOException("模型未返回正文，请检查模型 ID 与接口格式。");
                update.text(text); return text;
            }
            Stream stream = new Stream(responses, update);
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(c.getInputStream(), StandardCharsets.UTF_8))) {
                StringBuilder event = new StringBuilder(); String line;
                while ((line = reader.readLine()) != null) {
                    check(); if (line.length() > 1024 * 1024) throw new IOException("平台流式事件过大。");
                    if (line.isEmpty()) {
                        if (event.length() > 0) { stream.event(event.toString()); event.setLength(0); if (stream.done) break; }
                    } else if (line.startsWith("data:")) {
                        if (event.length() > 0) event.append('\n'); event.append(line.substring(5).replaceFirst("^ ", ""));
                        if (event.length() > 2 * 1024 * 1024) throw new IOException("平台流式事件过大。");
                    }
                }
                if (!stream.done && event.length() > 0) stream.event(event.toString());
            }
            check(); if (!stream.done) throw new IOException("连接在回复完成前中断，已保留收到的内容。");
            if (stream.text.toString().trim().isEmpty()) throw new IOException("模型未返回正文，请检查模型 ID 与接口格式。");
            return stream.text.toString();
        } finally { if (c != null) c.disconnect(); else if (current != null) current.disconnect(); current = null; }
    }
    private static String complete(JSONObject value, boolean responses) throws Exception {
        if (value.has("error") && !value.isNull("error")) throw new IOException("平台返回生成错误，请检查模型权限与额度。");
        if (responses) {
            if (!"completed".equals(value.optString("status"))) throw new IOException("回复未完整生成，已保留收到的内容。");
            StringBuilder result = new StringBuilder(); JSONArray output = value.optJSONArray("output");
            if (output != null) for (int i = 0; i < output.length(); i++) {
                JSONObject item = output.optJSONObject(i);
                if (item == null || !"message".equals(item.optString("type"))) continue;
                JSONArray content = item.optJSONArray("content");
                if (content != null) for (int j = 0; j < content.length(); j++) {
                    JSONObject part = content.optJSONObject(j); if (part == null) continue;
                    result.append(part.optString("type").equals("refusal") ? part.optString("refusal") : part.optString("text"));
                }
            }
            return result.toString();
        }
        JSONArray choices = value.optJSONArray("choices"); JSONObject first = choices == null ? null : choices.optJSONObject(0);
        if (first == null) return "";
        String reason = first.optString("finish_reason");
        if (!"stop".equals(reason)) throw new IOException("回复未完整生成（" + ("length".equals(reason) ? "达到长度限制" : "平台提前结束") + "）。");
        JSONObject message = first.optJSONObject("message");
        return message == null ? "" : message.isNull("content") ? message.optString("refusal") : message.optString("content");
    }
    private static final class Stream {
        final boolean responses; final Update update; final StringBuilder text = new StringBuilder(); boolean done;
        Stream(boolean responses, Update update) { this.responses = responses; this.update = update; }
        void event(String data) throws Exception {
            if (data.equals("[DONE]")) { if (!responses) done = true; return; }
            JSONObject value;
            try { value = new JSONObject(data); } catch (Exception e) { throw new IOException("平台返回了无法解析的流式数据。"); }
            if (value.has("error") && !value.isNull("error")) throw new IOException("平台生成失败，请检查模型权限与额度。");
            if (responses) {
                String type = value.optString("type");
                if (type.equals("response.failed") || type.equals("response.incomplete") || type.equals("error")) throw new IOException("平台未能完整生成回复，已保留收到的内容。");
                if (type.equals("response.output_text.delta") || type.equals("response.refusal.delta")) append(value.optString("delta"));
                if (type.equals("response.completed")) {
                    JSONObject response = value.optJSONObject("response");
                    if (response == null) throw new IOException("平台回复缺少完成状态。");
                    String full = complete(response, true);
                    if (text.length() == 0 && !full.isEmpty()) append(full);
                    done = true;
                }
            } else {
                JSONArray choices = value.optJSONArray("choices"); if (choices == null || choices.length() == 0) return;
                JSONObject first = choices.optJSONObject(0); if (first == null) return;
                JSONObject delta = first.optJSONObject("delta");
                if (delta != null) {
                    if (!delta.isNull("content")) append(delta.optString("content"));
                    else if (!delta.isNull("refusal")) append(delta.optString("refusal"));
                }
                if (!first.isNull("finish_reason")) {
                    if (!"stop".equals(first.optString("finish_reason"))) throw new IOException("平台提前结束回复，已保留收到的内容。");
                    done = true;
                }
            }
        }
        void append(String part) throws Exception {
            if (text.length() + part.length() > 300000) throw new IOException("回复过长，已保留收到的内容。");
            if (!part.isEmpty()) { text.append(part); update.text(text.toString()); }
        }
    }
}
