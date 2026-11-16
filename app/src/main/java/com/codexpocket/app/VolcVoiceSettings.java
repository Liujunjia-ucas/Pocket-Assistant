package com.codexpocket.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.SharedPreferences;
import android.graphics.drawable.GradientDrawable;
import android.media.AudioAttributes;
import android.media.MediaPlayer;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.widget.*;
import java.io.File;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;

/** Shared service credentials, separate real neural speakers for the two roles. */
final class VolcVoiceSettings {
    private final Activity activity;private final SharedPreferences prefs;private final ThemePalette p;
    private final Handler ui=new Handler(Looper.getMainLooper());private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private AlertDialog dialog;private VolcSpeech request;private MediaPlayer player;private File preview;
    private boolean closed;private int revision;private TextView result;private EditText key,app,resource,sweet,cool;private Switch enabled;private String savedKey="";
    VolcVoiceSettings(Activity a,SharedPreferences prefs,ThemePalette p){activity=a;this.prefs=prefs;this.p=p;}
    static boolean enabled(SharedPreferences prefs){return prefs.getBoolean("labVolcEnabled",false);}
    static VolcSpeech.Config load(Activity a,SharedPreferences prefs)throws Exception{
        return new VolcSpeech.Config(prefs.getString("labVolcApp",""),SecretStore.load(a,"volc_speech"),prefs.getString("labVolcResource","seed-tts-2.0"),prefs.getString("labVolcSweet",VolcSpeech.SWEET),prefs.getString("labVolcCool",VolcSpeech.COOL));
    }
    void show(Runnable saved){
        try{savedKey=SecretStore.load(activity,"volc_speech");}catch(Exception ignored){}
        LinearLayout body=new LinearLayout(activity);body.setOrientation(LinearLayout.VERTICAL);body.setPadding(dp(20),dp(8),dp(20),dp(12));
        enabled=new Switch(activity);enabled.setText("使用火山语音");enabled.setTextColor(p.text);enabled.setChecked(enabled(prefs));body.addView(enabled);
        label(body,"新版填写语音服务 API Key；旧版填写 Access Token 和 App ID。Ark 绘图 Key 与语音服务凭据可能不同。",13);
        key=field(body,"API Key / Access Token", "");key.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_PASSWORD);key.setHint(savedKey.isEmpty()?"在手机输入语音服务凭据":"已保存，留空保留");key.setSaveEnabled(false);
        app=field(body,"App ID（仅旧版，新版留空）",prefs.getString("labVolcApp",""));app.setInputType(InputType.TYPE_CLASS_NUMBER);
        resource=field(body,"资源 ID",prefs.getString("labVolcResource","seed-tts-2.0"));
        sweet=field(body,"汐月 · 甜妹音色 ID",prefs.getString("labVolcSweet",VolcSpeech.SWEET));
        preset(body,"选择汐月音色",sweet,new String[]{"甜美桃子 2.0","甜美小源 2.0","小何 2.0"},new String[]{VolcSpeech.SWEET,"zh_female_tianmeixiaoyuan_uranus_bigtts","zh_female_xiaohe_uranus_bigtts"});
        cool=field(body,"凌雪 · 御姐音色 ID",prefs.getString("labVolcCool",VolcSpeech.COOL));
        preset(body,"选择凌雪音色",cool,new String[]{"高冷御姐 2.0","Vivi 2.0"},new String[]{VolcSpeech.COOL,"zh_female_vv_uranus_bigtts"});
        LinearLayout tests=new LinearLayout(activity);Button a=button("试听汐月"),b=button("试听凌雪");tests.addView(a,new LinearLayout.LayoutParams(0,dp(48),1));tests.addView(b,new LinearLayout.LayoutParams(0,dp(48),1));body.addView(tests);a.setOnClickListener(v->test(false));b.setOnClickListener(v->test(true));
        result=label(body,"默认搭配：甜美桃子 2.0 / 高冷御姐 2.0。资源与音色须在账号中开通。",13);
        ScrollView scroll=new ScrollView(activity);scroll.addView(body);
        dialog=new RoundedDialogBuilder(activity).setTitle("火山语音 · 双角色音色").setView(scroll).setPositiveButton("保存",null).setNegativeButton("取消",null).create();dialog.setOnDismissListener(d->release());dialog.show();
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{try{
            VolcSpeech.Config config=current();if(enabled.isChecked())config.validate();
            SecretStore.save(activity,"volc_speech",config.key);
            if(!prefs.edit().putBoolean("labVolcEnabled",enabled.isChecked()).putString("labVolcApp",config.appId).putString("labVolcResource",config.resource).putString("labVolcSweet",config.sweet).putString("labVolcCool",config.cool).commit())throw new Exception("配置保存失败，请重试。");
            saved.run();dialog.dismiss();Toast.makeText(activity,"两人的音色配置已保存",Toast.LENGTH_SHORT).show();
        }catch(Exception e){result.setText(safe(e));}});
    }
    private VolcSpeech.Config current(){return new VolcSpeech.Config(app.getText().toString(),key.getText().length()==0?savedKey:key.getText().toString(),resource.getText().toString(),sweet.getText().toString(),cool.getText().toString());}
    private void test(boolean dragon){
        VolcSpeech.Config config=current();try{config.validate();}catch(Exception e){result.setText(safe(e));return;}
        stop();final int ticket=++revision;VolcSpeech next=new VolcSpeech();request=next;result.setText("准备试听…");
        worker.execute(()->{File audio=null;try{
            audio=File.createTempFile("voice-preview-",".mp3",activity.getCacheDir());File output=audio;
            next.synthesize(config,dragon,dragon?"我是凌雪。嗯，听着呢。今天想和我聊些什么？":"我是汐月。你来啦，我一直在这里陪着你呢。",output);
            ui.post(()->{if(closed||ticket!=revision){output.delete();return;}preview=output;try{
                player=new MediaPlayer();player.setAudioAttributes(new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build());player.setDataSource(output.getAbsolutePath());
                player.setOnPreparedListener(v->{if(!closed&&ticket==revision&&player==v){v.start();result.setText(dragon?"凌雪 · 御姐声线":"汐月 · 甜妹声线");}});
                player.setOnCompletionListener(v->{if(player==v){stop();result.setText("试听结束");}});
                player.setOnErrorListener((v,w,e)->{if(player==v){stop();result.setText("试听播放失败，请重试。");}return true;});player.prepareAsync();
            }catch(Exception e){stop();result.setText("试听播放失败，请重试。");}});
        }catch(Exception e){if(audio!=null)audio.delete();ui.post(()->{if(!closed&&ticket==revision)result.setText(safe(e));});}});
    }
    private static String safe(Exception e){return e instanceof java.net.SocketTimeoutException?"火山语音连接超时，请检查网络后重试。":e instanceof java.net.UnknownHostException?"无法连接火山语音，请检查手机网络。":e instanceof java.io.IOException&&e.getMessage()!=null&&e.getMessage().startsWith("火山")?e.getMessage():e instanceof java.io.IOException&&e.getMessage()!=null&&e.getMessage().startsWith("请")?e.getMessage():"语音试听失败，请检查凭据、资源 ID 和音色权限。";}
    private void stop(){revision++;if(request!=null){request.cancel();request=null;}if(player!=null){try{player.release();}catch(Exception ignored){}player=null;}if(preview!=null){preview.delete();preview=null;}}
    private void release(){closed=true;stop();worker.shutdownNow();}
    void pause(){stop();if(result!=null&&!closed)result.setText("试听已停止");}
    void close(){if(dialog!=null&&dialog.isShowing())dialog.dismiss();else release();}
    private TextView label(LinearLayout body,String text,int size){TextView v=new TextView(activity);v.setText(text);v.setTextSize(size);v.setTextColor(p.text);v.setPadding(0,dp(8),0,dp(6));body.addView(v);return v;}
    private EditText field(LinearLayout body,String name,String value){label(body,name,13);EditText v=new EditText(activity);v.setText(value);v.setSingleLine(true);v.setTextSize(13);v.setTextColor(p.text);v.setHintTextColor(p.muted);v.setPadding(dp(12),dp(8),dp(12),dp(8));GradientDrawable bg=new GradientDrawable();bg.setColor(p.chip);bg.setCornerRadius(dp(14));v.setBackground(bg);body.addView(v,new LinearLayout.LayoutParams(-1,dp(48)));return v;}
    private Button button(String title){Button b=new Button(activity);b.setText(title);b.setTextColor(p.accent);b.setAllCaps(false);GradientDrawable bg=new GradientDrawable();bg.setColor(p.chip);bg.setCornerRadius(dp(16));b.setBackground(bg);return b;}
    private void preset(LinearLayout body,String title,EditText target,String[] names,String[] ids){Button b=button(title);body.addView(b,new LinearLayout.LayoutParams(-1,dp(44)));b.setOnClickListener(v->new RoundedDialogBuilder(activity).setTitle(title).setItems(names,(d,n)->{target.setText(ids[n]);resource.setText("seed-tts-2.0");}).setNegativeButton("取消",null).show());}
    private int dp(int value){return Math.round(value*activity.getResources().getDisplayMetrics().density);}
}
