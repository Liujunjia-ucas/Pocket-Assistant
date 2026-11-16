package com.codexpocket.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.SharedPreferences;
import android.content.Intent;
import android.net.Uri;
import android.database.Cursor;
import android.provider.OpenableColumns;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.*;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Standalone phone chat; no computer connection, pairing or Tailscale dependency. */
final class AndroidChatView extends LinearLayout {
    private final Activity activity;
    private final ThemePalette p;
    private final SharedPreferences prefs;
    private final Runnable appearance;
    private Runnable assistant=()->{};
    private final boolean assistantMode;
    private String stateKey(String key){return assistantMode?"assistant."+key:key;}
    private int pickerCode(boolean image){return (image?PICK_IMAGES:PICK_INPUTS)+(assistantMode?20:0);}
    private String replyText(String value,boolean streaming){String visible=LocalFileSpec.display(value,streaming);return assistantMode?AssistantActions.display(visible,streaming):visible;}
    void onAssistant(Runnable action){assistant=action;}
    private java.util.function.Consumer<String> communicationSource=source->{};
    void onCommunicationSource(java.util.function.Consumer<String> action){communicationSource=action;}
    private final Handler ui = new Handler(Looper.getMainLooper());
    private final ExecutorService network = Executors.newSingleThreadExecutor();
    private final List<MathReplyView> formulaViews = new ArrayList<>();
    private DirectChats store;
    private LocalArtifacts artifacts;
    private LocalFileSave fileSave;
    private DirectInputs inputs;
    private JSONArray draftInputs=new JSONArray();
    private final List<String> pendingInputs=new ArrayList<>();
    private final List<String> inputErrors=new ArrayList<>();
    private HorizontalScrollView inputScroll;
    private LinearLayout inputStrip;
    private static final int PICK_INPUTS=7231,PICK_IMAGES=7232;
    private final ExecutorService files = Executors.newSingleThreadExecutor();
    private JSONObject chat, streamingMessage;
    private DirectApi request, modelRequest, webRequest;
    private AlertDialog settingsDialog;
    private LinearLayout messages;
    private ScrollView scroll;
    private String wallpaperProvider="";
    private java.util.function.Consumer<String> wallpaperListener;
    private EditText composer;
    private TextView modelLabel, status, streamingText, webToggle;
    private Button send;
    private boolean busy, exporting;
    private volatile boolean closed;
    private volatile int revision;
    private static final class Run { volatile String text = ""; volatile boolean cancelled; volatile JSONObject web; }
    private Run activeRun;
    private String provider = "deepseek";
    private long lastSaved;
    private boolean updatePosted;

