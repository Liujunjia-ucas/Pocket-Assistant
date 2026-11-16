package com.codexpocket.app;

import android.app.Activity;
import android.Manifest;
import android.content.pm.PackageManager;
import android.content.Intent;
import android.content.ComponentName;
import android.content.pm.ResolveInfo;
import android.content.pm.ServiceInfo;
import android.os.Bundle;
import android.provider.Settings;
import android.speech.RecognitionListener;
import android.speech.RecognitionService;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import java.util.ArrayList;
import java.util.List;

/** One foreground utterance, with bounded fallback across installed recognizers. */
final class PhoneRecognition {
    interface Listener{void text(String value);default void text(String value,boolean review){text(value);}void partial(String value);void level(float value);void error(String message,boolean retry);default void preparing(){}default void ready(){}}
    private SpeechRecognizer recognizer;
    private OfflineRecognition offline;
    private final android.os.Handler ui=new android.os.Handler(android.os.Looper.getMainLooper());
    private Runnable timeout,startTimeout;
    private boolean ended;
    private int revision,candidateIndex;
    private final List<String> candidates=new ArrayList<>();
    private Activity activity;
    private Listener callback;
    static final String AUTO="auto",DEVICE="device",DEFAULT="default",INTERNAL="internal";
    static List<ComponentName> services(Activity activity){
        List<ComponentName> result=new ArrayList<>();
        try{for(ResolveInfo resolved:activity.getPackageManager().queryIntentServices(new Intent(RecognitionService.SERVICE_INTERFACE),0)){
            ServiceInfo service=resolved.serviceInfo;
            if(service!=null&&service.enabled&&service.exported&&(service.applicationInfo==null||service.applicationInfo.enabled))result.add(new ComponentName(service.packageName,service.name));
        }}catch(RuntimeException ignored){}
        return result;
    }
    static boolean onDevice(Activity activity){try{return android.os.Build.VERSION.SDK_INT>=31&&SpeechRecognizer.isOnDeviceRecognitionAvailable(activity);}catch(RuntimeException ignored){return false;}}
    static boolean available(Activity activity){if(OfflineRecognition.available(activity))return true;try{return onDevice(activity)||SpeechRecognizer.isRecognitionAvailable(activity)||!services(activity).isEmpty();}catch(RuntimeException ignored){return false;}}
    private void add(String value){if(value!=null&&!value.isEmpty()&&!candidates.contains(value))candidates.add(value);}
    private static String systemService(Activity activity){String setting=Settings.Secure.getString(activity.getContentResolver(),"voice_recognition_service");ComponentName component=setting==null?null:ComponentName.unflattenFromString(setting);return component==null?"":component.flattenToString();}
    void start(Activity activity,Listener callback){
        start(activity,callback,false);
    }
    void start(Activity activity,Listener callback,boolean preferInternal){
        cancel();ended=false;this.activity=activity;this.callback=callback;candidates.clear();candidateIndex=0;
        if(!microphoneGranted()){finishError(SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS);return;}
        List<ComponentName> installed=services(activity);
        String chosen=activity.getSharedPreferences("phone_asr",Activity.MODE_PRIVATE).getString("service",AUTO);
        if(preferInternal&&AUTO.equals(chosen)&&OfflineRecognition.available(activity))add(INTERNAL);
        if(INTERNAL.equals(chosen)){if(OfflineRecognition.available(activity))add(INTERNAL);}
        else if(DEVICE.equals(chosen)){if(onDevice(activity))add(DEVICE);}
        else for(ComponentName service:installed)if(service.flattenToString().equals(chosen))add(chosen);
        // Many mainland phones lack a downloaded Mandarin offline model.
        // Use the default online-capable service without forcing offline mode.
        try{String system=systemService(activity);
            for(ComponentName service:installed)if(service.flattenToString().equals(system))add(system);
            if(SpeechRecognizer.isRecognitionAvailable(activity))add(DEFAULT);
        }catch(RuntimeException ignored){}
        if(onDevice(activity))add(DEVICE);
        for(ComponentName service:installed)add(service.flattenToString());
        if(OfflineRecognition.available(activity))add(INTERNAL);
        try{String system=systemService(activity);if(candidates.contains(system))candidates.remove(DEFAULT);}catch(RuntimeException ignored){}
        attempt(0);
    }
    private void attempt(int previousError){
        if(ended)return;
        release();final int ticket=revision;
        if(candidateIndex>=candidates.size()){finishError(previousError);return;}
        String next=candidates.get(candidateIndex++);
        if(INTERNAL.equals(next)){
            offline=new OfflineRecognition();offline.start(activity,new Listener(){
                private boolean valid(){return !ended&&ticket==revision;}
                public void preparing(){if(valid())callback.preparing();}
                public void ready(){if(valid())callback.ready();}
                public void text(String text){text(text,false);}
                public void text(String text,boolean review){if(!valid())return;ended=true;release();callback.text(text,review);}
                public void partial(String text){if(valid())callback.partial(text);}
                public void level(float value){if(valid())callback.level(value);}
                public void error(String error,boolean retry){if(!valid())return;ended=true;release();callback.error(error,retry);}
            });return;
        }
        try{
            recognizer=DEVICE.equals(next)&&android.os.Build.VERSION.SDK_INT>=31?SpeechRecognizer.createOnDeviceSpeechRecognizer(activity):DEFAULT.equals(next)?SpeechRecognizer.createSpeechRecognizer(activity):SpeechRecognizer.createSpeechRecognizer(activity,ComponentName.unflattenFromString(next));
            recognizer.setRecognitionListener(new RecognitionListener(){
                private boolean valid(){return !ended&&ticket==revision;}
                private void ready(){if(startTimeout!=null){ui.removeCallbacks(startTimeout);startTimeout=null;}}
                public void onReadyForSpeech(Bundle params){if(valid()){ready();callback.ready();}}
                public void onBeginningOfSpeech(){if(valid())ready();}
                public void onBufferReceived(byte[] audio){}
                public void onEndOfSpeech(){}
                public void onEvent(int type,Bundle params){}
                public void onRmsChanged(float rms){if(valid())callback.level(Math.max(0,Math.min(1,(rms+2)/12f)));}
                public void onPartialResults(Bundle result){if(valid()){ready();callback.partial(first(result));}}
                public void onResults(Bundle result){if(!valid())return;ended=true;String value=first(result);float[] scores=result==null?null:result.getFloatArray(SpeechRecognizer.CONFIDENCE_SCORES);boolean review=scores!=null&&scores.length>0&&SpeechText.uncertain(scores[0]);release();callback.text(value,review);}
                public void onError(int error){if(!valid())return;
                    if(error==SpeechRecognizer.ERROR_NO_MATCH||error==SpeechRecognizer.ERROR_SPEECH_TIMEOUT){ended=true;release();callback.error("没有听清，再说一次吧。",true);}
                    else if(error==SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS){if(!microphoneGranted())finishError(error);else ui.post(()->{if(valid())fallbackInternal(error);});}
                    else if(error==SpeechRecognizer.ERROR_AUDIO||error==10){ui.post(()->{if(valid())fallbackInternal(error);});}
                    else ui.post(()->{if(valid())attempt(error);});
                }
            });
            Intent intent=new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
            intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
            intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE,"zh-CN");intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE,"zh-CN");
            intent.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS,true);intent.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS,3);
            intent.putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS,1200L);
            intent.putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS,1200L);
            if(android.os.Build.VERSION.SDK_INT>=33)intent.putStringArrayListExtra("android.speech.extra.BIASING_STRINGS",new ArrayList<>(SpeechText.HINTS));
            intent.putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE,DEVICE.equals(next));
            intent.putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE,activity.getPackageName());
            startTimeout=()->{if(!ended&&ticket==revision)attempt(SpeechRecognizer.ERROR_CLIENT);};ui.postDelayed(startTimeout,8000);
            timeout=()->{if(!ended&&ticket==revision){ended=true;release();callback.error("语音识别等待超时，请重新开始。",false);}};ui.postDelayed(timeout,45000);
            recognizer.startListening(intent);
        }catch(SecurityException error){if(!microphoneGranted())finishError(SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS);else ui.post(()->{if(!ended&&ticket==revision)fallbackInternal(SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS);});}
        catch(RuntimeException error){ui.post(()->{if(!ended&&ticket==revision)attempt(SpeechRecognizer.ERROR_CLIENT);});}
    }
    private boolean microphoneGranted(){return activity.checkSelfPermission(Manifest.permission.RECORD_AUDIO)==PackageManager.PERMISSION_GRANTED;}
    private void fallbackInternal(int error){int local=candidates.indexOf(INTERNAL);if(local>=candidateIndex){candidates.remove(local);candidates.add(candidateIndex,INTERNAL);release();final int ticket=revision;ui.postDelayed(()->{if(!ended&&ticket==revision)attempt(error);},250);}else finishError(error);}
    private void finishError(int code){
        if(ended)return;ended=true;release();String message;
        if(code==SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS)message=microphoneGranted()?"App 麦克风已授权，但系统语音服务拒绝访问。请检查语音服务权限或改用输入法语音。":"App 尚未获得录音权限，请在系统应用权限中允许麦克风。";
        else if(code==SpeechRecognizer.ERROR_AUDIO)message="麦克风无法录音，请检查权限、系统麦克风开关，以及其他应用是否正在占用麦克风。";
        else if(code==SpeechRecognizer.ERROR_NETWORK||code==SpeechRecognizer.ERROR_NETWORK_TIMEOUT)message="已尝试可用的语音服务，但识别网络连接失败。请在「语音识别设置」中更换服务或下载离线中文包。";
        else if(code==12||code==13)message="语音服务没有可用的普通话模型，请在「语音识别设置」中启用中文识别或下载普通话离线包。";
        else if(code==10)message="系统语音服务请求过于频繁，请稍后再试。";
        else if(candidates.isEmpty())message="手机未提供可调用的语音识别服务，请在「语音识别设置」中启用或安装中文语音服务。";
        else message="手机语音服务启动失败，请在「语音识别设置」中更换服务，或检查系统语音输入是否可用。";
        callback.error(message+(code>0?"（错误 "+code+"）":""),false);
    }
    private static String first(Bundle bundle){if(bundle==null)return "";ArrayList<String> values=bundle.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);return values==null||values.isEmpty()?"":SpeechText.clean(values.get(0));}
    private void release(){revision++;if(offline!=null){offline.cancel();offline=null;}if(timeout!=null){ui.removeCallbacks(timeout);timeout=null;}if(startTimeout!=null){ui.removeCallbacks(startTimeout);startTimeout=null;}SpeechRecognizer current=recognizer;recognizer=null;if(current!=null){try{current.cancel();}catch(RuntimeException ignored){}try{current.destroy();}catch(RuntimeException ignored){}}}
    void cancel(){ended=true;release();}
}
