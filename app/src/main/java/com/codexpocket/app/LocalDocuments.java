package com.codexpocket.app;

import org.json.JSONArray;
import org.json.JSONObject;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.HashSet;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/** Small OOXML/text writers; packages contain data and never macros or external links. */
final class LocalDocuments {
    private static final String HEADER = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>";
    static void write(File file, LocalFileSpec spec) throws Exception {
        switch (spec.extension) {
            case "docx": docx(file, spec.content); break;
            case "xlsx": xlsx(file, spec.data); break;
            case "zip": bundle(file, spec.data); break;
            case "csv":
                String csv = spec.data.has("rows") ? csv(spec.data.getJSONArray("rows")) : spec.content;
                Files.write(file.toPath(), ("\ufeff" + csv).getBytes(StandardCharsets.UTF_8)); break;
            default: Files.write(file.toPath(), spec.content.getBytes(StandardCharsets.UTF_8));
        }
    }
    private static String xml(String text) {
        StringBuilder result = new StringBuilder();
        text.codePoints().forEach(c -> {
            if (!(c == 9 || c == 10 || c == 13 || c >= 32 && c <= 0xd7ff || c >= 0xe000 && c <= 0xfffd || c >= 0x10000 && c <= 0x10ffff)) return;
            switch (c) {
                case '&': result.append("&amp;"); break; case '<': result.append("&lt;"); break; case '>': result.append("&gt;"); break;
                case '"': result.append("&quot;"); break; case '\'': result.append("&apos;"); break; default: result.appendCodePoint(c);
            }
        }); return result.toString();
    }
    private static void entry(ZipOutputStream zip, String name, String text) throws Exception {
        zip.putNextEntry(new ZipEntry(name)); zip.write(text.getBytes(StandardCharsets.UTF_8)); zip.closeEntry();
    }
    private static String paragraph(String value, boolean heading) {
        StringBuilder runs = new StringBuilder();
        for (String part : value.split("\n", -1)) {
            if (runs.length() > 0) runs.append("<w:r><w:br/></w:r>");
            runs.append("<w:r><w:rPr><w:rFonts w:ascii=\"Calibri\" w:hAnsi=\"Calibri\" w:eastAsia=\"Microsoft YaHei\"/>")
                .append(heading ? "<w:b/><w:sz w:val=\"32\"/>" : "<w:sz w:val=\"22\"/>")
                .append("</w:rPr><w:t xml:space=\"preserve\">").append(xml(part)).append("</w:t></w:r>");
        }
        return "<w:p><w:pPr><w:spacing w:after=\"120\" w:line=\"300\" w:lineRule=\"auto\"/></w:pPr>" + runs + "</w:p>";
    }
    private static void docx(File file, String content) throws Exception {
        StringBuilder body = new StringBuilder(); boolean table = false;
        for (String line : content.replace("\r\n", "\n").split("\n", -1)) {
            boolean row = line.trim().startsWith("|") && line.trim().endsWith("|");
            if (row) {
                if (line.trim().matches("\\|[ \\-:|]+\\|")) continue;
                if (!table) { body.append("<w:tbl><w:tblPr><w:tblW w:w=\"0\" w:type=\"auto\"/><w:tblBorders>");
                    for (String edge : new String[]{"top", "left", "bottom", "right", "insideH", "insideV"}) body.append("<w:").append(edge).append(" w:val=\"single\" w:sz=\"4\" w:color=\"CCCCCC\"/>");
                    body.append("</w:tblBorders></w:tblPr><w:tblGrid>");
                    String trimmed = line.trim(); int columns = trimmed.substring(1, trimmed.length() - 1).split("\\|", -1).length;
                    for (int i = 0; i < columns; i++) body.append("<w:gridCol w:w=\"").append(9000 / columns).append("\"/>");
                    body.append("</w:tblGrid>"); table = true;
                }
                body.append("<w:tr>"); String trimmed = line.trim();
                for (String cell : trimmed.substring(1, trimmed.length() - 1).split("\\|", -1)) body.append("<w:tc><w:tcPr><w:tcW w:w=\"2400\" w:type=\"dxa\"/></w:tcPr>").append(paragraph(cell.trim(), false)).append("</w:tc>");
                body.append("</w:tr>");
            } else {
                if (table) { body.append("</w:tbl>"); table = false; }
                boolean heading = line.matches("^#{1,6} .*" );
                String value = heading ? line.replaceFirst("^#{1,6} +", "") : line.replaceFirst("^\\s*[-*] +", "• ");
                body.append(paragraph(value, heading));
            }
        }
        if (table) body.append("</w:tbl>");
        body.append("<w:sectPr><w:pgSz w:w=\"11906\" w:h=\"16838\"/><w:pgMar w:top=\"1134\" w:right=\"1134\" w:bottom=\"1134\" w:left=\"1134\"/></w:sectPr>");
        try (ZipOutputStream zip = new ZipOutputStream(new FileOutputStream(file))) {
            entry(zip, "[Content_Types].xml", HEADER + "<Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\"><Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/><Default Extension=\"xml\" ContentType=\"application/xml\"/><Override PartName=\"/word/document.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml\"/></Types>");
            entry(zip, "_rels/.rels", HEADER + "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\"><Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument\" Target=\"word/document.xml\"/></Relationships>");
            entry(zip, "word/document.xml", HEADER + "<w:document xmlns:w=\"http://schemas.openxmlformats.org/wordprocessingml/2006/main\"><w:body>" + body + "</w:body></w:document>");
        }
    }
    private static void validateRows(JSONArray rows) throws Exception {
        if (rows.length() > 2000) throw new IOException("表格最多支持2000行。");
        int cells = 0;
        for (int r = 0; r < rows.length(); r++) {
            JSONArray row = rows.getJSONArray(r); cells += row.length();
            if (row.length() > 100 || cells > 30000) throw new IOException("表格过大，请拆分生成。");
            for (int c = 0; c < row.length(); c++) if (String.valueOf(row.get(c)).length() > 32767) throw new IOException("单元格文本过长。");
        }
    }
    private static String column(int index) { String name = ""; for (int n = index + 1; n > 0; n = (n - 1) / 26) name = (char)('A' + (n - 1) % 26) + name; return name; }
    private static void xlsx(File file, JSONObject value) throws Exception {
        JSONArray sheets = value.optJSONArray("sheets");
        if (sheets == null) sheets = new JSONArray().put(new JSONObject().put("name", "Sheet1").put("rows", value.getJSONArray("rows")));
        if (sheets.length() < 1 || sheets.length() > 8) throw new IOException("Excel 工作表数量应为1到8张。");
        StringBuilder types = new StringBuilder(), workbook = new StringBuilder(), rels = new StringBuilder(); Set<String> names = new HashSet<>();
        try (ZipOutputStream zip = new ZipOutputStream(new FileOutputStream(file))) {
            for (int i = 0; i < sheets.length(); i++) {
                JSONObject sheet = sheets.getJSONObject(i); String name = sheet.optString("name", "Sheet" + (i + 1));
                if (name.isEmpty() || name.length() > 31 || name.matches(".*[\\[\\]:*?/\\\\].*") || name.startsWith("'") || name.endsWith("'") || !names.add(name.toLowerCase(java.util.Locale.ROOT))) throw new IOException("Excel 工作表名称无效或重复。");
                JSONArray rows = sheet.getJSONArray("rows"); validateRows(rows); StringBuilder xml = new StringBuilder();
                xml.append(HEADER).append("<worksheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\"><sheetData>");
                for (int r = 0; r < rows.length(); r++) {
                    xml.append("<row r=\"").append(r + 1).append("\">"); JSONArray row = rows.getJSONArray(r);
                    for (int c = 0; c < row.length(); c++) {
                        Object cell = row.get(c); if (cell == JSONObject.NULL) continue;
                        xml.append("<c r=\"").append(column(c)).append(r + 1).append("\"");
                        if (cell instanceof Number) xml.append("><v>").append(cell).append("</v></c>");
                        else if (cell instanceof Boolean) xml.append(" t=\"b\"><v>").append((Boolean)cell ? 1 : 0).append("</v></c>");
                        else xml.append(" t=\"inlineStr\"><is><t xml:space=\"preserve\">").append(xml(String.valueOf(cell))).append("</t></is></c>");
                    }
                    xml.append("</row>");
                }
                xml.append("</sheetData></worksheet>"); entry(zip, "xl/worksheets/sheet" + (i + 1) + ".xml", xml.toString());
                types.append("<Override PartName=\"/xl/worksheets/sheet").append(i + 1).append(".xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml\"/>");
                workbook.append("<sheet name=\"").append(xml(name)).append("\" sheetId=\"").append(i + 1).append("\" r:id=\"rId").append(i + 1).append("\"/>");
                rels.append("<Relationship Id=\"rId").append(i + 1).append("\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet\" Target=\"worksheets/sheet").append(i + 1).append(".xml\"/>");
            }
            entry(zip, "[Content_Types].xml", HEADER + "<Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\"><Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/><Default Extension=\"xml\" ContentType=\"application/xml\"/><Override PartName=\"/xl/workbook.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml\"/>" + types + "</Types>");
            entry(zip, "_rels/.rels", HEADER + "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\"><Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument\" Target=\"xl/workbook.xml\"/></Relationships>");
            entry(zip, "xl/workbook.xml", HEADER + "<workbook xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\" xmlns:r=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships\"><sheets>" + workbook + "</sheets></workbook>");
            entry(zip, "xl/_rels/workbook.xml.rels", HEADER + "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">" + rels + "</Relationships>");
        }
    }
    private static String csv(JSONArray rows) throws Exception {
        validateRows(rows); StringBuilder result = new StringBuilder();
        for (int r = 0; r < rows.length(); r++) {
            JSONArray row = rows.getJSONArray(r);
            for (int c = 0; c < row.length(); c++) {
                if (c > 0) result.append(','); Object value = row.get(c); String text = value == JSONObject.NULL ? "" : String.valueOf(value);
                if (value instanceof String && text.matches("^[=+@\\-].*")) text = "'" + text;
                result.append('"').append(text.replace("\"", "\"\"")).append('"');
            }
            result.append("\r\n");
        } return result.toString();
    }
    private static void bundle(File file, JSONObject value) throws Exception {
        JSONArray entries = value.getJSONArray("entries");
        if (entries.length() < 1 || entries.length() > 40) throw new IOException("ZIP 应包含1到40个文本文件。");
        Set<String> names = new HashSet<>(); int size = 0;
        try (ZipOutputStream zip = new ZipOutputStream(new FileOutputStream(file), StandardCharsets.UTF_8)) {
            for (int i = 0; i < entries.length(); i++) {
                JSONObject part = entries.getJSONObject(i); String name = LocalFileSpec.safeName(part.getString("name"), true);
                if (!names.add(name.toLowerCase(java.util.Locale.ROOT))) throw new IOException("ZIP 内文件名重复。");
                String content = part.getString("content"); size += content.length(); if (size > 500000) throw new IOException("ZIP 文本内容过大。");
                entry(zip, name, content);
            }
        }
    }
    private LocalDocuments() {}
}
