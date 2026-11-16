package com.codexpocket.app;

import android.media.MediaCodec;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import java.io.File;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;

/** Decode the actual clip once; the player clock indexes 20 ms RMS windows. */
final class SpeechEnvelope {
    private final float[] values;
    SpeechEnvelope(float[] values){this.values=values;}
    float at(int millis){int index=Math.max(0,millis/20);return index<values.length?values[index]:0;}
    float forDisplay(int millis){
        // Cover the next display composition (roughly 1-2 frames), keeping
        // current voiced samples until the actual silence/end, never a tail.
        return Math.max(at(millis),at(millis+32));
    }
    static SpeechEnvelope decode(File file) throws Exception {
        MediaExtractor extractor=new MediaExtractor();MediaCodec codec=null;
        try{
            extractor.setDataSource(file.getAbsolutePath());int track=-1;MediaFormat format=null;
            for(int i=0;i<extractor.getTrackCount();i++){MediaFormat f=extractor.getTrackFormat(i);if(f.getString(MediaFormat.KEY_MIME).startsWith("audio/")){track=i;format=f;break;}}
            if(track<0)throw new Exception("语音文件没有音轨");extractor.selectTrack(track);
            codec=MediaCodec.createDecoderByType(format.getString(MediaFormat.KEY_MIME));codec.configure(format,null,null,0);codec.start();
            int rate=format.getInteger(MediaFormat.KEY_SAMPLE_RATE),channels=format.getInteger(MediaFormat.KEY_CHANNEL_COUNT),encoding=2;
            boolean inputDone=false,outputDone=false;long deadline=android.os.SystemClock.elapsedRealtime()+10000;
            ArrayList<Double> energy=new ArrayList<>();ArrayList<Integer> counts=new ArrayList<>();MediaCodec.BufferInfo info=new MediaCodec.BufferInfo();
            while(!outputDone){
                if(Thread.currentThread().isInterrupted()||android.os.SystemClock.elapsedRealtime()>deadline)throw new Exception("语音分析中断");
                if(!inputDone){int index=codec.dequeueInputBuffer(10000);if(index>=0){ByteBuffer input=codec.getInputBuffer(index);int size=extractor.readSampleData(input,0);if(size<0){codec.queueInputBuffer(index,0,0,0,MediaCodec.BUFFER_FLAG_END_OF_STREAM);inputDone=true;}else{codec.queueInputBuffer(index,0,size,extractor.getSampleTime(),0);extractor.advance();}}}
                int index=codec.dequeueOutputBuffer(info,10000);
                if(index==MediaCodec.INFO_OUTPUT_FORMAT_CHANGED){MediaFormat f=codec.getOutputFormat();rate=f.getInteger(MediaFormat.KEY_SAMPLE_RATE);channels=f.getInteger(MediaFormat.KEY_CHANNEL_COUNT);encoding=f.containsKey(MediaFormat.KEY_PCM_ENCODING)?f.getInteger(MediaFormat.KEY_PCM_ENCODING):2;}
                else if(index>=0){ByteBuffer buffer=codec.getOutputBuffer(index).order(ByteOrder.LITTLE_ENDIAN);buffer.position(info.offset);buffer.limit(info.offset+info.size);int sample=0;
                    while(buffer.remaining()>=(encoding==4?4:2)){double value=encoding==4?buffer.getFloat():buffer.getShort()/32768.0;int bin=(int)((info.presentationTimeUs/1000.0+sample*1000.0/(rate*channels))/20);if(bin>=0&&bin<6000){while(energy.size()<=bin){energy.add(0.0);counts.add(0);}energy.set(bin,energy.get(bin)+value*value);counts.set(bin,counts.get(bin)+1);}sample++;}
                    outputDone=(info.flags&MediaCodec.BUFFER_FLAG_END_OF_STREAM)!=0;codec.releaseOutputBuffer(index,false);
                }
            }
            float[] result=new float[energy.size()];double max=.025;
            for(int i=0;i<result.length;i++){result[i]=(float)Math.sqrt(energy.get(i)/Math.max(1,counts.get(i)));max=Math.max(max,result[i]);}
            for(int i=0;i<result.length;i++)result[i]=result[i]<.006?0:(float)Math.min(1,Math.pow(result[i]/max,.6));
            return new SpeechEnvelope(result);
        }finally{if(codec!=null){try{codec.stop();}catch(Exception ignored){}codec.release();}extractor.release();}
    }
}
