package com.codexpocket.app;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.File;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public final class DirectApiTest {
    static void require(boolean condition, String why) { if (!condition) throw new AssertionError(why); }
    static final String KEY = "fake-local-test-key";
    static void reply(HttpExchange exchange, int status, String type, String body) throws Exception {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8); exchange.getResponseHeaders().set("Content-Type", type); exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream out = exchange.getResponseBody()) { out.write(bytes); }
    }
    static String data(String json) { return "data: " + json + "\r\n\r\n"; }
    public static void main(String[] args) throws Exception {
        AtomicInteger leaks = new AtomicInteger(), calls = new AtomicInteger(); CountDownLatch started = new CountDownLatch(1);
        HttpServer server = HttpServer.create(new java.net.InetSocketAddress("127.0.0.1", 0), 0);
        java.util.concurrent.ExecutorService workers = Executors.newCachedThreadPool(); server.setExecutor(workers);
        String base = "http://127.0.0.1:" + server.getAddress().getPort() + "/v1";
        server.createContext("/leak", exchange -> { leaks.incrementAndGet(); exchange.sendResponseHeaders(200, -1); exchange.close(); });
        server.createContext("/v1/", exchange -> {
            try {
                calls.incrementAndGet(); require(("Bearer " + KEY).equals(exchange.getRequestHeaders().getFirst("Authorization")), "provider credential only");
                if (exchange.getRequestURI().getPath().endsWith("/models")) { reply(exchange, 200, "application/json", "{\"data\":[{\"id\":\"z\"},{\"id\":\"deepseek-flash\"},{\"id\":\"z\"}]}"); return; }
                JSONObject body = new JSONObject(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8)); String model = body.getString("model");
                require(body.getBoolean("stream"), "stream enabled");
                boolean responses = exchange.getRequestURI().getPath().endsWith("/responses");
                require(body.has(responses ? "input" : "messages"), "protocol request schema");
                require(!responses || !body.getBoolean("store"), "responses store false");
                if (model.equals("auth")) { reply(exchange, 401, "application/json", "{\"error\":{\"message\":\"" + KEY + "\"}}"); return; }
                if (model.equals("redirect")) { exchange.getResponseHeaders().set("Location", base.replace("/v1", "/leak")); exchange.sendResponseHeaders(307, -1); exchange.close(); return; }
                if (model.equals("json")) { reply(exchange, 200, "application/json", responses ? "{\"status\":\"completed\",\"output\":[{\"type\":\"message\",\"content\":[{\"type\":\"output_text\",\"text\":\"完整回答\"}]}]}" : "{\"choices\":[{\"message\":{\"content\":\"完整回答\"},\"finish_reason\":\"stop\"}]}"); return; }
                exchange.getResponseHeaders().set("Content-Type", "text/event-stream"); exchange.sendResponseHeaders(200, 0);
                try (OutputStream out = exchange.getResponseBody()) {
                    if (model.equals("slow")) { out.write(": ready\n\n".getBytes(StandardCharsets.UTF_8)); out.flush(); started.countDown(); Thread.sleep(1800); out.write(data("[DONE]").getBytes(StandardCharsets.UTF_8)); return; }
                    if (model.equals("malformed")) { out.write(data("{broken").getBytes(StandardCharsets.UTF_8)); return; }
                    if (model.equals("empty")) { out.write(data("[DONE]").getBytes(StandardCharsets.UTF_8)); return; }
                    if (model.equals("file")) {
                        JSONArray messages = body.getJSONArray(responses ? "input" : "messages");
                        require(messages.getJSONObject(0).getString("role").equals("system") && messages.getJSONObject(0).getString("content").contains("pocket-file"), "local file instructions on both protocols");
                        String artifact = "文档已准备。\n```pocket-file\n" + new JSONObject().put("name", "模型文档.docx").put("content", "# 中文报告\n内容🐳") + "\n```\n";
                        JSONObject delta = responses ? new JSONObject().put("type", "response.output_text.delta").put("delta", artifact) :
                            new JSONObject().put("choices", new JSONArray().put(new JSONObject().put("delta", new JSONObject().put("content", artifact))));
                        out.write(data(delta.toString()).getBytes(StandardCharsets.UTF_8));
                        String finish = responses ? "{\"type\":\"response.completed\",\"response\":{\"status\":\"completed\",\"output\":[]}}" : "{\"choices\":[{\"delta\":{},\"finish_reason\":\"stop\"}]}";
                        out.write(data(finish).getBytes(StandardCharsets.UTF_8)); return;
                    }
                    String first = responses ? data("{\"type\":\"response.output_text.delta\",\"delta\":\"你好🐳\"}") :
                        "data: {\"choices\":\n" + "data: [{\"delta\":{\"content\":\"你好🐳\"}}]}\r\n\r\n";
                    byte[] bytes = first.getBytes(StandardCharsets.UTF_8);
                    for (byte value : bytes) { out.write(value); out.flush(); }
                    if (model.equals("abrupt")) return;
                    String end = responses ? model.equals("failed") ? data("{\"type\":\"response.failed\"}") : model.equals("incomplete") ? data("{\"type\":\"response.incomplete\"}") : data("{\"type\":\"response.completed\",\"response\":{\"status\":\"completed\",\"output\":[]}}") :
                        data("{\"choices\":[{\"delta\":{},\"finish_reason\":\"" + (model.equals("length") ? "length" : "stop") + "\"}]}");
                    out.write(end.getBytes(StandardCharsets.UTF_8));
                }
            } catch (Throwable error) {
                if (error instanceof InterruptedException) Thread.currentThread().interrupt();
                else if (!(error instanceof java.io.IOException)) error.printStackTrace();
                exchange.close();
            }
        });
        server.start();
        try {
            require(new DirectApi(base, KEY).models().equals(java.util.Arrays.asList("deepseek-flash", "z")), "real model IDs deduplicated");
            JSONArray input = new JSONArray().put(new JSONObject().put("role", "user").put("content", "test"));
            for (String protocol : new String[]{"chat", "responses"}) {
                StringBuilder received = new StringBuilder();
                String result = new DirectApi(base, KEY).chat("good", protocol, input, text -> { received.setLength(0); received.append(text); });
                require(result.equals("你好🐳") && received.toString().equals(result), "fragmented UTF-8/multiline SSE " + protocol);
            }
            for (String protocol : new String[]{"chat", "responses"}) require(new DirectApi(base, KEY).chat("json", protocol, input, text -> {}).equals("完整回答"), "JSON fallback " + protocol);
            for (String model : new String[]{"abrupt", "length", "failed", "incomplete", "malformed", "empty", "auth", "redirect"}) {
                int before = calls.get(); boolean failed = false;
                try { new DirectApi(base, KEY).chat(model, model.equals("failed") || model.equals("incomplete") ? "responses" : "chat", input, text -> {}); }
                catch (Exception e) { failed = true; require(!String.valueOf(e.getMessage()).contains(KEY), "no credential echo"); }
                require(failed, "incomplete/auth/redirect failure " + model); require(calls.get() == before + 1, "no automatic paid retries");
            }
            require(leaks.get() == 0, "never forward bearer token on redirect");
            File generatedDir = Files.createTempDirectory("pocket-file-http-test-").toFile();
            try {
                LocalArtifacts artifacts = new LocalArtifacts(generatedDir);
                for (String protocol : new String[]{"chat", "responses"}) {
                    String answer = new DirectApi(base, KEY).chat("file", protocol, LocalFileSpec.withGuide(input), text -> {});
                    JSONArray descriptors = artifacts.generate(LocalFileSpec.parse(answer), (file, content) -> { throw new AssertionError(); });
                    require(descriptors.length() == 1 && artifacts.resolve(descriptors.getJSONObject(0)).length() > 100, "HTTP reply materializes real Word file " + protocol);
                    artifacts.discard(descriptors);
                }
            } finally { for (File file : generatedDir.listFiles()) file.delete(); generatedDir.delete(); }
            DirectApi client = new DirectApi(base, KEY);
            java.util.concurrent.ExecutorService runner = Executors.newSingleThreadExecutor();
            try {
                java.util.concurrent.Future<Boolean> task = runner.submit(() -> { try { client.chat("slow", "chat", input, text -> {}); return false; } catch (Exception e) { return true; } });
                require(started.await(3, TimeUnit.SECONDS), "stream started"); client.cancel(); require(task.get(5, TimeUnit.SECONDS), "cancelled request fails instead of completing");
            } finally { runner.shutdownNow(); }

            require(DirectEndpoint.normalize("https://api.deepseek.com").equals("https://api.deepseek.com/v1"), "root normalization");
            require(DirectEndpoint.normalize("https://api.openai.com/v1/responses/").equals("https://api.openai.com/v1"), "endpoint normalization");
            for (String bad : new String[]{"http://host/v1", "https://host/pricing", "https://host/usage", "https://key@host/v1", "https://host/v1?key=secret", "https://host/v1#secret"}) {
                boolean rejected = false; try { DirectEndpoint.normalize(bad); } catch (Exception e) { rejected = true; } require(rejected, "reject unsafe/non-API URL");
            }
            require(!DirectEndpoint.sameOrigin("https://api.deepseek.com/v1", "https://api.openai.com/v1"), "credentials bound to host");

            File directory = Files.createTempDirectory("pocket-direct-chat-test-").toFile();
            try {
                DirectChats store = new DirectChats(directory); JSONObject chat = store.create("deepseek", "https://api.deepseek.com/v1", "deepseek-flash", "chat");
                JSONArray turns = chat.getJSONArray("messages");
                turns.put(new JSONObject().put("role", "user").put("text", "first").put("state", "complete"));
                turns.put(new JSONObject().put("role", "assistant").put("text", "answer").put("state", "complete"));
                turns.put(new JSONObject().put("role", "user").put("text", "failed question").put("state", "complete"));
                turns.put(new JSONObject().put("role", "assistant").put("text", "partial").put("state", "streaming"));
                chat.put("draft", "saved draft"); store.save(chat); JSONObject restored = store.load(chat.getString("id"));
                require(restored.getJSONArray("messages").getJSONObject(3).getString("state").equals("interrupted"), "process death recovery");
                JSONArray context = DirectChats.context(restored, "next"); require(context.length() == 3 && context.getJSONObject(0).getString("content").equals("first") && context.getJSONObject(2).getString("content").equals("next"), "partial/failed pair excluded from context");
                require(restored.getString("draft").equals("saved draft") && store.list().size() == 1, "history and draft reload");
                require(!new String(FileVault.read(new File(directory, chat.getString("id") + ".json")),StandardCharsets.UTF_8).contains(KEY), "no key in decrypted history");
                boolean traversal = false; try { store.load("../../other"); } catch (Exception e) { traversal = true; } require(traversal, "path traversal rejected");
                store.delete(chat.getString("id")); require(store.list().isEmpty(), "delete isolated phone history");
            } finally { for (File file : directory.listFiles()) file.delete(); directory.delete(); }
            System.out.println("PASS: real HTTP schemas/auth, models, fragmented UTF-8/SSE, two protocols, JSON, truncation, errors, no redirects/retries, cancel, endpoint validation and atomic phone history/context recovery.");
        } finally { server.stop(0); workers.shutdownNow(); }
    }
}