    AndroidChatView(Activity activity, ThemePalette palette, Runnable appearance) {
        this(activity,palette,appearance,false);
    }
    AndroidChatView(Activity activity, ThemePalette palette, Runnable appearance,boolean assistantMode) {
        super(activity); this.activity = activity; this.p = palette; this.appearance = appearance;
        this.assistantMode=assistantMode;
        prefs = activity.getSharedPreferences("android_direct", Activity.MODE_PRIVATE);
        provider = validProvider(prefs.getString(stateKey("provider"), prefs.getString("provider","deepseek")));
        setOrientation(VERTICAL); setBackgroundColor(android.graphics.Color.TRANSPARENT); setPadding(dp(12), dp(2), dp(12), dp(6));
        build();
        try {
            store = new DirectChats(new File(activity.getFilesDir(), assistantMode?"assistant-chat":"android-chat"));
            artifacts = new LocalArtifacts(new File(activity.getFilesDir(), "android-artifacts"));
            fileSave = new LocalFileSave(activity, artifacts,assistantMode?7240:LocalFileSave.CREATE_DOCUMENT,assistantMode?"assistant_runtime":"android_direct");
            inputs = new DirectInputs(new File(activity.getFilesDir(), "android-inputs"));
            String id = prefs.getString(stateKey("current"), "");
            if (!id.isEmpty()) {
                try { chat = store.load(id); provider = validProvider(chat.optString("provider")); }
                catch (Exception e) { note("上次会话暂时无法读取，原文件仍保留，可在历史中查看其他会话。", true); }
            }
            if (chat == null) createChat();
            restoreInputs();composer.setText(chat.optString("draft")); render();
        } catch (Exception e) { note("无法保存手机会话，请检查手机存储空间。", true); send.setEnabled(false); }
    }
    private static String validProvider(String value) { return ProviderCatalog.validOrDefault(value); }
    private static String name(String provider) { return ProviderCatalog.name(provider); }
    private String config(String provider, String field) {
        return prefs.getString(provider + "." + field, ProviderCatalog.defaultValue(provider,field));
    }
    private String secret(String provider) throws Exception {
        String encrypted = SecretStore.load(activity, "direct_" + provider);
        if (encrypted.isEmpty()) return "";
        JSONObject credential = new JSONObject(encrypted);
        if (!DirectEndpoint.sameOrigin(credential.getString("base"), config(provider, "base"))) throw new IOException("密钥与当前 API 地址不匹配，请重新填写。");
        return credential.getString("key");
    }
    private int dp(float value) { return (int)(value * getResources().getDisplayMetrics().density + .5f); }
    private LinearLayout column() { LinearLayout box = new LinearLayout(activity); box.setOrientation(VERTICAL); return box; }
    private LinearLayout row() { LinearLayout box = new LinearLayout(activity); box.setGravity(Gravity.CENTER_VERTICAL); return box; }
    private LinearLayout.LayoutParams lp(int w, int h) { return new LinearLayout.LayoutParams(w < 0 ? w : dp(w), h < 0 ? h : dp(h)); }
    private GradientDrawable shape(int color, int radius) { GradientDrawable d = new GradientDrawable(); d.setColor(color); d.setCornerRadius(dp(radius)); return d; }
    private TextView text(String value, int size, int color) { TextView t = new TextView(activity); t.setText(value); t.setTextSize(size); t.setTextColor(color); t.setLineSpacing(dp(3), 1); return t; }
    private Button button(String title, boolean primary) {
        Button b = new Button(activity); b.setText(title); b.setAllCaps(false); b.setTextSize(13); b.setTextColor(primary ? p.onAccent : p.text);
        PocketUi.button(b,p,primary);return b;
    }
    private IconView icon(String glyph, String description, Runnable action) { IconView v = new IconView(activity, glyph, p.text, description); v.setOnClickListener(view -> action.run()); return v; }
    private void gap(LinearLayout box, int height) { box.addView(new View(activity), lp(1, height)); }
    private void toast(String value) { Toast.makeText(activity, value, Toast.LENGTH_SHORT).show(); }
    private int readingSurface(boolean user){return ((user?p.userBubble:p.panel)&0x00ffffff)|0xe6000000;}
    void onWallpaperChanged(java.util.function.Consumer<String> listener){wallpaperListener=listener;wallpaperProvider="";updateWallpaper();}
    private void updateWallpaper(){if(wallpaperListener!=null&&!provider.equals(wallpaperProvider)){wallpaperListener.accept(provider);wallpaperProvider=provider;}}
    private void note(String value, boolean error) { status.setText(value); status.setTextColor(error ? p.error : p.muted); status.setVisibility(value.isEmpty() ? GONE : VISIBLE); }
    private void build() {
        scroll = new ScrollView(activity); scroll.setFillViewport(true); scroll.setVerticalScrollBarEnabled(false);
        messages = column(); messages.setPadding(dp(2), dp(10), dp(2), dp(10)); scroll.addView(messages);
        addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        LinearLayout input = column(); input.setPadding(dp(10), dp(8), dp(10), dp(8)); input.setBackground(PocketUi.surface(activity,readingSurface(false),28,p.border));input.setElevation(dp(3));
        status = text("", 12, p.muted); status.setPadding(dp(8), dp(5), dp(8), dp(5)); status.setVisibility(GONE); input.addView(status, lp(-1, -2));
        inputScroll=new HorizontalScrollView(activity);inputScroll.setHorizontalScrollBarEnabled(false);inputStrip=row();inputScroll.addView(inputStrip);inputScroll.setVisibility(GONE);input.addView(inputScroll,lp(-1,-2));
        composer = new EditText(activity); composer.setTextSize(15); composer.setTextColor(p.text); composer.setHintTextColor(p.muted); composer.setBackgroundColor(android.graphics.Color.TRANSPARENT);
        composer.setHint("说说你的想法，或交给我一件事…"); composer.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        composer.setMinLines(1); composer.setMaxLines(4); composer.setPadding(dp(8), dp(6), dp(8), dp(6)); input.addView(composer, lp(-1, -2));
        LinearLayout tools = row();
        tools.addView(icon("plus", "添加图片、文档或生成文件", this::attachmentMenu), lp(40, 44));
        tools.addView(icon("grid", "AI助理与对话菜单", this::menu), lp(40, 44));
        modelLabel = text("选择模型 ⌄", 12, p.text); modelLabel.setGravity(Gravity.CENTER);modelLabel.setSingleLine(true);modelLabel.setEllipsize(android.text.TextUtils.TruncateAt.END);modelLabel.setPadding(dp(6),0,dp(6),0);modelLabel.setBackground(PocketUi.touch(activity,android.graphics.Color.TRANSPARENT,16,0,p.text));
        tools.addView(modelLabel,new LinearLayout.LayoutParams(0,dp(44),1));modelLabel.setOnClickListener(v -> chooseProvider());
        webToggle=text("联网搜索",11,p.muted);webToggle.setGravity(Gravity.CENTER);tools.addView(webToggle,lp(70,40));updateWebToggle();
        webToggle.setOnClickListener(v->{if(!idle())return;if(privateCommunications()){toast("\u901a\u8baf\u8d44\u6599\u4f1a\u8bdd\u5df2\u5173\u95ed\u8054\u7f51\u641c\u7d22\uff0c\u516c\u5f00\u641c\u7d22\u8bf7\u65b0\u5efa\u5bf9\u8bdd\u3002");return;}boolean enabled=!webEnabled();prefs.edit().putBoolean(stateKey("web_enabled"),enabled).apply();updateWebToggle();note(enabled?"网页搜索已开启；先搜索再回答，长按联网按钮可设置密钥。":"网页搜索已关闭，本轮仅使用模型与对话资料。",false);});
        webToggle.setOnLongClickListener(v->{webSettings();return true;});
        send = button("↑", true); send.setContentDescription("发送消息");send.setTextSize(22);send.setPadding(0,0,0,0);send.setOnClickListener(v -> { if (busy) stop(); else send(); }); tools.addView(send, lp(44, 44)); input.addView(tools); addView(input, lp(-1, -2));
    }
    private void createChat() throws Exception {
        draftInputs=new JSONArray();
        chat = store.create(provider, config(provider, "base"), config(provider, "model"), config(provider, "protocol"));
        store.save(chat); prefs.edit().putString(stateKey("current"), chat.getString("id")).putString(stateKey("provider"), provider).apply();
    }
    private boolean idle() { if (busy || exporting || !pendingInputs.isEmpty()) { toast(!pendingInputs.isEmpty()?"请等待附件准备完成。":exporting ? "请等待文件生成完成。" : "请先停止当前回复。"); return false; } return !closed && store != null; }
    private boolean save() {
        try { if (store != null && chat != null) { chat.put("draft", composer.getText().toString()).put("draftAttachments",draftInputs); store.save(chat); } return true; }
        catch (Exception e) { note("手机历史保存失败，请检查存储空间。", true); return false; }
    }
    private void newChat(String selected) {
        if (!idle() || !save()) return;
        provider = validProvider(selected);
        try { createChat(); composer.setText(""); note("", false); render(); }
        catch (Exception e) { note("无法新建手机对话，请检查存储空间。", true); }
    }
    private void chooseProvider() {
        if (!idle()) return;
        LinearLayout body=column();body.setPadding(dp(20),dp(4),dp(20),dp(8));
        EditText search=new EditText(activity);search.setSingleLine(true);search.setTextColor(p.text);search.setHintTextColor(p.muted);search.setTextSize(14);search.setHint("搜索平台，例如 Kimi、千问");body.addView(search,lp(-1,46));
        List<ProviderCatalog.Entry> shown=new ArrayList<>();List<String> labels=new ArrayList<>();
        ArrayAdapter<String> adapter=new ArrayAdapter<String>(activity,android.R.layout.simple_list_item_1,labels){@Override public View getView(int position,View convert,ViewGroup parent){TextView row=(TextView)super.getView(position,convert,parent);row.setTextColor(p.text);row.setTextSize(14);row.setMinHeight(dp(58));row.setPadding(dp(8),dp(8),dp(8),dp(8));return row;}};
        ListView list=new ListView(activity);list.setAdapter(adapter);body.addView(list,lp(-1,340));
        Runnable filter=()->{shown.clear();shown.addAll(ProviderCatalog.search(search.getText().toString()));labels.clear();for(ProviderCatalog.Entry entry:shown){boolean filled=!activity.getSharedPreferences("credentials",Activity.MODE_PRIVATE).getString("direct_"+entry.id,"").isEmpty();labels.add(entry.name+"\n"+(entry.id.equals(provider)?"当前使用":filled?"已填写 API":"未配置"));}adapter.notifyDataSetChanged();};filter.run();
        AlertDialog picker=new RoundedDialogBuilder(activity).setTitle("选择模型平台").setView(body).setPositiveButton("当前 API 设置",(d,i)->settings()).setNegativeButton("关闭",null).create();
        list.setOnItemClickListener((parent,view,position,id)->{String selected=shown.get(position).id;picker.dismiss();if(selected.equals(provider)){settings();return;}newChat(selected);if(!selected.equals(provider))return;try{PhoneApiConfig.load(activity,selected);}catch(Exception e){settings();}});
        search.addTextChangedListener(new TextWatcher(){public void beforeTextChanged(CharSequence s,int start,int count,int after){}public void onTextChanged(CharSequence s,int start,int before,int count){filter.run();}public void afterTextChanged(Editable e){}});picker.show();
    }
    private void menu() {
        LinearLayout body=column();body.setPadding(dp(20),dp(8),dp(20),dp(18));ScrollView scroller=new ScrollView(activity);scroller.addView(body);
        AlertDialog menu=new RoundedDialogBuilder(activity).setTitle("你的工作空间").setView(scroller).setNegativeButton("回到对话",null).create();
        PocketUi.entry(body,p,"assistant","AI助理","记录事务，整理今天",()->{menu.dismiss();assistant.run();});
        PocketUi.entry(body,p,"new","新对话","从一个新想法开始",()->{menu.dismiss();newChat(provider);});
        PocketUi.entry(body,p,"history","最近对话","接着上次的话题",()->{menu.dismiss();history();});
        PocketUi.entry(body,p,"file","我的文件","生成、查找与保存",()->{menu.dismiss();openFiles();});
        PocketUi.section(body,p,"偏好与连接");
        PocketUi.entry(body,p,"settings","模型与 API",name(provider),()->{menu.dismiss();settings();});
        PocketUi.entry(body,p,"search","联网搜索","搜索服务与密钥",()->{menu.dismiss();webSettings();});
        PocketUi.entry(body,p,"theme","个性化","显示模式与颜色主题",()->{menu.dismiss();if(idle()){save();appearance.run();}});
        TextView remove=text("删除当前对话",13,p.error);remove.setPadding(dp(16),dp(16),dp(16),dp(16));body.addView(remove);remove.setOnClickListener(v->{menu.dismiss();delete();});menu.show();
    }
    private boolean privateCommunications(){return assistantMode&&chat!=null&&chat.optBoolean("privateCommunications");}
    private boolean webEnabled(){return !privateCommunications()&&prefs.getBoolean(stateKey("web_enabled"),false);}
    private void updateWebToggle(){if(webToggle!=null){webToggle.setText("联网搜索");webToggle.setTextColor(webEnabled()?p.accent:p.muted);webToggle.setBackground(shape(webEnabled()?p.selected:p.chip,14));webToggle.setSelected(webEnabled());webToggle.setContentDescription(webEnabled()?"联网搜索已开启，点击关闭，长按设置":"联网搜索已关闭，点击开启，长按设置");}}
    void openApiSettings(){chooseProvider();}
    void openHistory(){history();}
    boolean sendAssistantInput(String prompt){if(!assistantMode||!idle())return false;try{if(!composer.getText().toString().trim().isEmpty()||draftInputs.length()>0){if(!save())return false;createChat();}composer.setText(prompt);if(!save())return false;send();composer.clearFocus();android.view.inputmethod.InputMethodManager keyboard=(android.view.inputmethod.InputMethodManager)activity.getSystemService(Activity.INPUT_METHOD_SERVICE);if(keyboard!=null)keyboard.hideSoftInputFromWindow(composer.getWindowToken(),0);return true;}catch(Exception e){note("无法准备对话，输入内容已保留。",true);return false;}}
    boolean startWork(JSONObject work){
        if(assistantMode||!idle())return false;
        try{String existing=work.optString("chat");if(!existing.isEmpty()){return openWorkChat(existing);}if(!save())return false;createChat();chat.put("assistantWork",work.getString("id"));composer.setText(work.getString("prompt"));if(!save())return false;AssistantWork.update(activity,work.getString("id"),"prepared",chat.getString("id"),"指令已准备；配置模型或联网搜索后可发送");render();send();return true;}catch(Exception e){note("无法准备工作，原指令已保留。",true);return false;}
    }
    boolean openWorkChat(String id){
        if(chat!=null&&id.equals(chat.optString("id")))return true;if(!idle()||!save())return false;
        try{chat=store.load(id);provider=validProvider(chat.optString("provider"));prefs.edit().putString(stateKey("current"),chat.getString("id")).putString(stateKey("provider"),provider).apply();composer.setText(chat.optString("draft"));restoreInputs();note("",false);render();return true;}catch(Exception e){note("工作会话暂时无法打开，原记录已保留。",true);return false;}
    }
    void prepareAssistantDraft(String prompt){
        if(!idle())return;String draft=composer.getText().toString();composer.setText(draft.trim().isEmpty()?prompt:draft+"\n\n"+prompt);composer.setSelection(composer.length());composer.requestFocus();save();
        composer.postDelayed(()->{if(!closed&&composer.hasFocus()&&composer.length()>0){android.view.inputmethod.InputMethodManager keyboard=(android.view.inputmethod.InputMethodManager)activity.getSystemService(Activity.INPUT_METHOD_SERVICE);if(keyboard!=null)keyboard.showSoftInput(composer,android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT);}},240);
    }
    void openFiles(){
        if(store==null||closed)return;
        LinearLayout body=column();body.setPadding(dp(20),dp(8),dp(20),dp(18));
        Button create=button("＋ 生成新文件",true);body.addView(create,lp(-1,48));gap(body,18);TextView progress=text("正在整理文件…",13,p.muted);body.addView(progress);
        ScrollView scroller=new ScrollView(activity);scroller.setVerticalScrollBarEnabled(false);scroller.addView(body);
        AlertDialog library=new RoundedDialogBuilder(activity).setTitle("我的文件").setView(scroller).setNegativeButton("返回",null).create();create.setOnClickListener(v->{library.dismiss();fileMenu();});library.show();
        files.execute(()->{List<JSONObject> found=new ArrayList<>();java.util.Set<String> seen=new java.util.HashSet<>();boolean complete=true;
            try{for(JSONObject entry:store.list()){
                JSONObject saved=store.load(entry.getString("id"));JSONArray turns=saved.optJSONArray("messages");
                for(int i=turns.length()-1;i>=0;i--){JSONArray generated=turns.optJSONObject(i)==null?null:turns.getJSONObject(i).optJSONArray("files");for(int j=0;generated!=null&&j<generated.length();j++){JSONObject file=generated.getJSONObject(j);if(!file.optString("id").isEmpty()&&seen.add(file.getString("id")))found.add(new JSONObject(file.toString()).put("chatTitle",entry.optString("title")));}}
                if(found.size()>=100)break;
            }}catch(Exception e){complete=false;}
            final boolean loaded=complete;ui.post(()->{if(closed||!library.isShowing())return;progress.setText(found.isEmpty()?(loaded?"生成过的文档会出现在这里，随时可以再次保存。":"文件列表暂时无法读取，请稍后重试。"):"最近生成的 "+found.size()+" 份文件"+(loaded?"":" · 部分会话读取失败"));gap(body,14);
                for(JSONObject file:found){PocketUi.entry(body,p,"file",file.optString("name","文档"),file.optString("chatTitle")+" · "+String.format(java.util.Locale.ROOT,"%.1f KB",file.optLong("size")/1024.0),()->fileSave.save(file));}
            });
        });
    }
    void openApiSettings(String required){if(!idle())return;settings(required.isEmpty()?provider:validProvider(required));}
    private String webKey()throws Exception{
        String dedicated=SecretStore.load(activity,"direct_web");
        if(!dedicated.isEmpty()){JSONObject saved=new JSONObject(dedicated);if(!DirectWeb.official(saved.getString("base")))throw new IOException("网页搜索密钥地址无效，请重新设置。");return saved.getString("key");}
        if(DirectWeb.official(config("deepseek","base")))return secret("deepseek");
        return "";
    }
    private void webSettings(){
        if(!idle())return;
        if(privateCommunications()){toast("通讯资料会话已关闭联网搜索。需要搜索公开资料时，请新建助理对话。");return;}
        LinearLayout body=column();body.setPadding(dp(22),dp(12),dp(22),dp(12));
        Switch enabled=new Switch(activity);enabled.setText("启用网页搜索");enabled.setTextColor(p.text);enabled.setChecked(webEnabled());body.addView(enabled,lp(-1,48));gap(body,10);
        body.addView(text("使用 DeepSeek 官方搜索与网页读取，默认复用已保存的 DeepSeek Key；也可填写专用 Key。ChatGPT 对话也可使用这些搜索结果。搜索词或网址会提交到 DeepSeek，按其平台规则计费。",12,p.muted));gap(body,14);
        EditText key=field(body,"搜索用 DeepSeek API Key（可留空）","",true);boolean hasKey=false;try{hasKey=!webKey().isEmpty();}catch(Exception ignored){}key.setHint(hasKey?"已有可用配置，留空保留":"填写 Key，或先配置 DeepSeek 对话密钥");
        TextView feedback=text("开启后先检索再回答；无需连接电脑或 Tailscale。",12,p.muted);body.addView(feedback);
        ScrollView container=new ScrollView(activity);container.addView(body);
        AlertDialog dialog=new RoundedDialogBuilder(activity).setTitle("网页搜索").setView(container).setPositiveButton("保存",null).setNegativeButton("取消",null).setNeutralButton("清除专用密钥",null).create();dialog.setOnDismissListener(d->key.setText(""));dialog.show();
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{
            try{String entered=key.getText().toString().trim();if(!entered.isEmpty()){if(entered.matches(".*\\s.*"))throw new IOException("密钥不能包含空白字符。");SecretStore.save(activity,"direct_web",new JSONObject().put("base",DirectWeb.BASE).put("key",entered).toString());}
                if(enabled.isChecked()&&webKey().isEmpty()){feedback.setText(R.string.web_key_required);return;}
                prefs.edit().putBoolean(stateKey("web_enabled"),enabled.isChecked()).apply();updateWebToggle();dialog.dismiss();note(webEnabled()?"网页搜索已开启。":"网页搜索已关闭。",false);
            }catch(Exception e){feedback.setText("搜索设置保存失败，请检查密钥和手机存储。");}
        });
        dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(v->{try{SecretStore.save(activity,"direct_web","");key.setText("");feedback.setText(R.string.web_key_cleared);}catch(Exception e){feedback.setText("无法清除密钥，请重试。");}});
    }
    private void render() {
        updateWebToggle();
        updateWallpaper();
        for (MathReplyView view : formulaViews) view.destroy(); formulaViews.clear(); messages.removeAllViews();
        if (chat == null) return;
        renderInputs();
        String model = chat.optString("model");
        modelLabel.setText(activity.getString(R.string.phone_model_selector_label,ProviderCatalog.find(provider).shortName));
        modelLabel.setContentDescription(name(provider)+(model.isEmpty()?"，设置模型":"，"+model)+"，点击切换平台或设置 API");
        modelLabel.setTooltipText(name(provider)+(model.isEmpty()?"":" · "+model));
        JSONArray turns = chat.optJSONArray("messages");
        if (turns != null) {
            JSONObject question = null;
            for (int i = 0; i < turns.length(); i++) {
                JSONObject message = turns.optJSONObject(i);
                if (message != null) { if ("user".equals(message.optString("role"))) question = message; bubble(message, question); }
            }
        }
        scroll.post(() -> scroll.fullScroll(ScrollView.FOCUS_DOWN));
    }
    private void bubble(JSONObject message, JSONObject question) {
        boolean user = "user".equals(message.optString("role")); String state = message.optString("state"), value = message.optString("text");
        if (!user) value = replyText(value, state.equals("streaming"));
        LinearLayout box = column(); box.setPadding(dp(18), dp(15), dp(18), dp(15)); box.setBackground(shape(readingSurface(user),24));
        TextView label = text(user ? "你" : name(provider), 11, p.muted); box.addView(label); gap(box, 6);
        TextView body = text(value.isEmpty() && state.equals("streaming") ? "正在思考…" : value, 15, p.text); body.setTextIsSelectable(true); box.addView(body, lp(-1, -2));
        JSONArray attached=message.optJSONArray("attachments");
        if(user&&attached!=null)for(int i=0;i<attached.length();i++){JSONObject item=attached.optJSONObject(i);if(item!=null){TextView file=text((item.optString("kind").equals("image")?"图片 · ":"文档 · ")+item.optString("name"),12,p.muted);file.setPadding(0,dp(8),0,0);box.addView(file);file.setOnClickListener(v->previewInput(item));}}
        if (!user && state.equals("complete") && MathReplyView.needsMath(value)) {
            MathReplyView math = new MathReplyView(activity, body, p.dark, null); formulaViews.add(math); box.addView(math, lp(-1, 1)); math.setSource(value);
        }
        if (!user && state.equals("complete")) {
            if(assistantMode)for(JSONObject action:AssistantActions.parse(message.optString("text"))){Button execute=button(SystemAssistantActions.title(action),false);gap(box,8);box.addView(execute,lp(-1,-2));execute.setMinHeight(dp(44));execute.setOnClickListener(v->{if(action.optString("type").equals("communications"))communicationSource.accept(action.optString("source"));else SystemAssistantActions.open(activity,p,action);});}
            JSONArray generated = message.optJSONArray("files");
            if (generated != null) for (int i = 0; i < generated.length(); i++) { JSONObject file = generated.optJSONObject(i); if (file != null) fileCard(box, file); }
            if (!message.optString("fileError").isEmpty()) { gap(box, 7); box.addView(text(message.optString("fileError"), 12, p.error)); }
            TextView export = text("保存为文件", 12, p.accent); export.setPadding(0, dp(10), 0, dp(4)); box.addView(export); export.setOnClickListener(v -> exportReply(message));
            TextView keep=text("收进AI助理  ↗",12,p.accent);keep.setPadding(0,dp(10),0,dp(8));box.addView(keep);keep.setOnClickListener(v->AssistantHub.capture(activity,p,replyText(message.optString("text"),false)));
        }
        if(!user&&message.optJSONObject("web")!=null)webCard(box,message.optJSONObject("web"));
        if (!user && (state.equals("failed") || state.equals("interrupted"))) {
            gap(box, 7); box.addView(text(state.equals("failed") ? "回复未完成" : "已停止", 11, p.error));
            if(!message.optString("error").isEmpty())box.addView(text(message.optString("error"),12,p.error));
            TextView retry = text("将问题放回输入框", 12, p.accent); retry.setPadding(0, dp(10), 0, dp(4)); box.addView(retry);
            retry.setOnClickListener(v -> {
                if (!idle() || question == null) return;
                composer.setText(question.optString("text")); composer.setSelection(composer.length());
                JSONArray attachedQuestion=question.optJSONArray("attachments");
                if(attachedQuestion!=null&&attachedQuestion.length()>0){
                    if(draftInputs.length()>0)note("已恢复问题文字；当前附件草稿已保留，原附件可在上方消息中查看。",false);
                    else try{JSONArray restored=new JSONArray(attachedQuestion.toString());for(int i=0;i<restored.length();i++)inputs.validate(restored.getJSONObject(i));draftInputs=restored;note("已恢复问题和附件，可以重新发送。",false);}
                    catch(Exception e){note("已恢复问题；原附件已失效，请重新添加。",true);}
                }
                save();renderInputs();
            });
        }
        LinearLayout.LayoutParams params = lp(-1, -2); params.bottomMargin = dp(12); messages.addView(box, params);
        if (state.equals("streaming")) streamingText = body;
    }
    private void webCard(LinearLayout box,JSONObject evidence){
        JSONArray sources=evidence.optJSONArray("sources");int count=sources==null?0:sources.length();
        gap(box,7);TextView toggle=text(count==0?"本次未检索到可用网页":"网页来源 · "+count+" 项  ⌄",12,count==0?p.error:p.accent);toggle.setPadding(0,dp(8),0,dp(5));box.addView(toggle);
        LinearLayout list=column();list.setVisibility(GONE);box.addView(list,lp(-1,-2));
        list.addView(text("检索于 "+DirectWeb.date(evidence.optLong("at"))+"\n"+evidence.optString("query"),10,p.muted));
        for(int i=0;sources!=null&&i<Math.min(5,sources.length());i++){
            JSONObject source=sources.optJSONObject(i);if(source==null)continue;String url=source.optString("url");if(!DirectWeb.safeUrl(url))continue;
            TextView link=text("["+(i+1)+"] "+source.optString("title",url),13,p.accent);link.setPadding(0,dp(12),0,dp(4));list.addView(link);
            String published=source.optString("date");String domain=url;try{domain=new java.net.URI(url).getHost();}catch(Exception ignored){}
            list.addView(text(domain+(published.isEmpty()?"":" · "+published),10,p.muted));
            link.setContentDescription("打开网页来源 "+source.optString("title"));link.setOnClickListener(v->{try{activity.startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse(url)));}catch(Exception e){toast("没有可用浏览器。");}});
        }
        toggle.setOnClickListener(v->list.setVisibility(list.getVisibility()==VISIBLE?GONE:VISIBLE));
    }
    private void send() {
        if (closed || store == null || chat == null) return;
        if(exporting){toast("请等待文件生成完成。");return;}
        if(!pendingInputs.isEmpty()){toast("请等待附件准备完成。");return;}
        String entered=composer.getText().toString().trim();if(entered.isEmpty()&&draftInputs.length()==0)return;
        String prompt=entered.isEmpty()?"请结合这些附件进行分析。":entered;
        if (prompt.length() > 30000) { toast("问题过长，请缩短后发送。"); return; }
        if (chat.optJSONArray("messages").length() >= 160) { toast("当前会话较长，请新建手机对话。"); return; }
        final DirectApi client,searchClient; final JSONArray context; final String model = chat.optString("model"), protocol = chat.optString("protocol");
        try {
            String base = DirectEndpoint.normalize(chat.getString("base"));
            if (!base.equals(DirectEndpoint.normalize(config(provider, "base")))) { note("平台地址已变更，请新建对话后再发送。", true); return; }
            String key = secret(provider);
            if (key.isEmpty() || model.isEmpty()) { settings(); return; }
            if(webEnabled()){String searchKey=webKey();if(searchKey.isEmpty()){note("联网需要 DeepSeek Key，请在网页搜索设置中填写；也可关闭联网后发送。",true);webSettings();return;}searchClient=new DirectApi(DirectWeb.BASE,searchKey);}else searchClient=null;
            for(int i=0;i<draftInputs.length();i++)inputs.validate(draftInputs.getJSONObject(i));
            JSONArray attached=new JSONArray(draftInputs.toString());
            JSONArray prepared=LocalFileSpec.withGuide(DirectChats.context(chat, prompt,attached));context=assistantMode?AssistantActions.withGuide(prepared,SmartRepository.context(activity)):prepared; client = new DirectApi(base, key);
            JSONArray turns = chat.getJSONArray("messages");
            turns.put(new JSONObject().put("role", "user").put("text", prompt).put("state", "complete").put("attachments",attached));
            streamingMessage = new JSONObject().put("role", "assistant").put("text", "").put("state", "streaming"); turns.put(streamingMessage);
            if ("新对话".equals(chat.optString("title"))) chat.put("title", prompt.substring(0, Math.min(24, prompt.length())).replace('\n', ' '));
            chat.put("updated", System.currentTimeMillis()); composer.setText("");draftInputs=new JSONArray();
            if (!save()) { streamingMessage.put("state", "failed"); composer.setText(prompt);draftInputs=attached; render(); return; }
        } catch (Exception e) { note("无法准备请求，请检查 API 设置及手机存储。", true); return; }
        busy = true; request = client;webRequest=searchClient;final JSONObject replyMessage=streamingMessage; final Run run = new Run(); activeRun = run; updatePosted = false; final int expected = ++revision; send.setText("■");send.setContentDescription("停止回复"); note(searchClient!=null?"正在检索网页…":"", false); render(); lastSaved = System.currentTimeMillis();
        if(!assistantMode&&!chat.optString("assistantWork").isEmpty())AssistantWork.update(activity,chat.optString("assistantWork"),"running",chat.optString("id"),"正在处理");
        network.execute(() -> {
            try {
                JSONArray wire=inputs.messages(context,protocol);
                if(searchClient!=null){
                    if(run.cancelled)throw new IOException("请求已停止");
                    JSONObject evidence;
                    try{evidence=DirectWeb.retrieve(searchClient,prompt,assistantMode?new JSONArray():context);}catch(Exception e){if(run.cancelled)throw e;throw new IOException("网页检索失败："+friendly(e)+" 未生成未经检索的实时回答，可稍后重试或关闭联网。");}
                    if(run.cancelled)throw new IOException("请求已停止");
                    run.web=DirectWeb.metadata(evidence);wire=DirectWeb.augment(wire,evidence);
                    ui.post(()->{if(!closed&&revision==expected){try{replyMessage.put("web",run.web);}catch(Exception ignored){}save();note(DirectWeb.warning(run.web).isEmpty()?"网页资料已获取，正在整理回答…":DirectWeb.warning(run.web),false);}});
                }
                if(run.cancelled)throw new IOException("请求已停止");
                String answer = client.chat(model, protocol, wire, value -> {
                    run.text = value;
                    ui.post(() -> { if (closed || revision != expected || !busy || updatePosted) return; updatePosted = true; ui.postDelayed(() -> { if (!closed && revision == expected && busy) { updatePosted = false; updateStreaming(); } }, 70); });
                });
                JSONArray generated = new JSONArray(); String fileError = "";
                if (!run.cancelled) {
                    try {
                        List<LocalFileSpec> specs = LocalFileSpec.parse(answer);
                        if (!specs.isEmpty()) {
                            ui.post(() -> { if (!closed && revision == expected) note("正在生成手机文件…", false); });
                            generated = artifacts.generate(specs, LocalPdf::write);
                        }
                    } catch (Exception e) { fileError = "文件未生成：" + fileError(e); }
                }
                final JSONArray completedFiles = generated; final String completedError = fileError;
                ui.post(() -> { if (!closed && revision == expected) finish(answer, "complete", completedError, completedFiles); else artifacts.discard(completedFiles); });
            } catch (Exception e) {
                String error = friendly(e);
                if(e instanceof DirectApi.Failure){int code=((DirectApi.Failure)e).status;if((code==400||code==415||code==422)&&hasImageInputs(context))error+=" 当前请求含图片或 PDF 页面，请确认模型支持图片，以及接口格式和输入限制。附件未被自动移除。";}
                final String failure=error;ui.post(() -> { if (!closed && revision == expected) finish(run.text, "failed", failure); });
            }
        });
    }
    private static boolean hasImageInputs(JSONArray context){
        for(int i=0;i<context.length();i++){JSONObject m=context.optJSONObject(i);JSONArray refs=m==null?null:m.optJSONArray("attachmentRefs");for(int j=0;refs!=null&&j<refs.length();j++){JSONObject ref=refs.optJSONObject(j);JSONArray parts=ref==null?null:ref.optJSONArray("parts");for(int k=0;parts!=null&&k<parts.length();k++){JSONObject part=parts.optJSONObject(k);if(part!=null&&"image".equals(part.optString("type")))return true;}}}return false;
    }
    private void updateStreaming() {
        if (streamingText == null || activeRun == null) return;
        boolean nearEnd = scroll.getChildAt(0).getHeight() - scroll.getScrollY() - scroll.getHeight() < dp(100);
        streamingText.setText(replyText(activeRun.text, true));
        try { streamingMessage.put("text", activeRun.text); } catch (Exception ignored) {}
        if (System.currentTimeMillis() - lastSaved > 1200) { save(); lastSaved = System.currentTimeMillis(); }
        if (nearEnd) scroll.post(() -> scroll.fullScroll(ScrollView.FOCUS_DOWN));
    }
    private void finish(String value, String state, String error) {
        finish(value, state, error, new JSONArray());
    }
    private void finish(String value, String state, String error, JSONArray generated) {
        Run run=activeRun;busy = false; request = null;webRequest=null; activeRun = null; send.setText("↑");send.setContentDescription("发送消息");
        try { streamingMessage.put("text", value).put("state", state).put("files", generated).put("error",state.equals("complete")?"":error);if(run!=null&&run.web!=null)streamingMessage.put("web",run.web); if (state.equals("complete") && !error.isEmpty()) streamingMessage.put("fileError", error); chat.put("updated", System.currentTimeMillis()); } catch (Exception ignored) {}
        note(error, !error.isEmpty()); boolean persisted=save(); render();
        if(!assistantMode&&!chat.optString("assistantWork").isEmpty())AssistantWork.update(activity,chat.optString("assistantWork"),!persisted?"failed":!error.isEmpty()&&state.equals("complete")?"failed":state,chat.optString("id"),!persisted?"结果保存失败，请检查存储空间":!error.isEmpty()?error:replyText(value,false));
        else if(persisted&&"complete".equals(state))AssistantNotifications.reply(activity,chat.optString("id"),assistantMode,chat.optString("title"));
    }
    private static String friendly(Exception e) {
        if (e instanceof DirectApi.Failure) return e.getMessage();
        if (e instanceof java.net.UnknownHostException || e instanceof java.net.ConnectException) return "手机无法访问 API 地址，请检查手机网络和平台地址。";
        if (e instanceof java.net.SocketTimeoutException) return "平台响应超时，已保留收到的内容。";
        if (e instanceof javax.net.ssl.SSLException) return "无法建立 HTTPS 连接，请检查手机时间、网络及平台证书。";
        if (e instanceof IOException && e.getMessage() != null && !e.getMessage().contains("\n") && e.getMessage().length() < 130) return e.getMessage();
        return "平台响应无法读取，请检查接口格式、模型权限和网络。";
    }
    private void stop() {
        if (!busy) return;
        String received = activeRun == null ? "" : activeRun.text;
        if(activeRun!=null)activeRun.cancelled=true;
        revision++; if (request != null) request.cancel();if(webRequest!=null)webRequest.cancel(); finish(received, "interrupted", "已停止生成，未完成的回复不会加入后续上下文。");
    }
    boolean isBusy() { return busy || exporting || !pendingInputs.isEmpty(); }
    void pause() { stop(); save(); if (settingsDialog != null) settingsDialog.dismiss(); }
    void close() { pause(); closed = true; if (modelRequest != null) modelRequest.cancel(); network.shutdownNow(); files.shutdown(); if(fileSave!=null)fileSave.close(); for (MathReplyView view : formulaViews) view.destroy(); formulaViews.clear(); }
    void saveState(Bundle state) { save(); }
    boolean onActivityResult(int requestCode, int resultCode, Intent data) {
        if(fileSave!=null&&fileSave.result(requestCode,resultCode,data))return true;
        if(requestCode!=pickerCode(false)&&requestCode!=pickerCode(true))return false;
        String original=prefs.getString(stateKey("input_picker_chat"),"");prefs.edit().remove(stateKey("input_picker_chat")).apply();
        if(resultCode!=Activity.RESULT_OK||data==null)return true;
        if(chat==null||!chat.optString("id").equals(original)){toast("请回到原会话重新添加附件。");return true;}
        inputErrors.clear();
        List<Uri> selected=new ArrayList<>();if(data.getClipData()!=null){for(int i=0;i<data.getClipData().getItemCount();i++)selected.add(data.getClipData().getItemAt(i).getUri());}else if(data.getData()!=null)selected.add(data.getData());
        if(selected.size()>DirectInputs.MAX_ATTACHMENTS-draftInputs.length())inputErrors.add("每条消息最多5个附件，超出部分未添加。");
        for(Uri uri:selected){if(draftInputs.length()+pendingInputs.size()>=DirectInputs.MAX_ATTACHMENTS)break;importInput(uri);}
        inputStatus();
        return true;
    }
    private void restoreInputs(){
        draftInputs=chat.optJSONArray("draftAttachments");if(draftInputs==null)draftInputs=new JSONArray();
        JSONArray ready=new JSONArray();boolean missing=false;
        for(int i=0;i<draftInputs.length();i++)try{JSONObject item=draftInputs.getJSONObject(i);inputs.validate(item);ready.put(item);}catch(Exception e){missing=true;}
        draftInputs=ready;if(missing)note("部分草稿附件已失效，请重新添加。",true);
    }
    private void attachmentMenu(){
        if(!idle())return;
        LinearLayout body=column();body.setPadding(dp(22),dp(12),dp(22),dp(16));
        Button images=button("选择图片",false),documents=button("选择文件",false),generate=button("生成或导出文件",false);
        body.addView(images,lp(-1,48));gap(body,10);body.addView(documents,lp(-1,48));gap(body,10);body.addView(generate,lp(-1,48));
        ScrollView container=new ScrollView(activity);container.addView(body);
        AlertDialog dialog=new RoundedDialogBuilder(activity).setTitle("添加附件").setView(container).setNegativeButton("取消",null).create();
        images.setOnClickListener(v->{dialog.dismiss();pickInputs(true);});documents.setOnClickListener(v->{dialog.dismiss();pickInputs(false);});generate.setOnClickListener(v->{dialog.dismiss();fileMenu();});dialog.show();
    }
    private void pickInputs(boolean image){
        if(!idle())return;
        if(draftInputs.length()>=DirectInputs.MAX_ATTACHMENTS){toast("每条消息最多5个附件。");return;}
        Intent intent=new Intent(Intent.ACTION_OPEN_DOCUMENT);intent.addCategory(Intent.CATEGORY_OPENABLE);intent.setType(image?"image/*":"*/*");intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE,true);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        try{prefs.edit().putString(stateKey("input_picker_chat"),chat.getString("id")).commit();activity.startActivityForResult(intent,pickerCode(image));}
        catch(Exception e){prefs.edit().remove(stateKey("input_picker_chat")).apply();toast("无法打开系统文件选择器。");}
    }
    private void importInput(Uri uri){
        String name="附件";long declared=-1;
        try(Cursor cursor=activity.getContentResolver().query(uri,new String[]{OpenableColumns.DISPLAY_NAME,OpenableColumns.SIZE},null,null,null)){
            if(cursor!=null&&cursor.moveToFirst()){int n=cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME),s=cursor.getColumnIndex(OpenableColumns.SIZE);if(n>=0&&!cursor.isNull(n))name=cursor.getString(n);if(s>=0&&!cursor.isNull(s))declared=cursor.getLong(s);}
        }catch(Exception ignored){}
        if(declared>DirectInputs.MAX_FILE){inputErrors.add(DirectInputs.cleanName(name)+"：单个附件不能超过25MB。");return;}
        String mime=null;try{mime=activity.getContentResolver().getType(uri);}catch(Exception ignored){}
        if(DirectInputs.extension(name).isEmpty()&&mime!=null){if(mime.equals("image/jpeg"))name+=".jpg";else if(mime.equals("image/png"))name+=".png";else if(mime.equals("application/pdf"))name+=".pdf";else if(mime.startsWith("text/"))name+=".txt";}
        final String filename=DirectInputs.cleanName(name);pendingInputs.add(filename);renderInputs();
        files.execute(()->{
            JSONObject imported=null;
            try{
                imported=inputs.copy(activity.getContentResolver().openInputStream(uri),filename);AndroidInputMedia.prepare(inputs,imported);final JSONObject ready=imported;
                ui.post(()->{
                    pendingInputs.remove(filename);
                    if(closed){inputs.discard(ready);return;}
                    long total=ready.optLong("size");for(int i=0;i<draftInputs.length();i++)total+=draftInputs.optJSONObject(i).optLong("size");
                    if(total>50L*1024*1024){inputs.discard(ready);inputErrors.add(filename+"：本条消息的附件总大小不能超过50MB。");}else{draftInputs.put(ready);save();}
                    inputStatus();
                    renderInputs();
                });
            }catch(Exception|OutOfMemoryError e){
                if(imported!=null)inputs.discard(imported);String error=e instanceof IOException&&e.getMessage()!=null&&e.getMessage().length()<150?e.getMessage():"无法读取附件，请确认格式受支持；加密 PDF 请先解密。";
                ui.post(()->{pendingInputs.remove(filename);if(!closed){inputErrors.add(filename+"："+error);inputStatus();renderInputs();}});
            }
        });
    }
    private void inputStatus(){
        if(!inputErrors.isEmpty())note(android.text.TextUtils.join("\n",inputErrors),true);
        else note(pendingInputs.isEmpty()?"附件已准备，可继续添加或和问题一起发送。":"正在准备附件…",false);
    }
    private void renderInputs(){
        if(inputStrip==null)return;inputStrip.removeAllViews();inputScroll.setVisibility(draftInputs.length()==0&&pendingInputs.isEmpty()?GONE:VISIBLE);
        if(send!=null)send.setEnabled(pendingInputs.isEmpty());
        for(int i=0;i<draftInputs.length();i++){
            final int index=i;JSONObject item=draftInputs.optJSONObject(i);if(item==null)continue;
            LinearLayout chip=row();chip.setPadding(dp(6),dp(6),dp(2),dp(6));chip.setBackground(shape(p.chip,12));
            try{File preview=inputs.preview(item);if(preview!=null){ImageView image=new ImageView(activity);RoundedDialogBuilder.clip(image,10);BitmapFactory.Options o=new BitmapFactory.Options();o.inSampleSize=16;image.setImageBitmap(BitmapFactory.decodeFile(preview.getPath(),o));image.setScaleType(ImageView.ScaleType.CENTER_CROP);chip.addView(image,lp(38,38));}}catch(Exception ignored){}
            LinearLayout labels=column();labels.setPadding(dp(7),0,dp(2),0);TextView title=text(item.optString("name"),12,p.text);title.setMaxLines(1);title.setEllipsize(android.text.TextUtils.TruncateAt.MIDDLE);labels.addView(title,lp(118,-2));
            TextView summary=text(item.optString("summary"),10,p.muted);summary.setMaxLines(1);summary.setEllipsize(android.text.TextUtils.TruncateAt.END);labels.addView(summary,lp(118,-2));chip.addView(labels);
            chip.addView(icon("close","移除 "+item.optString("name"),()->{
                if(busy)return;draftInputs.remove(index);if(!inputInHistory(item.optString("id")))inputs.discard(item);save();renderInputs();
            }),lp(32,38));chip.setOnClickListener(v->previewInput(item));LinearLayout.LayoutParams params=lp(-2,-2);params.rightMargin=dp(7);inputStrip.addView(chip,params);
        }
        for(String name:pendingInputs){TextView label=text(name+" · 准备中…",12,p.muted);label.setPadding(dp(8),dp(10),dp(8),dp(10));inputStrip.addView(label);}
    }
    private boolean inputInHistory(String id){
        JSONArray turns=chat.optJSONArray("messages");for(int i=0;turns!=null&&i<turns.length();i++){JSONObject turn=turns.optJSONObject(i);JSONArray attached=turn==null?null:turn.optJSONArray("attachments");for(int a=0;attached!=null&&a<attached.length();a++){JSONObject item=attached.optJSONObject(a);if(item!=null&&id.equals(item.optString("id")))return true;}}return false;
    }
    private void previewInput(JSONObject item){
        try{
            inputs.validate(item);LinearLayout body=column();body.setPadding(dp(18),dp(10),dp(18),dp(12));body.addView(text(item.optString("summary"),12,p.muted));gap(body,10);
            File preview=inputs.preview(item);
            if(preview!=null){ImageView image=new ImageView(activity);RoundedDialogBuilder.clip(image,18);BitmapFactory.Options options=new BitmapFactory.Options();options.inSampleSize=2;image.setImageBitmap(BitmapFactory.decodeFile(preview.getPath(),options));image.setScaleType(ImageView.ScaleType.FIT_CENTER);body.addView(image,lp(-1,340));}
            else{TextView content=text(inputs.textPreview(item),13,p.text);content.setTextIsSelectable(true);ScrollView view=new ScrollView(activity);view.addView(content);body.addView(view,lp(-1,320));body.addView(text("正文预览（最多5000字）",10,p.muted));}
            new RoundedDialogBuilder(activity).setTitle(item.optString("name")).setView(body).setPositiveButton("关闭",null).show();
        }catch(Exception|OutOfMemoryError e){toast("附件无法预览，可能已失效，请重新添加。");}
    }
    private static String fileError(Exception error) {
        String value = error.getMessage();
        return error instanceof IOException && value != null && value.length() < 100 ? value : "文件数据不完整或格式有误，请让模型重新生成。";
    }
    private void fileCard(LinearLayout parent, JSONObject file) {
        LinearLayout card = row(); card.setPadding(dp(10), dp(10), dp(10), dp(10)); card.setBackground(shape(p.chip, 12));
        LinearLayout info = column(); TextView title = text(file.optString("name"), 13, p.text); title.setMaxLines(2); info.addView(title);
        info.addView(text(String.format(java.util.Locale.ROOT, "%.1f KB", file.optLong("size") / 1024.0), 11, p.muted)); card.addView(info, new LinearLayout.LayoutParams(0, -2, 1));
        Button download = button("保存", false); card.addView(download, lp(64, 42)); download.setOnClickListener(v -> { if (fileSave != null) fileSave.save(file); });
        LinearLayout.LayoutParams params = lp(-1, -2); params.topMargin = dp(10); parent.addView(card, params);
    }
    private void fileMenu() {
        if (!idle()) return;
        new RoundedDialogBuilder(activity).setTitle("文件").setItems(new String[]{"生成 Word 文档", "生成 Excel 表格", "生成 PDF 文档", "生成文本、代码或 ZIP", "导出上一条完整回复"}, (d, which) -> {
            if (which == 4) {
                JSONArray turns = chat.optJSONArray("messages");
                for (int i = turns.length() - 1; i >= 0; i--) { JSONObject message = turns.optJSONObject(i); if (message != null && "assistant".equals(message.optString("role")) && "complete".equals(message.optString("state"))) { exportReply(message); return; } }
                toast("请先完成一条回复。"); return;
            }
            int[] requests = {R.string.file_request_word,R.string.file_request_excel,R.string.file_request_pdf,R.string.file_request_other};
            String draft = composer.getText().toString(); composer.setText(activity.getString(requests[which],draft)); composer.requestFocus(); composer.setSelection(composer.length());
            android.view.inputmethod.InputMethodManager keyboard = (android.view.inputmethod.InputMethodManager)activity.getSystemService(Activity.INPUT_METHOD_SERVICE); if (keyboard != null) keyboard.showSoftInput(composer, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT);
        }).setNegativeButton("取消", null).show();
    }
    private void exportReply(JSONObject message) {
        if (artifacts == null || !idle()) return;
        new RoundedDialogBuilder(activity).setTitle("将回复保存为").setItems(new String[]{"Word（.docx）", "PDF（.pdf）", "文本（.txt）", "Markdown（.md）"}, (d, which) -> {
            String extension = new String[]{"docx", "pdf", "txt", "md"}[which];
            String content = LocalFileSpec.display(message.optString("text"), false);
            if (content.isEmpty()) { toast("没有可导出的正文，请使用已生成文件的保存按钮。"); return; }
            if(!idle())return;exporting=true;toast("正在准备文件…");
            files.execute(() -> {
                try {
                    LocalFileSpec spec = new LocalFileSpec(new JSONObject().put("name", "对话内容." + extension).put("content", content));
                    JSONArray generated = artifacts.generate(java.util.Collections.singletonList(spec), LocalPdf::write);
                    ui.post(() -> {
                        exporting=false;
                        if (closed) { artifacts.discard(generated); return; }
                        try { JSONArray existing = message.optJSONArray("files"); if (existing == null) { existing = new JSONArray(); message.put("files", existing); } JSONObject descriptor = generated.getJSONObject(0); existing.put(descriptor); save(); render(); fileSave.save(descriptor); }
                        catch (Exception e) { artifacts.discard(generated); toast("无法保存文件卡片，请重试。"); }
                    });
                } catch (Exception e) { String error = fileError(e); ui.post(() -> { exporting=false;if (!closed) toast(error); }); }
            });
        }).setNegativeButton("取消", null).show();
    }
    private void history() {
        if (!idle() || !save()) return;
        try {
            List<JSONObject> items = store.list();
            LinearLayout body=column();body.setPadding(dp(20),dp(8),dp(20),dp(18));EditText search=new EditText(activity);search.setHint("搜索对话");search.setSingleLine(true);search.setTextColor(p.text);search.setHintTextColor(p.muted);search.setTextSize(15);search.setPadding(dp(16),0,dp(16),0);search.setBackground(shape(p.chip,22));body.addView(search,lp(-1,48));gap(body,16);
            LinearLayout list=column();body.addView(list);ScrollView scroll=new ScrollView(activity);scroll.addView(body);AlertDialog history=new RoundedDialogBuilder(activity).setTitle("最近对话").setView(scroll).setNegativeButton("返回",null).create();
            Runnable update=()->{list.removeAllViews();String query=search.getText().toString().trim().toLowerCase(java.util.Locale.ROOT);int count=0;
                for(JSONObject item:items){if(!item.optString("title").toLowerCase(java.util.Locale.ROOT).contains(query))continue;count++;
                    PocketUi.entry(list,p,"history",item.optString("title","新对话"),name(item.optString("provider"))+" · "+new java.text.SimpleDateFormat("M月d日 HH:mm",java.util.Locale.CHINA).format(new java.util.Date(item.optLong("updated"))),()->{
                        if(!idle())return;try{chat=store.load(item.getString("id"));provider=validProvider(chat.optString("provider"));prefs.edit().putString(stateKey("current"),chat.getString("id")).putString(stateKey("provider"),provider).apply();composer.setText(chat.optString("draft"));note("",false);restoreInputs();render();history.dismiss();}catch(Exception e){note("该会话无法读取，原文件已保留。",true);}
                    });
                }if(count==0)list.addView(text(items.isEmpty()?"第一段对话，从今天开始。":"没有找到相关对话",14,p.muted));
            };search.addTextChangedListener(new TextWatcher(){public void beforeTextChanged(CharSequence s,int start,int count,int after){}public void onTextChanged(CharSequence s,int start,int before,int count){update.run();}public void afterTextChanged(Editable e){}});update.run();history.show();
        } catch (Exception e) { note("手机历史读取失败。", true); }
    }
    private void delete() {
        if (!idle() || chat == null) return;
        new RoundedDialogBuilder(activity).setTitle("删除当前手机对话？").setMessage("这会删除本机保存的当前对话。").setNegativeButton("取消", null).setPositiveButton("删除", (d, which) -> {
            try {
                store.delete(chat.getString("id"));
                JSONArray turns=chat.optJSONArray("messages");
                for(int i=0;turns!=null&&i<turns.length();i++){JSONObject turn=turns.optJSONObject(i);if(turn!=null&&turn.optJSONArray("files")!=null)artifacts.discard(turn.getJSONArray("files"));if(turn!=null)discardInputs(turn.optJSONArray("attachments"));}
                discardInputs(draftInputs);
                createChat(); composer.setText(""); note("", false); render();
            } catch (Exception e) { note("删除失败，请检查手机存储。", true); }
        }).show();
    }
    private void discardInputs(JSONArray attached){for(int i=0;attached!=null&&i<attached.length();i++){JSONObject item=attached.optJSONObject(i);if(item!=null)inputs.discard(item);}}
    private EditText field(LinearLayout body, String caption, String value, boolean password) {
        body.addView(text(caption, 12, p.muted)); gap(body, 5);
        EditText edit = new EditText(activity); edit.setSingleLine(true); edit.setTextSize(14); edit.setTextColor(p.text); edit.setHintTextColor(p.muted); edit.setPadding(dp(12), dp(8), dp(12), dp(8)); edit.setBackground(shape(p.chip, 10));
        edit.setInputType(InputType.TYPE_CLASS_TEXT | (password ? InputType.TYPE_TEXT_VARIATION_PASSWORD : InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD)); edit.setText(value);
        if (password) { edit.setSaveEnabled(false); edit.setImportantForAutofill(View.IMPORTANT_FOR_AUTOFILL_NO); }
        body.addView(edit, lp(-1, 48)); gap(body, 14); return edit;
    }
    private void settings() { settings(provider); }
    private void settings(String targetProvider) {
        if (!idle()) return;
        if (settingsDialog != null && settingsDialog.isShowing()) return;
        final String selected = validProvider(targetProvider), oldBase = config(selected, "base");
        final ProviderCatalog.Entry entry=ProviderCatalog.find(selected);
        String existing;
        try { existing = secret(selected); } catch (Exception e) { existing = ""; }
        final String oldKey = existing;
        LinearLayout body = column(); body.setPadding(dp(22), dp(12), dp(22), dp(12));
        body.addView(text(name(selected), 18, p.text)); gap(body, 6);
        LinearLayout links=row();Button console=button("申请 API Key ↗",false),docs=button("接入文档 ↗",false);links.addView(console,new LinearLayout.LayoutParams(0,dp(44),1));links.addView(docs,new LinearLayout.LayoutParams(0,dp(44),1));body.addView(links);console.setOnClickListener(v->openProviderLink(entry.console));docs.setOnClickListener(v->openProviderLink(entry.docs));gap(body,8);body.addView(text(entry.hint,12,p.muted));gap(body,16);
        EditText address = field(body, "API 地址", oldBase, false);
        EditText key = field(body, selected.equals("spark")?"APIPassword":"API Key", "", true); key.setHint(oldKey.isEmpty() ? "粘贴该平台的 API Key" : "已加密保存，留空保留");
        EditText model = field(body, "模型 ID", chat.optString("provider").equals(selected) ? chat.optString("model") : config(selected, "model"), false); model.setHint("读取模型列表，或手动填写准确 ID");
        if(!entry.examples.isEmpty()){Button presets=button("常用模型",false);body.addView(presets,lp(-1,44));presets.setOnClickListener(v->modelPicker(entry.examples,model));gap(body,10);}
        body.addView(text("接口格式", 12, p.muted)); gap(body, 5);
        Spinner protocol = new Spinner(activity); protocol.setAdapter(new ArrayAdapter<>(activity, android.R.layout.simple_spinner_dropdown_item, new String[]{"Chat Completions", "Responses"}));
        protocol.setSelection("responses".equals(chat.optString("provider").equals(selected)?chat.optString("protocol",config(selected,"protocol")):config(selected,"protocol")) ? 1 : 0); body.addView(protocol, lp(-1, 46)); gap(body, 12);
        TextView feedback = text("密钥仅加密保存在手机，不发送到电脑。", 12, p.muted); body.addView(feedback); gap(body, 12);
        Button fetch = button("读取模型列表", false); body.addView(fetch, lp(-1, 44)); gap(body, 10);
        ScrollView container = new ScrollView(activity); container.addView(body);
        settingsDialog = new RoundedDialogBuilder(activity).setTitle("API 设置").setView(container).setPositiveButton("保存", null).setNegativeButton("取消", null).setNeutralButton("删除密钥", null).create();
        final AlertDialog dialog = settingsDialog;
        dialog.setOnDismissListener(d -> { key.setText(""); if (modelRequest != null) { modelRequest.cancel(); modelRequest = null; } }); dialog.show();
        fetch.setOnClickListener(v -> {
            try {
                String base = DirectEndpoint.normalize(address.getText().toString()); String credential = key.getText().toString().trim();
                if (credential.isEmpty()) { if (!DirectEndpoint.sameOrigin(base, oldBase)) throw new IOException("地址域名变更后，请重新填写该地址的密钥。"); credential = oldKey; }
                if (credential.isEmpty()) throw new IOException("请先填写该平台的 API Key。");
                final DirectApi client = new DirectApi(base, credential); modelRequest = client; fetch.setEnabled(false); feedback.setText("正在读取模型列表…");
                network.execute(() -> {
                    try { List<String> ids = client.models(); ui.post(() -> { if (closed || !dialog.isShowing() || modelRequest != client) return; modelRequest = null; fetch.setEnabled(true); feedback.setText("模型列表已读取，请选择支持对话的模型；实际权限以平台为准。"); modelPicker(ids, model); }); }
                    catch (Exception e) { String error = friendly(e); ui.post(() -> { if (closed || !dialog.isShowing() || modelRequest != client) return; modelRequest = null; fetch.setEnabled(true); feedback.setText(activity.getString(R.string.models_fetch_error,error)); }); }
                });
            } catch (Exception e) { feedback.setText(e.getMessage() == null ? "请检查 API 地址和密钥。" : e.getMessage()); }
        });
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            try {
                String base = DirectEndpoint.normalize(address.getText().toString()); String id = model.getText().toString().trim(); String credential = key.getText().toString().trim();
                if (id.isEmpty() || id.length() > 200 || id.matches(".*\\s.*")) { feedback.setText(R.string.model_id_required); return; }
                if (credential.isEmpty()) { if (!DirectEndpoint.sameOrigin(base, oldBase)) { feedback.setText("地址域名变更后，请重新填写该地址的密钥。"); return; } credential = oldKey; }
                if (credential.isEmpty()) { feedback.setText(R.string.api_key_required); return; }
                String mode = protocol.getSelectedItemPosition() == 1 ? "responses" : "chat";
                if (!save()) return;
                SecretStore.save(activity, "direct_" + selected, new JSONObject().put("base", base).put("key", credential).toString());
                if (!prefs.edit().putString(selected + ".base", base).putString(selected + ".model", id).putString(selected + ".protocol", mode).commit()) throw new IOException("无法保存 API 设置。");
                boolean changed = !base.equals(chat.optString("base")) || !id.equals(chat.optString("model")) || !mode.equals(chat.optString("protocol"));
                if (changed && selected.equals(provider)) {
                    if (chat.getJSONArray("messages").length() == 0) { chat.put("base", base).put("model", id).put("protocol", mode); save(); }
                    else { createChat(); composer.setText(""); }
                }
                dialog.dismiss(); note("API 设置已保存，可以发送消息了。", false); render();
            } catch (Exception e) { feedback.setText(R.string.api_save_failed); }
        });
        dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(v -> {
            try { SecretStore.save(activity, "direct_" + selected, ""); dialog.dismiss(); note("已删除该平台在手机上保存的密钥。", false); }
            catch (Exception e) { feedback.setText("无法删除密钥，请重试。"); }
        });
    }
    private void openProviderLink(String url){try{activity.startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse(url)));}catch(Exception e){toast("没有可用浏览器。");}}
    private void modelPicker(List<String> models, EditText destination) {
        LinearLayout body = column(); body.setPadding(dp(20), dp(8), dp(20), dp(8));
        EditText search = new EditText(activity); search.setSingleLine(true); search.setTextColor(p.text); search.setHintTextColor(p.muted); search.setHint("搜索模型 ID"); body.addView(search, lp(-1, 46));
        ListView list = new ListView(activity); ArrayAdapter<String> adapter = new ArrayAdapter<>(activity, android.R.layout.simple_list_item_1, new ArrayList<>(models)); list.setAdapter(adapter); body.addView(list, lp(-1, 300));
        AlertDialog picker = new RoundedDialogBuilder(activity).setTitle("选择模型").setView(body).setNegativeButton("取消", null).create();
        list.setOnItemClickListener((parent, view, position, id) -> { destination.setText(adapter.getItem(position)); picker.dismiss(); });
        search.addTextChangedListener(new TextWatcher() { public void beforeTextChanged(CharSequence s, int start, int count, int after) {} public void onTextChanged(CharSequence s, int start, int before, int count) { adapter.getFilter().filter(s); } public void afterTextChanged(Editable e) {} }); picker.show();
    }
}
