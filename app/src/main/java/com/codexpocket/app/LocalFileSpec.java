package com.codexpocket.app;

import org.json.JSONArray;
import org.json.JSONObject;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Data-only file requests. No model-supplied code is executed. */
final class LocalFileSpec {
    static final String MARKER = "```pocket-file";
    static final String GUIDE = "你可以让手机本地生成文件。只有用户要求生成/导出/下载文件时，才在正文后输出 pocket-file 代码块，内容为严格 JSON。"
        + "每个代码块描述一个文件，最多5个。示例：```pocket-file\n{\"name\":\"报告.docx\",\"content\":\"# 标题\\n正文\"}\n```。"
        + "支持 docx(Word)、pdf、txt、md、html、json、csv、xlsx(Excel)、zip 及常见代码文本。docx/pdf 的 content 是完整正文，可用 Markdown 标题、列表、表格，不能是生成脚本。"
        + "xlsx 使用 {\"name\":\"表格.xlsx\",\"sheets\":[{\"name\":\"Sheet1\",\"rows\":[[\"项目\",\"数值\"],[\"示例\",12]]}]}，也可只给 rows。"
        + "csv 使用 rows 二维数组。zip 使用 {\"name\":\"项目.zip\",\"entries\":[{\"name\":\"src/main.py\",\"content\":\"print('hello')\"}]}，entries 只含文本文件，不要嵌套 zip 或伪造二进制。"
        + "文本类使用 name 和 content。必须写完整内容，JSON 字符串正确转义，不用 Base64、不输出文件下载地址、不声称已保存到电脑。APP 会生成真正文件并提供保存按钮。"
        + "不需要代码执行器，不要用 Python 等脚本代替 Word/Excel 正文。普通聊天不输出这些块。不能生成的格式应说明能力范围。";
    private static final Pattern BLOCK = Pattern.compile("(?m)^ {0,3}```pocket-file[ \\t]*\\r?\\n([\\s\\S]*?)^ {0,3}```[ \\t]*(?:\\r?\\n|$)");
    final String name, extension, content;
    final JSONObject data;
    LocalFileSpec(JSONObject value) throws Exception {
        data = value; name = safeName(value.getString("name"), false);
        int dot = name.lastIndexOf('.'); extension = dot < 0 ? "" : name.substring(dot + 1).toLowerCase(Locale.ROOT);
        if (!extension.matches("docx|xlsx|pdf|zip|txt|md|markdown|csv|html?|json|xml|yaml|yml|py|js|ts|tsx|jsx|java|kt|kts|css|scss|sql|c|cpp|h|hpp|cs|go|rs|sh|bat|cmd|ps1|toml|ini|log|svg")) throw new IOException("暂不支持文件格式：" + extension);
        content = value.optString("content", "");
        if (content.length() > 200000) throw new IOException("单个文件内容过长，请分成几个文件生成。");
        if (!extension.matches("xlsx|zip|csv") && !value.has("content")) throw new IOException("文件缺少正文内容，请让模型重新生成。");
        if (extension.equals("csv") && !value.has("rows") && !value.has("content")) throw new IOException("CSV 文件缺少表格数据。");
    }
    static String safeName(String value, boolean nested) throws Exception {
        String name = value.trim();
        if (name.isEmpty() || name.length() > 180 || name.startsWith("/") || name.contains("\\") || Pattern.compile("[\\p{Cntrl}:*?\"<>|]").matcher(name).find()) throw new IOException("文件名无效。");
        if (!nested && name.contains("/")) throw new IOException("文件名不能包含目录。");
        for (String part : name.split("/", -1)) if (part.isEmpty() || part.equals(".") || part.equals("..") || part.endsWith(".") || part.endsWith(" ")) throw new IOException("文件目录或名称无效。");
        return name;
    }
    static List<LocalFileSpec> parse(String text) throws Exception {
        List<LocalFileSpec> specs = new ArrayList<>(); Matcher blocks = BLOCK.matcher(text); int end = 0;
        while (blocks.find()) {
            if (text.substring(end, blocks.start()).contains(MARKER)) throw new IOException("文件数据没有完整结束，请让模型重新生成。");
            if (specs.size() >= 5) throw new IOException("一次最多生成5个文件。");
            try { specs.add(new LocalFileSpec(new JSONObject(blocks.group(1)))); }
            catch (org.json.JSONException e) { throw new IOException("模型返回的文件数据格式有误，请让它重新生成。"); }
            end = blocks.end();
        }
        if (text.substring(end).contains(MARKER)) throw new IOException("文件数据没有完整结束，请让模型重新生成。");
        return specs;
    }
    static String display(String text, boolean streaming) {
        if (streaming) { int at = text.indexOf(MARKER); return at < 0 ? text : text.substring(0, at).trim() + "\n\n正在准备文件…"; }
        return BLOCK.matcher(text).replaceAll("").trim();
    }
    static JSONArray withGuide(JSONArray messages) throws Exception {
        JSONArray result = new JSONArray().put(new JSONObject().put("role", "system").put("content", GUIDE));
        for (int i = 0; i < messages.length(); i++) result.put(messages.get(i)); return result;
    }
}
