package com.codexpocket.app;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.graphics.pdf.PdfDocument;
import android.text.StaticLayout;
import android.text.TextPaint;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

/** A4 text PDF with Android's own CJK font fallback, wrapping and pagination. */
final class LocalPdf {
    static void write(File file, String content) throws Exception {
        TextPaint paint = new TextPaint(Paint.ANTI_ALIAS_FLAG); paint.setColor(Color.BLACK); paint.setTextSize(12); paint.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
        PdfDocument document = new PdfDocument(); PdfDocument.Page page = null; int number = 0, y = 52;
        try {
            page = document.startPage(new PdfDocument.PageInfo.Builder(595, 842, ++number).create());
            for (String paragraph : content.replace("\r\n", "\n").split("\n", -1)) {
                if (Thread.currentThread().isInterrupted()) throw new IOException("文件生成已停止。");
                boolean heading = paragraph.matches("^#{1,6} .*" );
                String text = heading ? paragraph.replaceFirst("^#{1,6} +", "") : paragraph.replaceFirst("^\\s*[-*] +", "• ");
                paint.setTextSize(heading ? 17 : 12); paint.setTypeface(Typeface.create("sans-serif", heading ? Typeface.BOLD : Typeface.NORMAL));
                StaticLayout layout = StaticLayout.Builder.obtain(text.isEmpty() ? " " : text, 0, Math.max(1, text.length()), paint, 491).setIncludePad(false).setLineSpacing(4, 1).build();
                for (int line = 0; line < layout.getLineCount(); line++) {
                    int top = layout.getLineTop(line), height = layout.getLineBottom(line) - top;
                    if (y + height > 790) {
                        document.finishPage(page); page = null;
                        if (number >= 80) throw new IOException("PDF 页数过多，请分成几个文档。");
                        page = document.startPage(new PdfDocument.PageInfo.Builder(595, 842, ++number).create()); y = 52;
                    }
                    Canvas canvas = page.getCanvas(); canvas.save(); canvas.clipRect(52, y, 543, y + height); canvas.translate(52, y - top); layout.draw(canvas); canvas.restore(); y += height;
                }
                y += heading ? 8 : 4;
            }
            document.finishPage(page); page = null;
            try (FileOutputStream out = new FileOutputStream(file)) { document.writeTo(out); }
        } finally { if (page != null) document.finishPage(page); document.close(); }
    }
    private LocalPdf() {}
}
