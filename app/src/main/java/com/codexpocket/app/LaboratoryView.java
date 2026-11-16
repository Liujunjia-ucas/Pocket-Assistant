package com.codexpocket.app;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.media.AudioAttributes;
import android.media.MediaPlayer;
import android.os.Handler;
import android.os.Looper;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import android.view.Gravity;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.*;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.File;
import java.util.ArrayDeque;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Laboratory entry and the independent, foreground-only whale voice conversation. */
final class LaboratoryView extends LinearLayout {
    static final int MICROPHONE_PERMISSION=711;
    private final Activity activity;
    private final ThemePalette p;
    private final SharedPreferences prefs;
    private final java.util.function.Consumer<String> settings;
    private CompanionProfile character;
    private boolean choosingCharacter;
    private final Handler ui=new Handler(Looper.getMainLooper());
    private final ExecutorService network=Executors.newSingleThreadExecutor();
    private final ExecutorService speech=Executors.newSingleThreadExecutor();
    private volatile int generation;
    private boolean active,closed,inConversation,ready,asr,continuous,turnBusy,networkDone,speechBusy,phoneReady,phoneFailed;
    private String reply="",characterStyle;
    private float characterSize;
    private int queuedChars;
    private final ArrayDeque<String> speechQueue=new ArrayDeque<>();
    private DirectApi client;
    private LocalCompanion local;
    private PhoneRecognition recognition;
    private int recognitionMisses;
    private MediaPlayer player;
    private File playingFile;
    private TextToSpeech phone;
    private VolcSpeech cloudSpeech;
    private VolcVoiceSettings voiceSettings;
    private long phoneInitAt;
    private WhaleView whale;
    private ImageView entryPoster;
    private WhaleView actionPreview;
    private AlertDialog actionDialog;
    private WhaleView chooserPreview;
    private AlertDialog characterDialog;
    private AlertDialog historyDialog;
    private AlertDialog memoryDialog;
    private int memoryRevision;
    private SpeechEnvelope envelope;
    private int playbackRevision;
    private String phoneUtterance="";
    private File phoneFile;
    private TextView status,subtitle,connectionLabel;
    private Button call,interrupt;
    private EditText composer;
    private LinearLayout transcript;
    private ScrollView transcriptScroll;
    private TextView currentReply;
    private LinearLayout roomPanel,roomHead,roomControls;
    private java.util.function.Consumer<Boolean> conversationListener=value->{};
    void onConversation(java.util.function.Consumer<Boolean> listener){conversationListener=listener;}
    boolean isInConversation(){return inConversation;}

