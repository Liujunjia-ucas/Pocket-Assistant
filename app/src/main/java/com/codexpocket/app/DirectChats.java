package com.codexpocket.app;

import org.json.JSONArray;
import org.json.JSONObject;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/** Independent phone conversations. Partial turns are visible but never resent as context. */
final class DirectChats {
    private final File directory;
    DirectChats(File directory) throws IOException {
        this.directory = directory;
        if (!directory.isDirectory() && !directory.mkdirs()) throw new IOException("无法建立手机会话目录。");
    }
    JSONObject create(String provider, String base, String model, String protocol) throws Exception {
        return new JSONObject().put("id", UUID.randomUUID().toString()).put("provider", provider).put("base", base)
            .put("model", model).put("protocol", protocol).put("title", "新对话").put("updated", System.currentTimeMillis())
            .put("draft", "").put("messages", new JSONArray());
    }
    private File file(String id) throws Exception {
        if (!id.matches("[a-f0-9-]{36}")) throw new IOException("会话编号无效。");
        return new File(directory, id + ".json");
    }
    JSONObject load(String id) throws Exception {
        File file = file(id);
        if (file.length() > 4 * 1024 * 1024) throw new IOException("手机会话过大，请新建对话。");
        JSONObject chat = new JSONObject(new String(FileVault.read(file), StandardCharsets.UTF_8));
        if (!id.equals(chat.optString("id")) || chat.optJSONArray("messages") == null) throw new IOException("会话文件无法读取。");
        // A process killed during a stream must not later treat the partial reply as complete.
        JSONArray messages = chat.getJSONArray("messages");
        for (int i = 0; i < messages.length(); i++) {
            JSONObject m = messages.getJSONObject(i);
            if ("streaming".equals(m.optString("state"))) m.put("state", "interrupted");
        }
        return chat;
    }
    void save(JSONObject chat) throws Exception {
        File target = file(chat.getString("id"));
        byte[] bytes = chat.toString().getBytes(StandardCharsets.UTF_8);
        if (bytes.length > 4 * 1024 * 1024) throw new IOException("手机会话过大，请新建对话。");
        File temporary = new File(directory, target.getName() + ".tmp");
        try (FileOutputStream out = new FileOutputStream(temporary)) { out.write(FileVault.encrypt(target,bytes)); out.getFD().sync(); }
        Files.move(temporary.toPath(), target.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
    }
    List<JSONObject> list() throws Exception {
        List<JSONObject> result = new ArrayList<>(); File[] files = directory.listFiles();
        if (files == null) return result;
        for (File file : files) if (file.getName().endsWith(".json")) {
            try { JSONObject chat = load(file.getName().substring(0, file.getName().length() - 5));
                result.add(new JSONObject().put("id", chat.getString("id")).put("title", chat.optString("title"))
                    .put("provider", chat.optString("provider")).put("model", chat.optString("model")).put("updated", chat.optLong("updated")));
            } catch (Exception ignored) { /* Preserve the original file for recovery. */ }
        }
        result.sort(Comparator.comparingLong((JSONObject chat) -> chat.optLong("updated")).reversed()); return result;
    }
    void delete(String id) throws Exception { Files.deleteIfExists(file(id).toPath()); }
    static JSONArray context(JSONObject chat, String prompt) throws Exception {
        return context(chat,prompt,new JSONArray());
    }
    static JSONArray context(JSONObject chat, String prompt, JSONArray attachments) throws Exception {
        JSONArray all = chat.getJSONArray("messages"), context = new JSONArray();
        List<JSONObject> pairs = new ArrayList<>(); int chars = prompt.length();
        for (int i = all.length() - 1; i >= 1; i--) {
            JSONObject reply = all.getJSONObject(i), user = all.getJSONObject(i - 1);
            if (!"assistant".equals(reply.optString("role")) || !"complete".equals(reply.optString("state")) || !"user".equals(user.optString("role"))) continue;
            String answer=reply.optString("text");if(reply.optJSONObject("web")!=null)answer+=DirectWeb.history(reply.getJSONObject("web"));
            int length = user.optString("text").length() + answer.length();
            if (chars + length > 100000 || pairs.size() >= 40) break;
            chars += length; pairs.add(0, new JSONObject().put("role", "assistant").put("content", answer));
            JSONObject input=new JSONObject().put("role", "user").put("content", user.optString("text"));
            if(user.optJSONArray("attachments")!=null)input.put("attachmentRefs",user.getJSONArray("attachments"));
            pairs.add(0, input); i--;
        }
        for (JSONObject message : pairs) context.put(message);
        JSONObject current=new JSONObject().put("role", "user").put("content", prompt);
        if(attachments.length()>0)current.put("attachmentRefs",attachments);
        context.put(current); return context;
    }
}
