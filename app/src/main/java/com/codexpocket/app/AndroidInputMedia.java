package com.codexpocket.app;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.pdf.PdfRenderer;
import android.media.ExifInterface;
import android.os.ParcelFileDescriptor;
import org.json.JSONObject;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

/** Converts images and complete, bounded PDFs to API-compatible image parts locally. */
final class AndroidInputMedia {
    static void prepare(DirectInputs store, JSONObject descriptor) throws Exception {
        String ext=DirectInputs.extension(descriptor.getString("name"));File source=store.path(descriptor.getString("id"));
        if(ext.equals("pdf")){
            try(ParcelFileDescriptor input=ParcelFileDescriptor.open(source,ParcelFileDescriptor.MODE_READ_ONLY);PdfRenderer pdf=new PdfRenderer(input)){
                int count=pdf.getPageCount();if(count<1||count>10)throw new IOException("PDF 每个最多10页，请拆分后添加。");
                for(int i=0;i<count;i++){
                    if(Thread.currentThread().isInterrupted())throw new IOException("附件准备已停止。");
                    try(PdfRenderer.Page page=pdf.openPage(i)){
                        float scale=1800f/Math.max(page.getWidth(),page.getHeight());int width=Math.max(1,Math.round(page.getWidth()*scale)),height=Math.max(1,Math.round(page.getHeight()*scale));
                        Bitmap image=Bitmap.createBitmap(width,height,Bitmap.Config.ARGB_8888);
                        try{image.eraseColor(Color.WHITE);page.render(image,null,null,PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY);writeJpeg(store.newBlob(descriptor,"image","image/jpeg"),image);}finally{image.recycle();}
                    }
                }
                descriptor.put("kind","document").put("summary","PDF · 全部"+count+"页（按页面图像读取）");
            }catch(SecurityException e){throw new IOException("此 PDF 已加密，请解密后发送。");}
        }else if(ext.matches("jpe?g|png|webp|gif|heic|heif|bmp")){
            BitmapFactory.Options options=new BitmapFactory.Options();options.inJustDecodeBounds=true;BitmapFactory.decodeFile(source.getPath(),options);
            if(options.outWidth<1||options.outHeight<1||options.outWidth*(long)options.outHeight>120000000L)throw new IOException("无法读取图片或图片尺寸过大，请转换为 JPG/PNG。");
            options.inSampleSize=1;while(Math.max(options.outWidth,options.outHeight)/options.inSampleSize>2400)options.inSampleSize*=2;
            options.inJustDecodeBounds=false;Bitmap decoded=BitmapFactory.decodeFile(source.getPath(),options);if(decoded==null)throw new IOException("此图片格式无法读取，请转换为 JPG/PNG。");
            Bitmap rotated=decoded,scaled=decoded;
            try{
                Matrix matrix=new Matrix();
                try{
                    int orientation=new ExifInterface(source.getPath()).getAttributeInt(ExifInterface.TAG_ORIENTATION,ExifInterface.ORIENTATION_NORMAL);
                    switch(orientation){
                        case 2:matrix.setScale(-1,1);break;case 3:matrix.setRotate(180);break;case 4:matrix.setScale(1,-1);break;
                        case 5:matrix.setRotate(90);matrix.postScale(-1,1);break;case 6:matrix.setRotate(90);break;
                        case 7:matrix.setRotate(270);matrix.postScale(-1,1);break;case 8:matrix.setRotate(270);break;
                    }
                }catch(IOException ignored){}
                if(!matrix.isIdentity())rotated=Bitmap.createBitmap(decoded,0,0,decoded.getWidth(),decoded.getHeight(),matrix,true);
                float scale=Math.min(1,2048f/Math.max(rotated.getWidth(),rotated.getHeight()));scaled=Bitmap.createScaledBitmap(rotated,Math.max(1,Math.round(rotated.getWidth()*scale)),Math.max(1,Math.round(rotated.getHeight()*scale)),true);
                writeJpeg(store.newBlob(descriptor,"image","image/jpeg"),scaled);
                descriptor.put("kind","image").put("summary",ext.equals("gif")?"图片 · 静态首帧":"图片 · 已优化尺寸");
            }finally{if(scaled!=decoded&&scaled!=rotated)scaled.recycle();if(rotated!=decoded)rotated.recycle();decoded.recycle();}
        }else{
            String text=DocumentText.extract(source,ext);store.addText(descriptor,text,ext.toUpperCase(java.util.Locale.ROOT)+" · 正文已提取");
        }
        store.validate(descriptor);
    }
    private static void writeJpeg(File file,Bitmap source)throws Exception{
        Bitmap opaque=Bitmap.createBitmap(source.getWidth(),source.getHeight(),Bitmap.Config.ARGB_8888);
        try{
            Canvas canvas=new Canvas(opaque);canvas.drawColor(Color.WHITE);canvas.drawBitmap(source,0,0,null);
            try(FileOutputStream out=new FileOutputStream(file)){if(!opaque.compress(Bitmap.CompressFormat.JPEG,88,out))throw new IOException("无法准备图片。");}
            if(file.length()>3L*1024*1024)throw new IOException("图片内容过大，请裁剪或缩小后发送。");
        }finally{opaque.recycle();}
    }
    private AndroidInputMedia(){}
}