    LaboratoryView(Activity activity,ThemePalette palette,SharedPreferences prefs,java.util.function.Consumer<String> settings){
        super(activity);this.activity=activity;this.p=palette;this.prefs=prefs;this.settings=settings;
        if(!prefs.getBoolean("labRoomIntroduced",false))prefs.edit().putString("labCharacterStyle","mature-room").putFloat("labCharacterSize",1f).putBoolean("labRoomIntroduced",true).apply();
        character=CompanionProfile.from(prefs.getString("labCompanion","xiyue"));loadCharacter();
        setOrientation(VERTICAL);setBackgroundColor(p.bg);entry();
        File[] cached=activity.getCacheDir().listFiles();if(cached!=null)for(File f:cached)if((f.getName().startsWith("whale-")||f.getName().startsWith("companion-voice-")||f.getName().startsWith("voice-preview-"))&&(f.getName().endsWith(".mp3")||f.getName().endsWith(".wav")))f.delete();
    }
    @Override protected void onMeasure(int widthSpec,int heightSpec){
        if(inConversation&&!CompanionProfile.isRoom(characterStyle)&&whale!=null&&whale.getLayoutParams()!=null){int available=MeasureSpec.getSize(heightSpec)-getPaddingTop()-getPaddingBottom();whale.getLayoutParams().height=Math.max(0,Math.min(dp(180),Math.min((int)(available*.60f),available-dp(245))));}
        if(inConversation&&CompanionProfile.isRoom(characterStyle)&&roomPanel!=null){
            int available=MeasureSpec.getSize(heightSpec);boolean compact=available<dp(440);
            status.setVisibility(compact?GONE:VISIBLE);subtitle.setVisibility(compact?GONE:VISIBLE);roomControls.setVisibility(available<dp(310)?GONE:VISIBLE);
            int top=dp(compact?8:14);if(roomPanel.getPaddingTop()!=top)roomPanel.setPadding(dp(16),top,dp(16),dp(12));
            roomHead.getLayoutParams().height=dp(compact?64:72);
        }
        super.onMeasure(widthSpec,heightSpec);
    }
    private int dp(float value){return (int)(value*getResources().getDisplayMetrics().density+.5f);}
    private LinearLayout col(){LinearLayout v=new LinearLayout(activity);v.setOrientation(VERTICAL);return v;}
    private LinearLayout row(){LinearLayout v=new LinearLayout(activity);v.setGravity(Gravity.CENTER_VERTICAL);return v;}
    private LinearLayout.LayoutParams lp(int w,int h){return new LinearLayout.LayoutParams(w<0?w:dp(w),h<0?h:dp(h));}
    private GradientDrawable shape(int color,int radius){GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(radius));return d;}
    private TextView text(String value,int size,int color){TextView v=new TextView(activity);v.setText(value);v.setTextSize(size);v.setTextColor(color);v.setFontFeatureSettings("kern");return v;}
    private Button button(String value,boolean accent){Button b=new Button(activity);b.setText(value);PocketUi.button(b,p,accent);return b;}
    private void gap(LinearLayout parent,int height){parent.addView(new View(activity),lp(1,height));}
    private void toast(String value){Toast.makeText(activity,value,Toast.LENGTH_LONG).show();}
    private void entry(){
        choosingCharacter=false;catalogue();
    }
    private void characters(){choosingCharacter=true;catalogue();}
    private void catalogue(){
        removeAllViews();inConversation=false;conversationListener.accept(false);setPadding(dp(22),dp(18),dp(22),dp(16));
        ScrollView scroll=new ScrollView(activity);LinearLayout body=col();scroll.addView(body);addView(scroll,new LinearLayout.LayoutParams(-1,-1));
        TextView title=text(choosingCharacter?"对话模式":"实验室",28,p.text);body.addView(title);gap(body,16);
        body.addView(text("语音陪伴 · 原创光环界面",15,p.muted));gap(body,20);
        companionCard(body,CompanionProfile.XIYUE,"01","温柔细腻，慢慢听你说。","");
        companionCard(body,CompanionProfile.DRAGON,"02","从容沉稳，有自己的主见。","");
        body.addView(text("社区版不包含鲸鱼娘、龙娘图片或动画。角色为虚构陪伴设定，与模型厂商无官方关系。",12,p.muted));
    }
    private void companionCard(LinearLayout body,CompanionProfile profile,String number,String description,String poster){
        LinearLayout card=col();card.setPadding(dp(20),dp(20),dp(20),dp(20));card.setBackground(shape(p.panel,24));
        card.addView(text(number+" / "+profile.name,22,p.text));gap(card,12);card.addView(text(description,14,p.muted));gap(card,16);
        Button enter=button("进入对话",true);card.addView(enter,lp(-1,48));enter.setOnClickListener(v->{selectCharacter(profile);enterConversation();});body.addView(card,lp(-1,-2));gap(body,18);
    }
    private void updateEntryPoster(){if(!inConversation&&!closed)catalogue();}
    private void loadCharacter(){
        local=null;try{local=character.open(activity.getFilesDir());}catch(Exception e){toast("手机陪伴记录无法读取，请检查存储空间。原记录已保留。");}
        characterStyle="community-orb";
        
        characterSize=prefs.getFloat(character.sizePreference,1f);
    }
    private void selectCharacter(CompanionProfile next){
        if(character==next)return;hangup();character=next;prefs.edit().putString("labCompanion",next.id).apply();loadCharacter();if(phoneReady){selectPhoneVoice();applyVoiceProfile();}
    }
    private void applyVoiceProfile(){if(phone!=null){phone.setSpeechRate(character.rate);phone.setPitch(character.pitch);}}
    private void enterConversation(){
        entryPoster=null;
        cancelTurn();removeAllViews();setPadding(dp(16),dp(4),dp(16),dp(12));inConversation=true;ready=false;
        LinearLayout head=row();Button back=button("‹",false);head.addView(back,lp(40,40));back.setContentDescription("返回角色选择");back.setOnClickListener(v->back());
        LinearLayout name=col();name.setGravity(Gravity.CENTER);TextView title=text(character.name,18,p.text);title.setTypeface(null,Typeface.BOLD);name.addView(title);connectionLabel=text("手机直连",10,p.muted);name.addView(connectionLabel);head.addView(name,new LinearLayout.LayoutParams(0,-2,1));
        Button menu=button("",false);menu.setVisibility(INVISIBLE);head.addView(menu,lp(40,40));addView(head,lp(-1,48));
        whale=new WhaleView(activity,p);if(!CompanionProfile.isRoom(characterStyle))RoundedDialogBuilder.clip(whale,24);whale.style(characterStyle,characterSize);whale.active(active);addView(whale,lp(-1,180));
        status=text("准备见面",12,p.accent);status.setGravity(Gravity.CENTER);addView(status,lp(-1,23));
        subtitle=text("点开始语音，她就会认真听你说。",13,p.muted);subtitle.setGravity(Gravity.CENTER);subtitle.setPadding(dp(6),0,dp(6),dp(8));subtitle.setMaxLines(2);addView(subtitle,lp(-1,-2));
        transcriptScroll=new ScrollView(activity);transcriptScroll.setFillViewport(true);transcriptScroll.setBackground(shape(p.panel,20));transcript=col();transcript.setPadding(dp(14),dp(12),dp(14),dp(12));transcriptScroll.addView(transcript);addView(transcriptScroll,new LinearLayout.LayoutParams(-1,0,1));
        gap(this,10);LinearLayout controls=row();call=button("开始语音",true);controls.addView(call,new LinearLayout.LayoutParams(0,dp(48),1));call.setOnClickListener(v->{if(continuous)hangup();else beginCall();});interrupt=button("打断",false);LinearLayout.LayoutParams ip=lp(75,48);ip.leftMargin=dp(8);controls.addView(interrupt,ip);interrupt.setEnabled(false);interrupt.setOnClickListener(v->{cancelTurn();if(continuous)listenSoon(300);else phase("idle","已打断","可以继续打字，或开始语音。");});addView(controls);
        gap(this,8);LinearLayout input=row();composer=new EditText(activity);composer.setSingleLine(false);composer.setMaxLines(3);composer.setTextSize(14);composer.setTextColor(p.text);composer.setHintTextColor(p.muted);composer.setHint("也可以打字聊聊…");composer.setPadding(dp(14),dp(9),dp(12),dp(9));composer.setBackground(shape(p.panel,16));input.addView(composer,new LinearLayout.LayoutParams(0,-2,1));
        Button send=button("发送",false);LinearLayout.LayoutParams sp=lp(65,44);sp.leftMargin=dp(6);input.addView(send,sp);send.setOnClickListener(v->sendText());addView(input,lp(-1,-2));
        if(CompanionProfile.isRoom(characterStyle))immersiveLayout(head,controls,input);
        conversationListener.accept(true);
        refreshConnection();
    }
    private void immersiveLayout(LinearLayout head,LinearLayout controls,LinearLayout input){
        roomHead=head;roomControls=controls;
        for(View view:new View[]{head,whale,status,subtitle,transcriptScroll,controls,input})removeView(view);
        removeAllViews();setPadding(0,0,0,0);
        FrameLayout stage=new FrameLayout(activity);stage.setBackgroundColor(0xff282a35);addView(stage,new LinearLayout.LayoutParams(-1,-1));
        stage.addView(whale,new FrameLayout.LayoutParams(-1,-1));
        head.setPadding(dp(12),dp(8),dp(12),dp(8));
        head.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,new int[]{0x66111520,0x00111520}));
        LinearLayout name=(LinearLayout)head.getChildAt(1);((TextView)name.getChildAt(0)).setTextColor(0xfff7f8ff);connectionLabel.setTextColor(0xffd4dbea);
        for(int i:new int[]{0,2}){Button b=(Button)head.getChildAt(i);b.setTextColor(0xfff7f8ff);b.setBackground(shape(0x66323a4b,16));}
        Button history=button("记录",false);history.setTextColor(0xfff7f8ff);history.setBackground(shape(0x66323a4b,16));LinearLayout.LayoutParams hp=lp(52,40);hp.rightMargin=dp(6);head.addView(history,2,hp);history.setOnClickListener(v->showHistory());
        FrameLayout.LayoutParams top=new FrameLayout.LayoutParams(-1,dp(72),Gravity.TOP);stage.addView(head,top);
        LinearLayout panel=col();roomPanel=panel;panel.setPadding(dp(16),dp(14),dp(16),dp(12));panel.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,new int[]{0x00111520,0x44111520,0x66111520}));
        status.setTextColor(0xffb8d5ff);panel.addView(status,lp(-1,23));
        subtitle.setTextColor(0xfff4f6ff);subtitle.setMaxLines(2);subtitle.setEllipsize(android.text.TextUtils.TruncateAt.END);subtitle.setLineSpacing(dp(2),1);panel.addView(subtitle,lp(-1,-2));gap(panel,8);
        call.setTextColor(0xfff7f8ff);call.setBackground(shape(0x995273e8,18));
        interrupt.setTextColor(0xfff7f8ff);interrupt.setBackground(shape(0x77414b60,18));panel.addView(controls,lp(-1,48));gap(panel,8);
        composer.setTextColor(0xfff4f6ff);composer.setHintTextColor(0xffd0d6e5);composer.setBackground(shape(0x88303a50,16));
        Button send=(Button)input.getChildAt(1);send.setTextColor(0xfff7f8ff);send.setBackground(shape(0x88414b60,16));panel.addView(input,lp(-1,-2));
        stage.addView(panel,new FrameLayout.LayoutParams(-1,-2,Gravity.BOTTOM));
        stage.addOnLayoutChangeListener((v,l,t,r,b,ol,ot,or,ob)->{if(whale!=null&&inConversation)whale.safeInsets(head.getHeight()+dp(6),panel.getHeight()+dp(8));});
        panel.addOnLayoutChangeListener((v,l,t,r,b,ol,ot,or,ob)->{if(whale!=null&&inConversation)whale.safeInsets(head.getHeight()+dp(6),panel.getHeight()+dp(8));});
    }
    private void showHistory(){
        if(historyDialog!=null)return;
        if(transcriptScroll.getParent() instanceof android.view.ViewGroup)((android.view.ViewGroup)transcriptScroll.getParent()).removeView(transcriptScroll);
        AlertDialog dialog=new RoundedDialogBuilder(activity).setTitle("我们的对话").setView(transcriptScroll).setPositiveButton("回到客厅",null).create();
        historyDialog=dialog;dialog.setOnDismissListener(d->{if(transcriptScroll.getParent() instanceof android.view.ViewGroup)((android.view.ViewGroup)transcriptScroll.getParent()).removeView(transcriptScroll);historyDialog=null;});dialog.show();
        if(dialog.getWindow()!=null)dialog.getWindow().setLayout(-1,(int)(getResources().getDisplayMetrics().heightPixels*.78f));scrollBottom();
    }
    void setActive(boolean value){boolean entered=value&&!active;active=value;if(actionPreview!=null)actionPreview.active(value);if(whale!=null)whale.active(value);if(chooserPreview!=null)chooserPreview.active(value);if(!value){memoryRevision++;if(voiceSettings!=null)voiceSettings.pause();if(memoryDialog!=null)memoryDialog.dismiss();if(actionDialog!=null)actionDialog.dismiss();hangup();}else if(inConversation&&(entered||!ready))refreshConnection();}
    void refreshConnection(){
        if(!inConversation||closed)return;
        hangup();
        ready=false;asr=PhoneRecognition.available(activity);transcript.removeAllViews();
        try{if(local==null)throw new java.io.IOException("手机陪伴存储未就绪，请检查存储空间。");JSONArray messages=local.messages();for(int i=Math.max(0,messages.length()-40);i<messages.length();i++){JSONObject m=messages.getJSONObject(i);addMessage("user".equals(m.optString("role")),m.optString("text"));}
            PhoneApiConfig config=PhoneApiConfig.load(activity,character.provider);ready=true;connectionLabel.setText(config.model+" · 手机直连");phase("idle","我在这里",asr?"点开始语音，或打字和我聊聊。":"可以打字聊天；语音需要手机中文识别服务。");whale.event("welcome","");
        }catch(Exception e){connectionLabel.setText("等待 API 设置");phase("idle","还差一点准备",message(e));}
        call.setEnabled(ready);scrollBottom();
    }
    private boolean valid(int expected){return !closed&&expected==generation;}
    private String message(Exception e){if(e instanceof DirectApi.Failure)return e.getMessage();if(e instanceof java.net.UnknownHostException||e instanceof java.net.ConnectException)return "手机无法访问模型平台，请检查网络。";String value=e.getMessage();return e instanceof java.io.IOException&&value!=null&&value.length()<180?value:"请求未完成，请检查手机网络、API 配置和存储空间。";}
    private void phase(String animation,String label,String hint){if(!inConversation)return;whale.phase(animation);status.setText(label);subtitle.setText(hint);}
    private TextView addMessage(boolean user,String value){
        LinearLayout box=col();box.setPadding(dp(12),dp(9),dp(12),dp(10));box.setBackground(shape(user?p.userBubble:p.chip,14));
        TextView label=text(user?"你":character.name,10,user?p.accent:p.muted);box.addView(label);TextView content=text(value,14,p.text);content.setLineSpacing(dp(3),1);content.setTextIsSelectable(true);box.addView(content);
        LinearLayout.LayoutParams params=lp(-1,-2);params.bottomMargin=dp(9);transcript.addView(box,params);
        while(transcript.getChildCount()>42)transcript.removeViewAt(0);scrollBottom();return content;
    }
    private void scrollBottom(){if(transcriptScroll!=null)transcriptScroll.post(()->transcriptScroll.fullScroll(ScrollView.FOCUS_DOWN));}
    private void beginCall(){
        if(!ready){toast("请先在 Android 页设置 API，再刷新连接。");return;}
        asr=PhoneRecognition.available(activity);
        if(!asr){recognitionSettings("手机当前没有可调用的语音识别服务。输入法自带语音按钮不一定对其他 APP 提供识别接口。");return;}
        if(activity.checkSelfPermission(Manifest.permission.RECORD_AUDIO)!=PackageManager.PERMISSION_GRANTED){activity.requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO},MICROPHONE_PERMISSION);return;}
        cancelTurn();recognitionMisses=0;continuous=true;call.setText("结束语音");initPhone();listenSoon(150);
    }
    void onPermissionsResult(int code,int[] grants){if(code!=MICROPHONE_PERMISSION)return;if(grants.length>0&&grants[0]==PackageManager.PERMISSION_GRANTED){if(active&&inConversation)beginCall();}else toast("未获得麦克风权限，仍可用文字和角色聊天。");}
    private void listenSoon(long delay){final int expected=generation;ui.postDelayed(()->{if(valid(expected)&&continuous&&active&&inConversation)listen();},delay);}
    private void listen(){
        if(!active||!continuous||turnBusy)return;
        final int expected=++generation;turnBusy=true;interrupt.setEnabled(true);phase("listening","正在听你说…","说完停顿一下，我会回应你。");
        PhoneRecognition next=new PhoneRecognition();recognition=next;next.start(activity,new PhoneRecognition.Listener(){
            public void preparing(){if(valid(expected))phase("listening","正在准备听音…","首次准备需要几秒，出现「正在听你说」后再开口。");}
            public void ready(){if(valid(expected))phase("listening","正在听你说…","说完停顿一下，我会回应你。");}
            public void text(String value){if(!valid(expected))return;recognition=null;if(value.isEmpty()){retryRecognition(expected,"没有听清，再说一次吧。");return;}recognitionMisses=0;if(value.length()>2000){failTurn(expected,"这次说得有点长，分开聊吧。");return;}startReply(value,expected);}
            public void partial(String value){if(valid(expected)&&!value.isEmpty())subtitle.setText(value);}
            public void level(float value){if(valid(expected))whale.level(value);}
            public void error(String error,boolean retry){if(!valid(expected))return;recognition=null;if(retry)retryRecognition(expected,error);else{failTurn(expected,error);if(active&&inConversation)recognitionSettings(error);}}
        });
    }
    private void retryRecognition(int expected,String hint){if(!valid(expected))return;turnBusy=false;if(++recognitionMisses>=3){failTurn(expected,"暂时没有听清，点击开始语音可以再聊，也可以打字。");return;}phase("idle","我在听",hint);listenSoon(900);}
    private void sendText(){
        String value=composer.getText().toString().trim();if(value.isEmpty())return;if(value.length()>2000){toast("一次最多输入 2000 字");return;}
        if(!ready){toast("请先在 Android 页设置 API，可在对话设置中打开。");return;}
        cancelTurn();initPhone();composer.setText("");composer.clearFocus();InputMethodManager keyboard=(InputMethodManager)activity.getSystemService(Activity.INPUT_METHOD_SERVICE);if(keyboard!=null)keyboard.hideSoftInputFromWindow(composer.getWindowToken(),0);
        int expected=generation;startReply(value,expected);
    }
    private void startReply(String text,int expected){
        if(!valid(expected))return;final PhoneApiConfig config;final JSONArray context;try{config=PhoneApiConfig.load(activity,character.provider);context=local.context(text);}catch(Exception e){failTurn(expected,message(e));return;}
        client=config.client();final DirectApi turn=client;connectionLabel.setText(config.model+" · 手机直连");turnBusy=true;networkDone=false;reply="";queuedChars=0;speechQueue.clear();whale.expression("neutral");addMessage(true,text);currentReply=addMessage(false,"");
        interrupt.setEnabled(true);phase("thinking","让我想一想…","");
        whale.event("user",text);
        network.execute(()->{
            try{
                String answer=turn.chat(config.model,config.protocol,context,1000,value->{if(value.length()>6000){turn.cancel();return;}ui.post(()->{if(!valid(expected))return;reply=value;currentReply.setText(reply);scrollBottom();});});
                if(answer.length()>6000)throw new java.io.IOException("回复过长，请缩短问题后重试。");
                ui.post(()->{if(!valid(expected))return;reply=answer;currentReply.setText(answer);try{local.record(text,answer);}catch(Exception e){toast("本次对话未能保存到手机，请检查存储空间。");}networkDone=true;queueSentences(expected,true);finishIfReady(expected);});
            }catch(Exception e){ui.post(()->failTurn(expected,message(e)));}
        });
    }
    private void queueSentences(int expected,boolean flush){
        // Complete reply, one synthesis request and one continuous audio file.
        // Punctuation and streamed fragments never create separate playback jobs.
        if(!flush)return;
        if(queuedChars<reply.length()){String text=reply.substring(queuedChars).trim();if(!text.isEmpty())speechQueue.add(text);queuedChars=reply.length();}pumpSpeech(expected);
    }
    private void pumpSpeech(int expected){
        if(!valid(expected)||speechBusy)return;
        if(speechQueue.isEmpty()){finishIfReady(expected);return;}
        String text=speechQueue.removeFirst();if(text.isEmpty()){pumpSpeech(expected);return;}
        speechBusy=true;phase("thinking","让我想一想…","");
        speakPhone(text,expected);
    }
    private void play(File audio,int expected,String text){
        releasePlayer();playingFile=audio;
        try{
            MediaPlayer next=new MediaPlayer();player=next;next.setAudioAttributes(new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build());next.setDataSource(audio.getAbsolutePath());
            next.setOnPreparedListener(v->{if(valid(expected)&&player==v){whale.prepareSpeech(text,()->{if(!valid(expected)||player!=v||!active||!inConversation)return;try{v.start();audibleStart(text);int revision=++playbackRevision;tickAudio(expected,revision,v);}catch(IllegalStateException e){releasePlayer();phoneSpeechFailed(expected,"手机语音播放失败，可以继续看文字。");}},()->{if(valid(expected)&&player==v){releasePlayer();phoneSpeechFailed(expected,"角色动画暂未准备好，本次请看文字，可以重新发送。");}});}else if(player==v)releasePlayer();});
            next.setOnCompletionListener(v->{if(valid(expected)&&player==v){releasePlayer();speechFinished(expected);}});
            next.setOnErrorListener((v,what,extra)->{if(valid(expected)&&player==v){releasePlayer();phoneSpeechFailed(expected,"手机语音播放失败，可以继续看文字。");}return true;});next.prepareAsync();
        }catch(Exception e){releasePlayer();phoneSpeechFailed(expected,"手机语音播放失败，可以继续看文字。");}
    }
    private void initPhone(){
        if(phone!=null)return;
        phoneInitAt=android.os.SystemClock.uptimeMillis();
        phone=new TextToSpeech(activity,result->ui.post(()->{if(closed||phone==null)return;if(result==TextToSpeech.SUCCESS){int supported=phone.setLanguage(Locale.SIMPLIFIED_CHINESE);phoneReady=supported!=TextToSpeech.LANG_MISSING_DATA&&supported!=TextToSpeech.LANG_NOT_SUPPORTED;selectPhoneVoice();applyVoiceProfile();}phoneFailed=!phoneReady;}));
        phone.setOnUtteranceProgressListener(new UtteranceProgressListener(){
            public void onStart(String id){}
            public void onDone(String id){ui.post(()->{
                int expected=utteranceGeneration(id);if(!id.equals(phoneUtterance)||!valid(expected))return;
                File audio=phoneFile;phoneFile=null;phoneUtterance="";String text=phoneText;if(audio==null)return;
                speech.execute(()->{SpeechEnvelope levels;try{levels=SpeechEnvelope.decode(audio);}catch(Exception e){levels=null;}final SpeechEnvelope decoded=levels;
                    ui.post(()->{if(!valid(expected)){audio.delete();return;}envelope=decoded;play(audio,expected,text);});});
            });}
            public void onError(String id){ui.post(()->{int expected=utteranceGeneration(id);if(!id.equals(phoneUtterance)||!valid(expected))return;if(phoneFile!=null){phoneFile.delete();phoneFile=null;}phoneSpeechFailed(expected,"手机语音暂不可用，请检查中文语音包，可以继续打字聊天。");});}
        });
    }
    private void speakPhone(String text,int expected){
        if(VolcVoiceSettings.enabled(prefs)){speakCloud(text,expected);return;}
        initPhone();if(!phoneReady&&!phoneFailed){if(android.os.SystemClock.uptimeMillis()-phoneInitAt>5000)phoneFailed=true;else {ui.postDelayed(()->{if(valid(expected))speakPhone(text,expected);},150);return;}}
        if(!phoneReady){phoneSpeechFailed(expected,"手机没有可用的普通话语音包，请在系统文字转语音设置中安装；可以继续打字聊天。");return;}
        if(text.length()>TextToSpeech.getMaxSpeechInputLength()){phoneSpeechFailed(expected,"回复过长，本次请看文字。");return;}
        String id=expected+":"+UUID.randomUUID();phoneUtterance=id;phoneText=text;
        try{phoneFile=File.createTempFile("whale-",".wav",activity.getCacheDir());int result=phone.synthesizeToFile(text,new android.os.Bundle(),phoneFile,id);if(result==TextToSpeech.ERROR){phoneFile.delete();phoneFile=null;phoneSpeechFailed(expected,"手机中文语音暂不可用，可以继续打字聊天。");return;}
            ui.postDelayed(()->{if(valid(expected)&&id.equals(phoneUtterance)){phoneUtterance="";phone.stop();if(phoneFile!=null){phoneFile.delete();phoneFile=null;}phoneSpeechFailed(expected,"手机语音等待超时，请检查语音包或网络。");}},60000);
        }catch(Exception e){if(phoneFile!=null){phoneFile.delete();phoneFile=null;}phoneSpeechFailed(expected,"无法生成手机语音，请检查语音包与存储空间。");}
    }
    private void speakCloud(String text,int expected){
        final VolcSpeech.Config config;try{config=VolcVoiceSettings.load(activity,prefs);config.validate();}catch(Exception e){phoneSpeechFailed(expected,"请在音色设置中填写有效的火山语音配置。");return;}
        final VolcSpeech request=new VolcSpeech();cloudSpeech=request;final boolean dragon=character.dragon();
        speech.execute(()->{File output=null;try{
            output=File.createTempFile("companion-voice-",".mp3",activity.getCacheDir());request.synthesize(config,dragon,text,output);
            SpeechEnvelope levels;try{levels=SpeechEnvelope.decode(output);}catch(Exception e){levels=null;}
            final File audio=output;final SpeechEnvelope decoded=levels;
            ui.post(()->{if(cloudSpeech==request)cloudSpeech=null;if(!valid(expected)){audio.delete();return;}envelope=decoded;play(audio,expected,text);});
        }catch(Exception e){
            if(output!=null)output.delete();final String error=e instanceof java.io.IOException&&e.getMessage()!=null&&(e.getMessage().startsWith("火山")||e.getMessage().startsWith("语音")||e.getMessage().startsWith("回复"))?e.getMessage():"火山语音连接失败，请检查手机网络和音色设置。";
            ui.post(()->{if(cloudSpeech==request)cloudSpeech=null;if(valid(expected))phoneSpeechFailed(expected,error);});
        }});
    }
    private void phoneSpeechFailed(int expected,String error){if(!valid(expected))return;continuous=false;if(call!=null)call.setText("开始语音");speechFinished(expected);phase("idle","文字回复已收到",error);toast(error);}
    private String phoneText="";
    private int utteranceGeneration(String id){try{return Integer.parseInt(id.split(":")[0]);}catch(Exception ignored){return -1;}}
    private void audibleStart(String text){phase("speaking",character.name+"在说话…",text);whale.event("answer",text);}
    private void tickAudio(int expected,int revision,MediaPlayer source){
        if(!valid(expected)||revision!=playbackRevision||player!=source||!inConversation)return;
        try{int position=source.getCurrentPosition();float value=source.isPlaying()?(envelope==null?.45f:envelope.forDisplay(position)):0;whale.audio(value,position);}catch(Exception ignored){whale.audio(0,0);}
        if(whale!=null)whale.postOnAnimation(()->tickAudio(expected,revision,source));
    }
    private void speechFinished(int expected){if(!valid(expected))return;playbackRevision++;phoneUtterance="";whale.audio(0,0);whale.phase("thinking");speechBusy=false;pumpSpeech(expected);}
    private void finishIfReady(int expected){
        if(!valid(expected)||!networkDone||speechBusy||!speechQueue.isEmpty())return;
        if(!turnBusy)return;turnBusy=false;interrupt.setEnabled(false);phase("idle",continuous?"轮到你啦":"我在这里",continuous?"马上继续听你说…":"可以继续打字，或开始语音。");whale.event("complete",reply);if(continuous)listenSoon(650);
    }
    private void failTurn(int expected,String error){
        if(!valid(expected)||!inConversation)return;
        continuous=false;cancelTurn();call.setText("开始语音");phase("idle","这次没有接上",error);whale.event("error",error);toast(error);
    }
    private void releasePlayer(){playbackRevision++;if(player!=null){try{player.reset();player.release();}catch(Exception ignored){}player=null;}if(playingFile!=null){playingFile.delete();playingFile=null;}}
    private void cancelTurn(){
        if(cloudSpeech!=null){cloudSpeech.cancel();cloudSpeech=null;}
        generation++;phoneUtterance="";if(client!=null){client.cancel();client=null;}if(recognition!=null){recognition.cancel();recognition=null;}releasePlayer();if(phone!=null)phone.stop();if(phoneFile!=null){phoneFile.delete();phoneFile=null;}if(whale!=null){whale.audio(0,0);whale.phase("idle");whale.event("interrupt","");}speechQueue.clear();speechBusy=false;turnBusy=false;networkDone=false;if(interrupt!=null)interrupt.setEnabled(false);
    }
    private void hangup(){continuous=false;cancelTurn();if(inConversation){call.setText("开始语音");phase("idle","语音已结束","麦克风已关闭，仍可使用文字对话。");}}
    boolean back(){if(!inConversation){if(choosingCharacter){entry();return true;}return false;}if(historyDialog!=null)historyDialog.dismiss();hangup();whale.close();whale=null;characters();return true;}
    void openSettings(){labSettings();}
    private void labSettings(){
        String[] options={"音色设置","安卓 API 设置","开始新对话","本地记忆与性格","语音识别设置","隐私与数据"};
        new RoundedDialogBuilder(activity).setTitle("对话设置").setItems(options,(d,which)->{
            if(which==0)chooseVoice();else if(which==1){hangup();settings.accept(character.provider);}else if(which==2)clearHistory();else if(which==3)showMemory();else if(which==4)recognitionSettings("");else PrivacySettings.show(activity,p);
        }).setNegativeButton("关闭",null).show();
    }
    private void recognitionSettings(String error){
        hangup();
        android.content.SharedPreferences saved=activity.getSharedPreferences("phone_asr",Activity.MODE_PRIVATE);
        java.util.List<String> ids=new java.util.ArrayList<>(),names=new java.util.ArrayList<>();
        ids.add(PhoneRecognition.AUTO);names.add("自动选择手机系统语音服务");
        if(OfflineRecognition.available(activity)){ids.add(PhoneRecognition.INTERNAL);names.add("内置中文识别 · 离线，无需系统语音服务");}
        if(PhoneRecognition.onDevice(activity)){ids.add(PhoneRecognition.DEVICE);names.add("优先使用本机离线识别");}
        for(android.content.ComponentName component:PhoneRecognition.services(activity)){
            String label=component.getPackageName();
            try{label=activity.getPackageManager().getServiceInfo(component,0).loadLabel(activity.getPackageManager()).toString();}catch(Exception ignored){}
            ids.add(component.flattenToString());names.add(label+" · "+component.getPackageName());
        }
        LinearLayout body=col();body.setPadding(dp(20),dp(8),dp(20),dp(12));
        String hint=(error.isEmpty()?"":error+"\n\n")+"社区版不附带离线识别模型。请选择手机可用的语音服务；系统服务可能联网，取决于其设置和隐私条款。没有兼容服务时，可使用输入法语音或文字。";
        body.addView(text(hint,13,p.text));gap(body,10);
        String selected=saved.getString("service",PhoneRecognition.AUTO);int checked=Math.max(0,ids.indexOf(selected));
        RadioGroup choices=new RadioGroup(activity);choices.setOrientation(VERTICAL);
        for(int i=0;i<ids.size();i++){final String id=ids.get(i);RadioButton item=new RadioButton(activity);item.setId(View.generateViewId());item.setText(names.get(i));item.setTextColor(p.text);item.setTextSize(13);item.setPadding(dp(4),dp(6),dp(4),dp(6));choices.addView(item,lp(-1,-2));item.setChecked(i==checked);item.setOnCheckedChangeListener((view,on)->{if(on)saved.edit().putString("service",id).apply();});}
        body.addView(choices);gap(body,10);
        if(!PhoneRecognition.available(activity))body.addView(text("尚未检测到可调用服务。若系统未提供此接口，需安装兼容的中文识别服务；目前可用输入法的语音按钮将内容输入文本框。",12,p.muted));
        Button voice=button("打开系统语音输入设置",false);body.addView(voice,lp(-1,44));voice.setOnClickListener(v->openRecognitionSettings(android.provider.Settings.ACTION_VOICE_INPUT_SETTINGS,android.provider.Settings.ACTION_INPUT_METHOD_SETTINGS));gap(body,8);
        Button microphone=button("检查麦克风权限",false);body.addView(microphone,lp(-1,44));microphone.setOnClickListener(v->{if(activity.checkSelfPermission(Manifest.permission.RECORD_AUDIO)!=PackageManager.PERMISSION_GRANTED)activity.requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO},MICROPHONE_PERMISSION);else{try{activity.startActivity(new android.content.Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,android.net.Uri.parse("package:"+activity.getPackageName())));}catch(Exception ignored){toast("请在系统应用权限中检查麦克风权限和系统麦克风开关。");}}});
        ScrollView scroll=new ScrollView(activity);scroll.addView(body);
        new RoundedDialogBuilder(activity).setTitle("语音识别设置").setView(scroll).setPositiveButton("完成",(d,w)->{asr=PhoneRecognition.available(activity);}).show();
    }
    private void openRecognitionSettings(String... actions){for(String action:actions)try{activity.startActivity(new android.content.Intent(action));return;}catch(Exception ignored){}toast("请手动打开系统设置 → 语言与输入法 → 语音输入。");}
    private void chooseVoice(){
        hangup();new RoundedDialogBuilder(activity).setTitle(character.name+"的声音").setItems(new String[]{"火山语音 · 甜妹 / 御姐","手机系统音色"},(d,n)->{
            if(n==0){if(voiceSettings!=null)voiceSettings.close();voiceSettings=new VolcVoiceSettings(activity,prefs,p);voiceSettings.show(()->hangup());}
            else chooseSystemVoice();
        }).setNegativeButton("取消",null).show();
    }
    private void chooseSystemVoice(){
        initPhone();if(!phoneReady){toast("正在准备手机中文语音；如未安装，请在系统文字转语音设置中添加普通话语音包。");return;}
        java.util.List<android.speech.tts.Voice> voices=phoneVoices();java.util.List<String> names=new java.util.ArrayList<>();names.add("自动选择普通话");for(android.speech.tts.Voice v:voices)names.add(v.getName()+(v.isNetworkConnectionRequired()?" · 联网":" · 本机"));
        String saved=prefs.getString(character.voicePreference,"");int selected=0;for(int i=0;i<voices.size();i++)if(voices.get(i).getName().equals(saved))selected=i+1;
        new RoundedDialogBuilder(activity).setTitle(character.name+"的手机音色").setSingleChoiceItems(names.toArray(new String[0]),selected,(d,which)->{hangup();prefs.edit().putBoolean("labVolcEnabled",false).putString(character.voicePreference,which==0?"":voices.get(which-1).getName()).apply();selectPhoneVoice();d.dismiss();toast("已切换到手机系统音色。");}).setNegativeButton("取消",null).show();
    }
    private java.util.List<android.speech.tts.Voice> phoneVoices(){
        java.util.List<android.speech.tts.Voice> result=new java.util.ArrayList<>();try{if(phone.getVoices()!=null)for(android.speech.tts.Voice v:phone.getVoices()){Locale locale=v.getLocale();String country=locale.getCountry();if(locale.getLanguage().equals("zh")&&(country.equals("CN")||country.isEmpty())&&(v.getFeatures()==null||!v.getFeatures().contains("notInstalled")))result.add(v);}}catch(Exception ignored){}
        result.sort(java.util.Comparator.comparingInt((android.speech.tts.Voice v)->v.isNetworkConnectionRequired()?1:0).thenComparing(android.speech.tts.Voice::getName));return result;
    }
    private void selectPhoneVoice(){java.util.List<android.speech.tts.Voice> voices=phoneVoices();String saved=prefs.getString(character.voicePreference,"");for(android.speech.tts.Voice v:voices)if(v.getName().equals(saved)){phone.setVoice(v);phoneReady=true;return;}if(!voices.isEmpty()){phone.setVoice(voices.get(0));phoneReady=true;}else{android.speech.tts.Voice current=phone.getVoice();if(current!=null){String country=current.getLocale().getCountry();if(country.equals("TW")||country.equals("HK"))phoneReady=false;}}}
    private void showMemory(){
        hangup();if(memoryDialog!=null)memoryDialog.dismiss();memoryRevision++;
        try{if(local==null)throw new java.io.IOException("手机陪伴存储未就绪。");memoryContent(local.view());}catch(Exception e){toast(message(e));}
    }
    private void memoryContent(JSONObject data){
        LinearLayout body=col();body.setPadding(dp(20),dp(8),dp(20),dp(16));
        body.addView(text("汐月和凌雪共享你明确说过的信息。删除、清空或暂停记忆对两人同时生效；性格和聊天记录分别保留。",13,p.muted));gap(body,12);
        Switch enabled=new Switch(activity);enabled.setText("使用并学习共享记忆");enabled.setTextColor(p.text);enabled.setChecked(data.optBoolean("enabled",true));body.addView(enabled,lp(-1,48));
        body.addView(text(character.name+"的性格",15,p.text));EditText personality=new EditText(activity);personality.setTextColor(p.text);personality.setTextSize(13);personality.setMinLines(4);personality.setMaxLines(9);personality.setPadding(dp(12),dp(10),dp(12),dp(10));personality.setBackground(shape(p.chip,16));personality.setText(data.optString("personality"));body.addView(personality,lp(-1,-2));gap(body,12);
        body.addView(text("共同记住的事",15,p.text));JSONArray facts=data.optJSONArray("facts");
        if(facts==null||facts.length()==0)body.addView(text("还没有明确的个人信息。聊到时她会慢慢记住，不用填写资料。",13,p.muted));
        if(facts!=null)for(int i=0;i<facts.length();i++){JSONObject fact=facts.optJSONObject(i);if(fact==null)continue;gap(body,10);LinearLayout item=row();LinearLayout content=col();TextView value=text(fact.optString("value")+("age".equals(fact.optString("category"))?" 岁（陈述时）":""),14,p.text);content.addView(value);content.addView(text("原话："+fact.optString("evidence"),12,p.muted));String date=fact.optString("reportedAt");content.addView(text(date.length()>10?date.substring(0,10):date,11,p.muted));item.addView(content,new LinearLayout.LayoutParams(0,-2,1));Button forget=button("删除",false);item.addView(forget,lp(62,38));final String id=fact.optString("id");forget.setOnClickListener(v->new RoundedDialogBuilder(activity).setTitle("忘记这条信息？").setMessage("将不再用于之后的对话。原始对话存档保留。未发送的性格修改不会保存。").setPositiveButton("忘记",(d,w)->{try{changeMemory(new JSONObject().put("action","forget").put("id",id));}catch(Exception e){toast(message(e));}}).setNegativeButton("取消",null).show());body.addView(item);}
        gap(body,18);Button clear=button("清空已学习的记忆",false);body.addView(clear,lp(-1,44));clear.setOnClickListener(v->new RoundedDialogBuilder(activity).setTitle("清空长期记忆？").setMessage("清空个人信息与相关话题索引，保留原始对话和性格。旧历史不会自动恢复已删除的信息。").setPositiveButton("清空",(d,w)->{try{changeMemory(new JSONObject().put("action","clear"));}catch(Exception e){toast(message(e));}}).setNegativeButton("取消",null).show());
        gap(body,12);TextView path=text("性格、记忆与对话保存在此手机，卸载应用会清除。",11,p.muted);body.addView(path);
        ScrollView scroll=new ScrollView(activity);scroll.addView(body);AlertDialog dialog=new RoundedDialogBuilder(activity).setTitle("长期记忆与性格").setView(scroll).setPositiveButton("保存设置",null).setNegativeButton("关闭",null).create();memoryDialog=dialog;dialog.setOnDismissListener(d->{if(memoryDialog==dialog)memoryDialog=null;});dialog.show();dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{String value=personality.getText().toString().trim();if(value.length()<10||value.length()>4000){toast("性格描述需要 10–4000 字");return;}try{changeMemory(new JSONObject().put("action","settings").put("enabled",enabled.isChecked()).put("personality",value));}catch(Exception e){toast(message(e));}});
    }
    private void changeMemory(JSONObject change){
        try{JSONObject result=local.update(change);if(memoryDialog!=null)memoryDialog.dismiss();memoryContent(result);toast("已保存到手机本地。");}catch(Exception e){toast(message(e));}
    }
    private void chooseCharacter(){toast("社区版使用原创光环界面。");}
    private void previewActions(){toast("社区版不包含第三方角色动作。");}
    private void spriteActions(){
        String[] names={"待机 · 呼吸 / 眨眼 / 歪头 / 摆尾","向右移动","向左移动","挥手","跳跃","失败 · 慌张","等待","工作 · 思考","审查","自动环视 16 个方向","指定环视方向","中性姿态"};
        String[] ids={"idle","right","left","wave","jump","failed","wait","work","review","look","directions","neutral"};
        new RoundedDialogBuilder(activity).setTitle("DeepSeek娘 · 动作预览").setItems(names,(d,index)->{if("directions".equals(ids[index]))chooseLookDirection();else previewGesture(ids[index]);}).setNegativeButton("关闭",null).show();
    }
    private void chooseLookDirection(){
        String[] directions={"上 · 0°","右上 · 22.5°","右上 · 45°","右上 · 67.5°","右 · 90°","右下 · 112.5°","右下 · 135°","右下 · 157.5°","下 · 180°","左下 · 202.5°","左下 · 225°","左下 · 247.5°","左 · 270°","左上 · 292.5°","左上 · 315°","左上 · 337.5°"};
        new RoundedDialogBuilder(activity).setTitle("环视方向").setItems(directions,(d,index)->previewGesture("look-"+index)).setNegativeButton("关闭",null).show();
    }
    private void previewGesture(String kind){
        if(inConversation){if(whale!=null)whale.gesture(kind);return;}
        if(actionDialog!=null)actionDialog.dismiss();
        WhaleView sample=new WhaleView(activity,p);sample.style(characterStyle,1);sample.active(active);sample.gesture(kind);actionPreview=sample;
        FrameLayout frame=new FrameLayout(activity);frame.addView(sample,new FrameLayout.LayoutParams(-1,dp(480)));
        AlertDialog dialog=new RoundedDialogBuilder(activity).setTitle("动作预览").setView(frame).setPositiveButton("关闭",null).create();actionDialog=dialog;
        dialog.setOnDismissListener(d->{sample.close();if(actionPreview==sample)actionPreview=null;if(actionDialog==dialog)actionDialog=null;});dialog.show();
    }
    private void clearHistory(){
        new RoundedDialogBuilder(activity).setTitle("开始一段新对话？").setMessage("旧对话保留在此手机，性格与已学习的记忆继续沿用。").setPositiveButton("新对话",(d,w)->{hangup();try{local.newConversation();if(inConversation)refreshConnection();}catch(Exception e){toast(message(e));}}).setNegativeButton("取消",null).show();
    }
    private void about(){new RoundedDialogBuilder(activity).setTitle("社区版对话模式").setMessage("使用原创程序绘制的语音光环，不分发第三方角色图片、动画或音色文件。聊天和语音将发送到你自行选择的服务，请查看隐私说明。").setPositiveButton("关闭",null).show();}
    private void showNotice(){about();}
    void close(){if(closed)return;hangup();closed=true;memoryRevision++;if(voiceSettings!=null)voiceSettings.close();if(memoryDialog!=null)memoryDialog.dismiss();if(historyDialog!=null)historyDialog.dismiss();if(characterDialog!=null)characterDialog.dismiss();if(actionDialog!=null)actionDialog.dismiss();if(whale!=null)whale.close();if(phone!=null){phone.shutdown();phone=null;}OfflineRecognition.releaseModel();network.shutdownNow();speech.shutdownNow();ui.removeCallbacksAndMessages(null);}
}
