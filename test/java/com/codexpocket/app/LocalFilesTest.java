package com.codexpocket.app;

import org.json.JSONArray;
import org.json.JSONObject;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipFile;

public final class LocalFilesTest {
    static void require(boolean value, String why) { if (!value) throw new AssertionError(why); }
    static String block(JSONObject value) { return "```pocket-file\n" + value + "\n```\n"; }
    interface Work { void run() throws Exception; }
    static void rejects(Work work, String why) throws Exception { boolean failed = false; try { work.run(); } catch (Exception e) { failed = true; } require(failed, why); }
    public static void main(String[] args) throws Exception {
        File directory = new File("state/local-file-fixtures"); Files.createDirectories(directory.toPath());
        FileVault.initialize(directory);
        LocalArtifacts artifacts = new LocalArtifacts(new File(directory, "artifacts"));
        String content = "# 中文报告🐳\n自然段包含 < & > 与数学符号 α。\n- 第一项\n| 项目 | 数量 |\n| --- | --- |\n| 苹果 | 12 |\n\n结束。";
        JSONObject word = new JSONObject().put("name", "中文报告.docx").put("content", content);
        JSONObject excel = new JSONObject().put("name", "统计.xlsx").put("sheets", new JSONArray()
            .put(new JSONObject().put("name", "中文统计").put("rows", new JSONArray().put(new JSONArray().put("项目").put("数量").put("备注"))
                .put(new JSONArray().put("苹果🐳").put(12).put("=not_a_formula"))
                .put(new JSONArray().put("梨").put(3.5).put(true))))
            .put(new JSONObject().put("name", "Sheet2").put("rows", new JSONArray().put(new JSONArray().put("A & B").put(JSONObject.NULL)))));
        JSONObject csv = new JSONObject().put("name", "明细.csv").put("rows", new JSONArray().put(new JSONArray().put("a,b").put("line\nnext").put("\"quoted\""))
            .put(new JSONArray().put("=1+1").put(12).put(true)));
        JSONObject zip = new JSONObject().put("name", "代码.zip").put("entries", new JSONArray()
            .put(new JSONObject().put("name", "src/main.py").put("content", "print('中文🐳')\n"))
            .put(new JSONObject().put("name", "说明.md").put("content", "# 项目\n正文")));
        JSONObject text = new JSONObject().put("name", "笔记.md").put("content", "正文🐳\n");
        String answer = "文件已准备好。\n" + block(word) + block(excel) + block(csv) + block(zip) + block(text);
        List<LocalFileSpec> specs = LocalFileSpec.parse(answer);
        require(specs.size() == 5, "multiple artifact blocks parsed");
        require(LocalFileSpec.display(answer, false).equals("文件已准备好。"), "file metadata hidden in final reply");
        require(!LocalFileSpec.display(answer.substring(0, answer.length() / 2), true).contains("\"name\""), "stream hides unfinished file JSON");
        require(LocalFileSpec.parse("普通聊天\n```python\nprint('hello')\n```").isEmpty(), "ordinary code not silently treated as generated file");
        JSONArray generated = artifacts.generate(specs, (file, body) -> { throw new AssertionError("PDF adapter must not run for OOXML"); });
        require(generated.length() == 5, "real files before descriptors");
        JSONArray manifest = new JSONArray();
        for (int i = 0; i < generated.length(); i++) {
            JSONObject descriptor = generated.getJSONObject(i); File resolved = artifacts.resolve(descriptor);
            require(resolved.length() > 0 && resolved.length() == descriptor.getLong("size"), "size verified");
            JSONObject record = new JSONObject(descriptor.toString()).put("path", resolved.getAbsolutePath()); manifest.put(record);
            if (descriptor.getString("name").matches(".*\\.(docx|xlsx|zip)")) try (ZipFile packageFile = new ZipFile(resolved)) { require(packageFile.size() > 0, "valid zip container"); }
        }
        Files.writeString(new File(directory, "manifest.json").toPath(), manifest.toString(2), StandardCharsets.UTF_8);
        rejects(() -> LocalFileSpec.parse("```pocket-file\n{broken}\n```\n"), "malformed JSON rejected");
        rejects(() -> LocalFileSpec.parse("```pocket-file\n{\"name\":\"x.txt\"}"), "unfinished block rejected");
        rejects(() -> LocalFileSpec.parse(block(word).repeat(6)), "file count capped");
        for (String name : new String[]{"../a.txt", "/a.txt", "C:\\a.txt", "file.exe", "file.txt\nother", "a/b.txt"}) rejects(() -> new LocalFileSpec(new JSONObject().put("name", name).put("content", "x")), "unsafe/unsupported name rejected");
        rejects(() -> artifacts.resolve(new JSONObject().put("id", "../../x").put("size", 1)), "artifact traversal rejected");
        JSONObject stale = new JSONObject(generated.getJSONObject(0).toString()).put("size", 999);
        rejects(() -> artifacts.resolve(stale), "corrupt/missing file not published");
        File rollback = new File(directory, "rollback"); LocalArtifacts rollbackStore = new LocalArtifacts(rollback);
        List<LocalFileSpec> bad = new ArrayList<>(); bad.add(new LocalFileSpec(text)); bad.add(new LocalFileSpec(new JSONObject().put("name", "bad.zip").put("entries", new JSONArray().put(new JSONObject().put("name", "../bad.txt").put("content", "x")))));
        rejects(() -> rollbackStore.generate(bad, (f,c) -> {}), "zip traversal rejected"); require(rollback.list().length == 0, "failed batch rolls back prior files and partials");
        List<LocalFileSpec> pdf = java.util.Collections.singletonList(new LocalFileSpec(new JSONObject().put("name", "report.pdf").put("content", "test")));
        rejects(() -> rollbackStore.generate(pdf, (f,c) -> { Files.writeString(f.toPath(), "partial"); throw new java.io.IOException("PDF failure"); }), "PDF writer failure surfaced");
        require(rollback.list().length == 0, "failed PDF leaves no card or temporary");
        Thread.currentThread().interrupt(); rejects(() -> rollbackStore.generate(specs, (f,c) -> {}), "interrupted generation rejected"); Thread.interrupted();
        JSONArray guide = LocalFileSpec.withGuide(new JSONArray().put(new JSONObject().put("role", "user").put("content", "生成 Word")));
        require(guide.getJSONObject(0).getString("role").equals("system") && guide.getJSONObject(1).getString("content").equals("生成 Word"), "file contract supplied without altering user text");
        DirectChats chats = new DirectChats(new File(directory, "chat")); JSONObject chat = chats.create("deepseek", "https://api.deepseek.com/v1", "deepseek-flash", "chat");
        chat.getJSONArray("messages").put(new JSONObject().put("role", "assistant").put("text", answer).put("state", "complete").put("files", generated)); chats.save(chat);
        require(chats.load(chat.getString("id")).getJSONArray("messages").getJSONObject(0).getJSONArray("files").length() == 5, "file cards persist in phone history");
        System.out.println("PASS: structured file contract, real DOCX/XLSX/CSV/ZIP/Markdown, UTF-8, persisted cards, limits, path safety, rollback, PDF failure and interruption. Fixtures: state/local-file-fixtures/manifest.json");
    }
}
