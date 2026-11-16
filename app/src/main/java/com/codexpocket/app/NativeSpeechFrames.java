package com.codexpocket.app;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Handler;
import android.os.Looper;
import android.util.LruCache;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.HashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

/** Original complete scene pictures for silent moments. Never draws a mouth patch. */
final class NativeSpeechFrames {
    private final String ROOT;
    private final Context context;
    private final Handler ui=new Handler(Looper.getMainLooper());
    private final ExecutorService decode=Executors.newSingleThreadExecutor();
    private final LruCache<String,Bitmap> cache=new LruCache<String,Bitmap>(24*1024*1024){
        protected int sizeOf(String key,Bitmap bitmap){return bitmap.getByteCount();}
    };
    private final HashMap<String,String[]> quiet=new HashMap<>();
    private volatile boolean closed;
    NativeSpeechFrames(Context context,String root){
        this.context=context;ROOT=root;
        try(InputStream input=context.getAssets().open(ROOT+"native-speech.json")){
            ByteArrayOutputStream data=new ByteArrayOutputStream();byte[] buffer=new byte[8192];int n;
            while((n=input.read(buffer))!=-1)data.write(buffer,0,n);
            JSONObject clips=new JSONObject(data.toString("UTF-8")).getJSONObject("clips");
            java.util.Iterator<String> keys=clips.keys();while(keys.hasNext()){
                String kind=keys.next();JSONArray array=clips.getJSONObject(kind).getJSONArray("quietFrames");String[] paths=new String[array.length()];
                for(int i=0;i<paths.length;i++)paths[i]=array.getString(i);quiet.put(kind,paths);
            }
        }catch(Exception ignored){}
    }
    String path(String kind,int millis){
        String[] paths=quiet.get(RoomSpeech.voiceKind(kind));
        return paths==null||paths.length==0?"poster.webp":paths[Math.min(paths.length-1,Math.max(0,(int)(millis*.048f)))];
    }
    void request(String path,Consumer<Bitmap> done){
        if(closed)return;Bitmap cached=cache.get(path);if(cached!=null){done.accept(cached);return;}
        decode.execute(()->{
            Bitmap bitmap=null;try(InputStream in=context.getAssets().open(ROOT+path)){bitmap=BitmapFactory.decodeStream(in);}catch(Exception ignored){}
            if(bitmap==null)try(InputStream in=context.getAssets().open(ROOT+"poster.webp")){bitmap=BitmapFactory.decodeStream(in);}catch(Exception ignored){}
            final Bitmap result=bitmap;
            ui.post(()->{if(closed)return;if(result!=null)cache.put(path,result);done.accept(result);});
        });
    }
    void close(){closed=true;decode.shutdownNow();cache.evictAll();}
}
