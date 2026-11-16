package com.codexpocket.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.content.Intent;
import android.content.Context;
import android.content.IntentFilter;
import android.net.Uri;
import android.database.Cursor;
import android.provider.OpenableColumns;
import android.graphics.BitmapFactory;
import android.graphics.Bitmap;
import android.view.Window;
import android.view.WindowManager;
import java.io.File;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.BackgroundColorSpan;
import android.text.style.StyleSpan;
import android.text.style.TypefaceSpan;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.MotionEvent;
import android.view.inputmethod.InputMethodManager;
import android.widget.*;
import org.json.JSONArray;
import org.json.JSONObject;
import java.net.URI;
import java.net.URLEncoder;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MainActivity extends Activity {
    private int BG, PANEL, BORDER, TEXT, MUTED, GREEN, ERROR;
    private ThemePalette palette;
    private String themeMode;
    private final Handler ui = new Handler(Looper.getMainLooper());
    private final ExecutorService requests = Executors.newSingleThreadExecutor();
    private final ExecutorService polls = Executors.newSingleThreadExecutor();
    private SharedPreferences prefs;
    private Api api;
    private RemoteFiles remoteFiles;
    private volatile int generation = 0;
    private volatile boolean foreground = false;
    private String threadId = "", renderedThread = "", base = "", token = "", model = "deepseek-flash", modelLabel = "DeepSeek Flash";
    private String approvalHash = "", connectionError = "", pendingId = "", pendingText = "", pendingThread = "";
    private long revision = 0;
    private boolean connected = false, busy = false, running = false;
    private LinearLayout root, messages, approvals, welcome;
    private LinearLayout shell;
    private FrameLayout appFrame;
    private IconView settingsGear;
    private android.database.ContentObserver assistantObserver;
    private final Runnable refreshAssistant=()->{if(this.assistantHub!=null)this.assistantHub.refresh();};
    private final android.content.BroadcastReceiver assistantChanges=new android.content.BroadcastReceiver(){@Override public void onReceive(Context c,Intent i){ui.removeCallbacks(refreshAssistant);ui.postDelayed(refreshAssistant,250);}};
    private boolean assistantReceiverRegistered;
    private ChatWallpaperDrawable androidWallpaper;
    private Boolean edgeChatLayout;
    private FrameLayout laboratoryPage;
    private AndroidChatView androidPage;
    private LaboratoryView laboratory;
    private AssistantHub assistantHub;
    private WorkspaceSwitch workspaceSwitch;
    private BottomFeatureBar workspaceBar;
    private WorkspaceSwitch codexModeSwitch;
    private WorkspacePager workspacePages;
    private WorkspacePager codexPages;
    private FrameLayout assistantPage;
    private View assistantHome,assistantConversation;
    private AndroidChatView assistantChat;
    private CommunicationCenter communications;
    private int featureDestination,codexMode;
    private boolean assistantConversationVisible;
    private boolean laboratorySelected;
    private int workspaceDestination;
    private boolean switchHidden, screenTouch;
    private long switchLastUsed;
    private float touchX, touchY;
    private final Runnable hideWorkspaceSwitch = () -> {
        if (!foreground || screenTouch || workspaceSwitch == null) return;
        switchHidden = true; updateConversationWindow();
    };
    private void showWorkspaceSwitch() {
        switchHidden = false; switchLastUsed=android.os.SystemClock.uptimeMillis(); updateConversationWindow(); scheduleSwitchHide();
    }
    private void scheduleSwitchHide() {
        ui.removeCallbacks(hideWorkspaceSwitch);
        if (foreground && !screenTouch && !switchHidden) ui.postDelayed(hideWorkspaceSwitch, Math.max(0,3000-(android.os.SystemClock.uptimeMillis()-switchLastUsed)));
    }
    @Override public boolean dispatchTouchEvent(MotionEvent event) {
        int action = event.getActionMasked();
        if (action == MotionEvent.ACTION_DOWN) { screenTouch = true; touchX = event.getX(); touchY = event.getY(); ui.removeCallbacks(hideWorkspaceSwitch);if(workspaceBar!=null&&!switchHidden&&touchY>getWindow().getDecorView().getHeight()-workspaceBar.getHeight()-dp(40))switchLastUsed=android.os.SystemClock.uptimeMillis(); }
        boolean handled = super.dispatchTouchEvent(event);
        if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
            screenTouch = false;
            if (action == MotionEvent.ACTION_UP && switchHidden && touchY > getWindow().getDecorView().getHeight()-dp(110) && Math.abs(event.getX()-touchX)<dp(12) && Math.abs(event.getY()-touchY)<dp(12)) showWorkspaceSwitch();
            else scheduleSwitchHide();
        }
        return handled;
    }
    private ScrollView scroll;
    private TextView connection, title, project, taskStatus, errorBanner, footer;
    private EditText composer;
    private Button send, stop;
    private final Map<String, TextView> itemTexts = new LinkedHashMap<>();
    private final Map<String, MathReplyView> mathReplies = new LinkedHashMap<>();
    private LinearLayout activityRow, attachmentStrip;
    private HorizontalScrollView attachmentScroll;
    private TextView modelChip;
    private final List<Attachment> attachments = new ArrayList<>();
    private final Map<String,String> itemAttachmentHashes = new HashMap<>();
    private JSONArray modelOptions = new JSONArray();
    private String busyLabel = "正在发送…", pendingFingerprint = "";

    @Override public void onCreate(Bundle state) {
        prefs = getSharedPreferences("settings", MODE_PRIVATE);
        themeMode=prefs.getString("themeMode","system");
        workspaceDestination=Math.max(WorkspaceSwitch.WINDOWS,Math.min(WorkspaceSwitch.LABORATORY,
            prefs.getInt("workspaceDestination",prefs.getBoolean("laboratorySelected",false)?WorkspaceSwitch.LABORATORY:WorkspaceSwitch.WINDOWS)));
        codexMode=AppNavigation.mode(prefs.getInt("codexMode",AppNavigation.migrateMode(workspaceDestination)));
        featureDestination=AppNavigation.section(prefs.getInt("featureDestination",AppNavigation.migrateSection(workspaceDestination)));
        workspaceDestination=featureDestination==AppNavigation.LAB?WorkspaceSwitch.LABORATORY:codexMode;
        laboratorySelected=featureDestination==AppNavigation.LAB;
        boolean systemDark=(getResources().getConfiguration().uiMode&Configuration.UI_MODE_NIGHT_MASK)==Configuration.UI_MODE_NIGHT_YES;
        palette=new ThemePalette(ThemePalette.isDark(themeMode,systemDark),prefs.getString("colorTheme","sage"));
        BG=palette.bg;PANEL=palette.panel;BORDER=palette.border;TEXT=palette.text;MUTED=palette.muted;GREEN=palette.accent;ERROR=palette.error;
        setTheme(palette.dark?R.style.AppThemeDark:R.style.AppTheme);
        getTheme().applyStyle(palette.accentStyle(),true);
        super.onCreate(state);
        getWindow().addFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE);
        remoteFiles = new RemoteFiles(this, palette);
        getWindow().setStatusBarColor(BG);getWindow().setNavigationBarColor(BG);
        base = prefs.getString("base", "");
        threadId = prefs.getString("threadId", "");
        model = prefs.getString("model", "deepseek-flash"); modelLabel = labelFor(model);
        if (modelLabel.isEmpty()) { model = "deepseek-flash"; modelLabel = labelFor(model); }
        pendingId = prefs.getString("pendingId", ""); pendingText = prefs.getString("pendingText", ""); pendingThread = prefs.getString("pendingThread", "");
        pendingFingerprint=prefs.getString("pendingFingerprint",pendingText+"|");
        try { token = SecretStore.load(this); } catch (Exception e) { connectionError = "配对信息无法解密，请重新填写令牌"; }
        if(BridgeEndpoint.configured(base,token))remoteFiles.restoreState(state,new Api(base,token));
        try{AssistantWork.store(this).recoverAndroid();}catch(Exception ignored){}AssistantNotifications.channels(this);
        buildUi();
        composer.setText(prefs.getString("draft", ""));ui.post(()->handleAssistantIntent(getIntent()));
    }
    @Override public void onStart() {
        super.onStart(); foreground = true;AssistantBackgroundService.start(this);AssistantWork.refreshRemote(this);
        if(laboratory!=null)laboratory.setActive(laboratorySelected);
        updateConversationWindow();
        showWorkspaceSwitch();
        if(assistantHub!=null){assistantHub.setActive(featureDestination==AppNavigation.ASSISTANT&&!assistantConversationVisible);assistantHub.refresh();}
        if(!assistantReceiverRegistered){IntentFilter filter=new IntentFilter(SmartRepository.CHANGED);
            // Old systems use the signature permission; API 33+ additionally
            // marks the receiver as private using its supported platform flag.
            int flags=android.os.Build.VERSION.SDK_INT>=33?Context.RECEIVER_NOT_EXPORTED:0;
            registerReceiver(assistantChanges,filter,getPackageName()+".permission.APP_EVENTS",null,flags);assistantReceiverRegistered=true;}
        assistantObserver=AssistantSync.observe(getApplicationContext());AssistantSync.request(this,false);
        if (!BridgeEndpoint.configured(base,token)) { if(featureDestination==AppNavigation.CODEX&&codexMode==WorkspaceSwitch.WINDOWS)ui.post(() -> {if(foreground&&featureDestination==AppNavigation.CODEX&&codexMode==WorkspaceSwitch.WINDOWS)showSettings();}); } else connect();
    }
    @Override public void onStop() {
        foreground = false; stopPolling();
        if(assistantReceiverRegistered){unregisterReceiver(assistantChanges);assistantReceiverRegistered=false;}if(assistantObserver!=null){getContentResolver().unregisterContentObserver(assistantObserver);assistantObserver=null;}ui.removeCallbacks(refreshAssistant);
        screenTouch=false;ui.removeCallbacks(hideWorkspaceSwitch);
        if(workspacePages!=null)workspacePages.cancelGesture();
        if(codexPages!=null)codexPages.cancelGesture();
        if(androidPage!=null&&!AssistantBackgroundService.running())androidPage.pause();
        if(assistantChat!=null&&!AssistantBackgroundService.running())assistantChat.pause();
        if(assistantHub!=null)assistantHub.setActive(false);
        if(laboratory!=null)laboratory.setActive(false);
        updateConversationWindow();
        persistAttachments();
        prefs.edit().putString("draft", composer.getText().toString()).apply();
        super.onStop();
    }
    @Override public void onDestroy() { stopPolling(); releaseMathReplies(); requests.shutdownNow(); polls.shutdownNow(); if(communications!=null)communications.close(); if(assistantHub!=null)assistantHub.close(); if(assistantChat!=null)assistantChat.close(); if(laboratory!=null)laboratory.close(); if(androidPage!=null)androidPage.close(); if(remoteFiles!=null)remoteFiles.close(); super.onDestroy(); }
    @Override public void onBackPressed(){if(laboratorySelected&&laboratory!=null&&laboratory.back())return;if(featureDestination==AppNavigation.ASSISTANT&&assistantConversationVisible){showAssistant();return;}if(featureDestination!=AppNavigation.CODEX){selectSection(AppNavigation.CODEX,true);return;}if(codexMode==WorkspaceSwitch.ANDROID){selectWorkspace(WorkspaceSwitch.WINDOWS,true);return;}super.onBackPressed();}
    @Override public void onWindowFocusChanged(boolean focused){super.onWindowFocusChanged(focused);if(focused){updateConversationWindow();if(assistantHub!=null)assistantHub.resumeVoiceIfRequested();}}
    private void updateConversationWindow(){
        boolean immersive=foreground&&laboratorySelected&&laboratory!=null&&laboratory.isInConversation();
        boolean edgeChat=featureDestination==AppNavigation.CODEX&&codexMode==WorkspaceSwitch.ANDROID;
        if(settingsGear!=null){FrameLayout.LayoutParams gear=(FrameLayout.LayoutParams)settingsGear.getLayoutParams();int inset=0;if(edgeChat&&appFrame.getRootWindowInsets()!=null)inset=appFrame.getRootWindowInsets().getSystemWindowInsetTop();gear.topMargin=dp(immersive?14:6)+inset;settingsGear.setLayoutParams(gear);settingsGear.bringToFront();settingsGear.setBackground(PocketUi.touch(this,(PANEL&0xffffff)|0xd9000000,22,BORDER,TEXT));}
        if(shell!=null){
            android.graphics.drawable.Drawable backdrop=edgeChat?androidWallpaper:null;
            if(backdrop!=null){if(shell.getBackground()!=backdrop)shell.setBackground(backdrop);}
            else if(edgeChatLayout==null||edgeChatLayout!=edgeChat)shell.setBackgroundColor(BG);
        }
        if(edgeChatLayout==null||edgeChatLayout!=edgeChat){
            edgeChatLayout=edgeChat;
            Window window=getWindow();
            window.setStatusBarColor(edgeChat?Color.TRANSPARENT:BG);window.setNavigationBarColor(edgeChat?Color.TRANSPARENT:BG);
            if(android.os.Build.VERSION.SDK_INT>=30)window.setDecorFitsSystemWindows(!edgeChat);
            if(android.os.Build.VERSION.SDK_INT>=29){window.setStatusBarContrastEnforced(!edgeChat);window.setNavigationBarContrastEnforced(!edgeChat);}
            if(android.os.Build.VERSION.SDK_INT>=28){WindowManager.LayoutParams params=window.getAttributes();params.layoutInDisplayCutoutMode=edgeChat?WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES:WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_DEFAULT;window.setAttributes(params);}
            if(shell!=null){shell.setPadding(0,0,0,0);shell.requestApplyInsets();}
        }
        if(workspaceBar!=null)workspaceBar.setExpanded(!immersive&&!switchHidden,foreground&&!immersive);
        View decor=getWindow().getDecorView();
        if(android.os.Build.VERSION.SDK_INT>=30){
            android.view.WindowInsetsController controller=decor.getWindowInsetsController();
            if(controller!=null){if(immersive){controller.setSystemBarsBehavior(android.view.WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);controller.hide(android.view.WindowInsets.Type.systemBars());}else controller.show(android.view.WindowInsets.Type.systemBars());}
        }else{
            int flags=palette.dark?0:View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR|View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
            if(immersive)flags|=View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY|View.SYSTEM_UI_FLAG_FULLSCREEN|View.SYSTEM_UI_FLAG_HIDE_NAVIGATION|View.SYSTEM_UI_FLAG_LAYOUT_STABLE;
            else if(edgeChat)flags|=View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN|View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION|View.SYSTEM_UI_FLAG_LAYOUT_STABLE;
            decor.setSystemUiVisibility(flags);
        }
    }
    @Override protected void onSaveInstanceState(Bundle state) { super.onSaveInstanceState(state); if(remoteFiles!=null)remoteFiles.saveState(state); if(androidPage!=null)androidPage.saveState(state);if(assistantChat!=null)assistantChat.saveState(state); }

    private int dp(float n) { return (int) (n * getResources().getDisplayMetrics().density + .5f); }
    private LinearLayout column() { LinearLayout l = new LinearLayout(this); l.setOrientation(LinearLayout.VERTICAL); return l; }
    private LinearLayout row() { LinearLayout l = new LinearLayout(this); l.setOrientation(LinearLayout.HORIZONTAL); l.setGravity(Gravity.CENTER_VERTICAL); return l; }
    private LinearLayout.LayoutParams lp(int w, int h) { return new LinearLayout.LayoutParams(w < 0 ? w : dp(w), h < 0 ? h : dp(h)); }
    private GradientDrawable background(int color, int radius) { GradientDrawable d = new GradientDrawable(); d.setColor(color); d.setCornerRadius(dp(radius)); return d; }
    private GradientDrawable outline(int color, int radius) { GradientDrawable d = background(color, radius); d.setStroke(dp(1), BORDER); return d; }
    private TextView text(String value, int size, int color) { TextView t = new TextView(this); t.setText(value); t.setTextSize(size); t.setTextColor(color); t.setLineSpacing(dp(3), 1); return t; }
    private Button button(String value, boolean primary) {
        Button b = new Button(this); b.setText(value); b.setTextSize(14); b.setAllCaps(false);
        PocketUi.button(b,palette,primary);
        b.setMinHeight(dp(44)); b.setMinimumHeight(dp(44)); b.setPadding(dp(12), 0, dp(12), 0); return b;
    }
    private void gap(LinearLayout parent, int height) { parent.addView(new View(this), lp(1, height)); }
    private void spacer(LinearLayout parent) { parent.addView(new View(this), new LinearLayout.LayoutParams(0, 1, 1)); }
    private IconView icon(String name, String description, int color) {
        IconView view = new IconView(this, name, color, description);
        return view;
    }
    private ImageView brandImage(String brand,int size){
        ImageView image=new ImageView(this);image.setScaleType(ImageView.ScaleType.FIT_CENTER);image.setContentDescription(brand+" 标识");
        int resource=R.drawable.ic_launcher;
        image.setImageTintList(null);image.clearColorFilter();image.setImageResource(resource);
        image.setMinimumWidth(dp(size));image.setMinimumHeight(dp(size));
        return image;
    }
    private String themeLabel(String mode){return "light".equals(mode)?"浅色模式":"dark".equals(mode)?"深色模式":"跟随系统";}
    private void showAppearance(){
        if(assistantHub!=null)assistantHub.pause();LinearLayout body=dialogBody();PocketUi.section(body,palette,"显示模式");AlertDialog sheet=dialog("个性化",body);
        String[] modes={"system","light","dark"};
        for(String mode:modes){LinearLayout choice=row();choice.setPadding(dp(14),dp(13),dp(14),dp(13));choice.setBackground(outline(mode.equals(themeMode)?palette.selected:PANEL,15));choice.addView(icon(mode.equals("light")?"sun":mode.equals("dark")?"moon":"system",themeLabel(mode),TEXT),lp(40,42));choice.addView(text(themeLabel(mode),15,TEXT),new LinearLayout.LayoutParams(0,-2,1));if(mode.equals(themeMode))choice.addView(icon("check","当前外观",GREEN),lp(28,32));body.addView(choice,lp(-1,-2));gap(body,10);choice.setOnClickListener(v->{sheet.dismiss();changeTheme(mode);});}
        PocketUi.section(body,palette,"颜色主题");
        for(int group=0;group<4;group++){LinearLayout colorsRow=row();for(int slot=0;slot<4;slot++){String color=ThemePalette.COLOR_IDS[group*4+slot];boolean selected=color.equals(palette.colorTheme);ThemePalette sample=new ThemePalette(palette.dark,color);LinearLayout choice=column();choice.setGravity(Gravity.CENTER);choice.setPadding(dp(3),dp(10),dp(3),dp(8));choice.setBackground(PocketUi.touch(this,selected?palette.selected:PANEL,18,selected?GREEN:BORDER,TEXT));choice.setContentDescription(ThemePalette.colorName(color)+(selected?"，已选择":""));View swatch=selected?new IconView(this,"check",sample.onAccent,""):new View(this);swatch.setBackground(background(sample.accent,16));choice.addView(swatch,lp(30,30));TextView colorName=text(ThemePalette.colorName(color),12,TEXT);colorName.setGravity(Gravity.CENTER);colorName.setSingleLine(true);colorName.setPadding(0,dp(7),0,0);choice.addView(colorName,lp(-1,-2));LinearLayout.LayoutParams item=new LinearLayout.LayoutParams(0,dp(80),1);if(slot>0)item.leftMargin=dp(6);colorsRow.addView(choice,item);choice.setOnClickListener(v->{sheet.dismiss();changePersonalization(themeMode,color);});}body.addView(colorsRow,lp(-1,-2));gap(body,8);}
        gap(body,10);body.addView(text("颜色会应用到按钮、卡片和消息气泡，并自动保存。",11,MUTED));sheet.show();
    }
    private void changeTheme(String mode){
        changePersonalization(mode,palette.colorTheme);
    }
    private void changePersonalization(String mode,String color){if(mode.equals(themeMode)&&color.equals(palette.colorTheme))return;if(busy||(androidPage!=null&&androidPage.isBusy())||(assistantChat!=null&&assistantChat.isBusy())||attachments.stream().anyMatch(a->a.preparing)){toast("请等待消息发送或附件准备完成后切换个性化");return;}if(assistantHub!=null)assistantHub.saveDraft();prefs.edit().putString("themeMode",mode).putString("colorTheme",ThemePalette.normalizeColor(color)).putString("draft",composer.getText().toString()).apply();persistAttachments();recreate();}
    private void buildUi() {
        appFrame=new FrameLayout(this);shell=column();shell.setBackgroundColor(BG);appFrame.addView(shell,new FrameLayout.LayoutParams(-1,-1));setContentView(appFrame);
        // Only controls receive safe-area/IME padding; the wallpaper always
        // paints the full window, including beneath transparent system bars.
        shell.setOnApplyWindowInsetsListener((view,insets)->{
            if(featureDestination==AppNavigation.CODEX&&codexMode==WorkspaceSwitch.ANDROID){
                int left,top,right,bottom;
                if(android.os.Build.VERSION.SDK_INT>=30){
                    android.graphics.Insets bars=insets.getInsets(android.view.WindowInsets.Type.systemBars()|android.view.WindowInsets.Type.displayCutout());
                    android.graphics.Insets ime=insets.getInsets(android.view.WindowInsets.Type.ime());
                    left=bars.left;top=bars.top;right=bars.right;bottom=Math.max(bars.bottom,ime.bottom);
                }else{
                    left=insets.getSystemWindowInsetLeft();top=insets.getSystemWindowInsetTop();right=insets.getSystemWindowInsetRight();bottom=insets.getSystemWindowInsetBottom();
                    if(android.os.Build.VERSION.SDK_INT>=28&&insets.getDisplayCutout()!=null){android.view.DisplayCutout cutout=insets.getDisplayCutout();left=Math.max(left,cutout.getSafeInsetLeft());top=Math.max(top,cutout.getSafeInsetTop());right=Math.max(right,cutout.getSafeInsetRight());bottom=Math.max(bottom,cutout.getSafeInsetBottom());}
                }
                view.setPadding(left,top,right,bottom);
            }else view.setPadding(0,0,0,0);
            return insets;
        });
        workspaceSwitch=new WorkspaceSwitch(this,palette,selected->selectSection(selected,true),new String[]{"Codex","AI助理","实验室"});
        workspaceBar=new BottomFeatureBar(this,palette,workspaceSwitch);
        WorkspacePager features=new WorkspacePager(this,destination->selectSection(destination,true));workspacePages=features;shell.addView(features,new LinearLayout.LayoutParams(-1,0,1));
        features.onSwipeActivity(this::showWorkspaceSwitch);
        LinearLayout codex=column();features.addView(codex,new FrameLayout.LayoutParams(-1,-1));
        FrameLayout modeHeader=new FrameLayout(this){@Override protected void onMeasure(int width,int height){if(codexModeSwitch!=null){android.view.ViewGroup.LayoutParams tab=codexModeSwitch.getLayoutParams();int size=Math.min(dp(224),Math.max(dp(120),View.MeasureSpec.getSize(width)-dp(120)));if(tab.width!=size){tab.width=size;codexModeSwitch.setLayoutParams(tab);}}super.onMeasure(width,height);}};codex.addView(modeHeader,lp(-1,52));
        codexModeSwitch=new WorkspaceSwitch(this,palette,mode->selectWorkspace(mode,true),new String[]{"Windows","Android"});FrameLayout.LayoutParams modeLp=new FrameLayout.LayoutParams(dp(224),dp(44),Gravity.TOP|Gravity.CENTER_HORIZONTAL);modeLp.topMargin=dp(4);modeHeader.addView(codexModeSwitch,modeLp);
        WorkspacePager pages=new WorkspacePager(this,destination->selectWorkspace(destination,true));codexPages=pages;pages.setSwipingEnabled(false);codex.addView(pages,new LinearLayout.LayoutParams(-1,0,1));
        pages.onSwipeActivity(this::showWorkspaceSwitch);
        root = column(); root.setBackgroundColor(BG); root.setPadding(dp(18), dp(4), dp(18), dp(10));pages.addView(root,new FrameLayout.LayoutParams(-1,-1));
        androidPage=new AndroidChatView(this,palette,()->showAppearance());androidPage.setContentDescription("Android 手机直连 API");
        androidPage.onAssistant(this::showAssistant);
        androidPage.onWallpaperChanged(provider->{androidWallpaper=new ChatWallpaperDrawable(this,provider,palette);if(featureDestination==AppNavigation.CODEX&&codexMode==WorkspaceSwitch.ANDROID)shell.setBackground(androidWallpaper);});
        pages.addView(androidPage,new FrameLayout.LayoutParams(-1,-1));
        assistantPage=new FrameLayout(this);assistantPage.setBackgroundColor(BG);assistantPage.setContentDescription("AI助理");features.addView(assistantPage,new FrameLayout.LayoutParams(-1,-1));
        communications=new CommunicationCenter(this,palette);
        assistantHub=new AssistantHub(this,palette,prompt->showAssistantChat(prompt),()->{showAssistantChat("");assistantChat.openHistory();},()->{showAssistantChat("");assistantChat.openFiles();},()->selectWorkspace(WorkspaceSwitch.WINDOWS,true),()->selectSection(AppNavigation.LAB,true),()->communications.show());assistantHub.onConversationInput(prompt->{if(assistantChat!=null&&assistantChat.isBusy())return false;showAssistantChat("");return assistantChat.sendAssistantInput(prompt);});assistantHub.onWorkInput(this::dispatchAssistantWork,this::openAssistantWork);assistantHome=assistantHub.createView();assistantPage.addView(assistantHome,new FrameLayout.LayoutParams(-1,-1));
        laboratoryPage=new FrameLayout(this);laboratoryPage.setBackgroundColor(BG);laboratoryPage.setContentDescription("实验室");features.addView(laboratoryPage,new FrameLayout.LayoutParams(-1,-1));
        laboratory=new LaboratoryView(this,palette,prefs,provider->{selectWorkspace(WorkspaceSwitch.ANDROID,true);androidPage.openApiSettings(provider);});laboratoryPage.addView(laboratory,new FrameLayout.LayoutParams(-1,-1));
        laboratory.onConversation(value->updateConversationWindow());
        LinearLayout head = row(); head.setPadding(0, dp(3), 0, dp(3));
        IconView menu = icon("grid", "打开工作台与AI助理", TEXT); head.addView(menu, lp(44, 48)); menu.setOnClickListener(v -> showMenu());
        LinearLayout center = column(); center.setGravity(Gravity.CENTER);
        LinearLayout titleRow=row();titleRow.setGravity(Gravity.CENTER);ImageView titleLogo=brandImage("codex",24);LinearLayout.LayoutParams titleLogoParams=lp(24,24);titleLogoParams.rightMargin=dp(8);titleRow.addView(titleLogo,titleLogoParams);
        title = text("新对话", 17, TEXT); title.setTypeface(null, Typeface.BOLD); title.setMaxLines(1); title.setEllipsize(android.text.TextUtils.TruncateAt.END); title.setGravity(Gravity.CENTER);titleRow.addView(title,new LinearLayout.LayoutParams(-2,-2));center.addView(titleRow,lp(-1,-2));
        connection = text("连接电脑", 10, MUTED); connection.setGravity(Gravity.CENTER); center.addView(connection, lp(-1,-2)); center.setOnClickListener(v -> showConversationInfo());
        head.addView(center, new LinearLayout.LayoutParams(0,-2,1));
        IconView add = icon("new", "新建对话", TEXT); head.addView(add, lp(44,48)); add.setOnClickListener(v -> newSession()); root.addView(head);
        LinearLayout modelRow = row(); modelRow.setGravity(Gravity.CENTER); modelChip = text(modelLabel + "  ⌄", 12, MUTED); modelChip.setPadding(dp(14),dp(8),dp(14),dp(8)); modelChip.setBackground(background(palette.chip,20)); modelRow.addView(modelChip); modelChip.setOnClickListener(v -> chooseModel(null)); root.addView(modelRow,lp(-1,45));
        project = text("",11,MUTED); project.setVisibility(View.GONE);
        errorBanner = text("",12,ERROR); errorBanner.setPadding(dp(12),dp(10),dp(12),dp(10)); errorBanner.setMaxLines(3); errorBanner.setEllipsize(android.text.TextUtils.TruncateAt.END); errorBanner.setBackground(background(palette.errorSurface,12)); errorBanner.setVisibility(View.GONE); errorBanner.setOnClickListener(v -> new RoundedDialogBuilder(this).setTitle("连接与任务提示").setMessage(errorBanner.getText()).setPositiveButton("知道了",(d,w)->clearError()).setNeutralButton("连接设置",(d,w)->showSettings()).show()); root.addView(errorBanner);
        scroll = new ScrollView(this); scroll.setFillViewport(true); scroll.setClipToPadding(false); scroll.setPadding(0,dp(16),0,dp(8)); scroll.setVerticalScrollBarEnabled(false);
        LinearLayout content = column(); messages = column(); approvals = column(); welcome = column(); welcome.setGravity(Gravity.START); welcome.setPadding(dp(4),dp(24),dp(4),dp(24));
        LinearLayout brand=row();ImageView emblem=brandImage("codex",38);brand.addView(emblem,lp(38,38));TextView welcomeBrand=text("  Pocket Assistant",15,TEXT);welcomeBrand.setTypeface(Typeface.create("sans-serif-medium",Typeface.NORMAL));brand.addView(welcomeBrand);welcome.addView(brand);gap(welcome,22);
        TextView greeting = text("让想法，\n往前走一步。",32,TEXT); greeting.setTypeface(Typeface.create("sans-serif-medium",Typeface.NORMAL));welcome.addView(greeting);gap(welcome,10);
        welcome.addView(text("写作、文件、代码。\n从一件你想完成的事开始。",14,MUTED));gap(welcome,24);
        PocketUi.entry(welcome,palette,"assistant","AI助理","待办、随手记与最近成果",this::showAssistant);
        PocketUi.entry(welcome,palette,"system","电脑工作台","选择一个项目，继续工作",this::newSession);
        content.addView(welcome,lp(-1,-2)); content.addView(messages,lp(-1,-2)); content.addView(approvals,lp(-1,-2)); scroll.addView(content); root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        activityRow = row(); taskStatus = text("",11,MUTED); activityRow.addView(taskStatus,new LinearLayout.LayoutParams(0,-2,1)); stop=button("停止",false);stop.setTextColor(ERROR);activityRow.addView(stop,lp(64,38)); stop.setOnClickListener(v->{if(api==null||threadId.isEmpty())return;final Api c=api;final String id=threadId;perform(()->c.post(path(id)+"/interrupt",new JSONObject()),r->toast("已请求停止"));});root.addView(activityRow,lp(-1,42));
        LinearLayout inputBox=column();inputBox.setPadding(dp(10),dp(8),dp(10),dp(8));inputBox.setBackground(outline(PANEL,28));inputBox.setElevation(dp(2));
        attachmentScroll=new HorizontalScrollView(this);attachmentScroll.setHorizontalScrollBarEnabled(false);attachmentStrip=row();attachmentScroll.addView(attachmentStrip);attachmentScroll.setVisibility(View.GONE);inputBox.addView(attachmentScroll,lp(-1,-2));
        composer=new EditText(this);composer.setTextColor(TEXT);composer.setHintTextColor(MUTED);composer.setTextSize(16);composer.setBackgroundColor(Color.TRANSPARENT);composer.setHint("今天，想一起完成什么？");composer.setMinLines(1);composer.setMaxLines(5);composer.setGravity(Gravity.TOP);composer.setPadding(dp(8),dp(12),dp(8),dp(10));composer.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_FLAG_MULTI_LINE|InputType.TYPE_TEXT_FLAG_CAP_SENTENCES); inputBox.addView(composer,lp(-1,-2));
        LinearLayout tools=row();IconView attach=icon("plus","添加图片或文件",MUTED);tools.addView(attach,lp(42,42));attach.setOnClickListener(v->showAttachmentMenu());spacer(tools);send=button("↑",true);send.setTextSize(22);send.setPadding(0,0,0,0);send.setContentDescription("发送消息");tools.addView(send,lp(44,42));send.setOnClickListener(v->sendMessage());inputBox.addView(tools);root.addView(inputBox,lp(-1,-2));
        footer=text("通过 Tailscale 连接你的电脑",10,MUTED);footer.setGravity(Gravity.CENTER);footer.setPadding(0,dp(9),0,0);root.addView(footer);restoreAttachments();updateControls();
        shell.addView(workspaceBar,lp(-1,54));settingsGear=icon("settings","设置",TEXT);FrameLayout.LayoutParams gear=new FrameLayout.LayoutParams(dp(44),dp(44),Gravity.TOP|Gravity.END);gear.setMarginEnd(dp(12));gear.topMargin=dp(6);appFrame.addView(settingsGear,gear);settingsGear.setOnClickListener(v->showUnifiedSettings());codexPages.setDestination(codexMode,false);codexModeSwitch.setDestination(codexMode,true);selectSection(featureDestination,false);if(featureDestination==AppNavigation.ASSISTANT&&prefs.getBoolean("assistantConversationOpen",false))showAssistantChat("");
    }
    private void selectWorkspace(int destination,boolean animate) {
        if(destination==WorkspaceSwitch.LABORATORY){selectSection(AppNavigation.LAB,animate);return;}
        codexMode=AppNavigation.mode(destination);codexPages.setDestination(codexMode,animate);codexModeSwitch.setDestination(codexMode,true);
        prefs.edit().putInt("codexMode",codexMode).apply();selectSection(AppNavigation.CODEX,animate);showWorkspaceSwitch();
        if(foreground&&codexMode==WorkspaceSwitch.WINDOWS&&!BridgeEndpoint.configured(base,token))showSettings();
    }
    private void selectSection(int destination,boolean animate){
        destination=AppNavigation.section(destination);boolean changed=featureDestination!=destination;featureDestination=destination;
        workspaceDestination=destination==AppNavigation.LAB?WorkspaceSwitch.LABORATORY:codexMode;
        laboratorySelected=destination==AppNavigation.LAB;workspacePages.setDestination(destination,animate);workspaceSwitch.setDestination(destination,animate);workspaceBar.setSection(destination);
        if(laboratory!=null)laboratory.setActive(laboratorySelected&&foreground);
        if(assistantHub!=null)assistantHub.setActive(destination==AppNavigation.ASSISTANT&&!assistantConversationVisible&&foreground);
        if(destination==AppNavigation.ASSISTANT&&assistantHub!=null&&!assistantConversationVisible)assistantHub.refresh();
        if(changed){View focus=getCurrentFocus();if(focus!=null){focus.clearFocus();InputMethodManager keyboard=(InputMethodManager)getSystemService(INPUT_METHOD_SERVICE);if(keyboard!=null)keyboard.hideSoftInputFromWindow(focus.getWindowToken(),0);}showWorkspaceSwitch();}
        updateConversationWindow();prefs.edit().putInt("featureDestination",destination).putInt("codexMode",codexMode).putInt("workspaceDestination",workspaceDestination).putBoolean("laboratorySelected",laboratorySelected).apply();
    }

    private interface Work { JSONObject run() throws Exception; }
    private interface Done { void accept(JSONObject result) throws Exception; }
    private void perform(Work work, Done done) {
        requests.execute(() -> { try { JSONObject value = work.run(); ui.post(() -> { if (isDestroyed()) return; try { done.accept(value); } catch (Exception e) { showError(e.getMessage()); } }); }
            catch (Exception e) { ui.post(() -> { if (!isDestroyed()) showError(friendly(e)); }); } });
    }
    private String friendly(Exception e) {
        if (e instanceof java.net.ConnectException || e instanceof java.net.SocketTimeoutException || e instanceof java.net.UnknownHostException)
            return "无法连接电脑。请确认两端 Tailscale 已开启、电脑未休眠，且 start-server.cmd 正在运行。";
        return e.getMessage() == null ? e.toString() : e.getMessage();
    }
    private void toast(String text) { Toast.makeText(this, text, Toast.LENGTH_SHORT).show(); }
    private void showError(String error) { errorBanner.setText(error); errorBanner.setVisibility(View.VISIBLE); }
    private void clearError() { errorBanner.setVisibility(View.GONE); }
    private void setConnection(boolean value, String detail) {
        connected = value; connectionError = value ? "" : detail;
        connection.setText(value ? "●  电脑已连接" : "○  " + detail); connection.setTextColor(value ? palette.success : MUTED); updateControls();
    }
    private void updateControls() {
        if (send == null) return;
        send.setEnabled(connected && !busy && !running && attachments.stream().noneMatch(a->a.preparing)); send.setAlpha(send.isEnabled() ? 1 : .4f);
        stop.setVisibility(running ? View.VISIBLE : View.GONE);
        activityRow.setVisibility(busy||running?View.VISIBLE:View.GONE);
        taskStatus.setText(busy ? busyLabel : "Codex 正在工作…");
        taskStatus.setTextColor(running ? GREEN : MUTED);
    }
    private void connect() {
        stopPolling();
        if(!BridgeEndpoint.configured(base,token)){api=null;setConnection(false,getString(R.string.computer_not_configured));return;}
        api = new Api(base, token); final Api client = api; final int expected = generation;
        setConnection(false, "正在连接…");
        requests.execute(() -> {
            try {
                JSONObject status = client.get("/api/status");
                if(!status.optString("version").startsWith("2."))throw new Exception("电脑端服务需要更新：请关闭旧服务，重新运行 start-server.cmd。");
                if (!status.optBoolean("ready")) throw new Exception(status.optString("error", "Codex 尚未就绪"));
                if (!status.optBoolean("authenticated")) throw new Exception("电脑上的 Codex 尚未登录。请在 CMD 中运行 codex login，再重启电脑端服务。");
                if (expected != generation || !foreground) return;
                ui.post(() -> {
                    if (expected != generation || isDestroyed()) return;
                    clearError(); setConnection(true, "");
                    modelOptions=status.optJSONArray("models");
                    if (prefs.getString("cwd", "").isEmpty()) prefs.edit().putString("cwd", status.optString("defaultCwd")).apply();
                    if (!threadId.isEmpty()) openSession(threadId); else startPolling();
                });
            } catch (Exception e) { ui.post(() -> { if (expected != generation || isDestroyed()) return; setConnection(false, "连接失败 · 点击设置重连"); showError(friendly(e)); }); }
        });
    }
    private void stopPolling() { generation++; if (api != null) api.cancelPoll(); }
    private void startPolling() {
        stopPolling(); if (!foreground || api == null) return;
        final int current = generation; final Api client = api; final String id = threadId; revision = 0;
        polls.execute(() -> {
            long after = 0; int failures = 0;
            while (foreground && generation == current && !Thread.currentThread().isInterrupted()) {
                try {
                    JSONObject snapshot = client.poll("/api/events?after=" + after + (id.isEmpty() ? "" : "&threadId=" + enc(id)));
                    after = snapshot.optLong("revision"); failures = 0;
                    ui.post(() -> {
                        if (generation != current || !foreground || isDestroyed()) return;
                        if (!connected && snapshot.optBoolean("ready")) clearError();
                        setConnection(snapshot.optBoolean("ready"), snapshot.optString("error", "Codex 已断开")); render(snapshot);
                    });
                } catch (Exception e) {
                    if (generation != current || !foreground) break;
                    failures++;
                    ui.post(() -> { if (generation == current && !isDestroyed()) { setConnection(false, "网络中断 · 自动重连中"); showError(friendly(e)); } });
                    try { Thread.sleep(Math.min(10000, failures * 2000L)); } catch (InterruptedException interrupted) { break; }
                }
            }
        });
    }
    private static String enc(String text) { try { return URLEncoder.encode(text, "UTF-8"); } catch (Exception e) { return text; } }
    private String path(String id) { return "/api/threads/" + enc(id); }

    private EditText field(LinearLayout parent, String label, String value, boolean secret) {
        TextView caption = text(label, 12, MUTED); parent.addView(caption); gap(parent, 5);
        EditText e = new EditText(this); e.setSingleLine(true); e.setTextSize(14); e.setTextColor(TEXT); e.setHintTextColor(MUTED); e.setPadding(dp(12), dp(12), dp(12), dp(12)); e.setBackground(outline(BG, 10));
        e.setInputType(InputType.TYPE_CLASS_TEXT | (secret ? InputType.TYPE_TEXT_VARIATION_PASSWORD : InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD)); e.setText(value); parent.addView(e, lp(-1, 50)); gap(parent, 16); return e;
    }
    private LinearLayout dialogBody() { LinearLayout l = column(); l.setPadding(dp(22), dp(12), dp(22), dp(6)); return l; }
    private AlertDialog dialog(String name, View content) {
        LinearLayout sheet = new LinearLayout(this) {
            @Override protected void onMeasure(int w, int h) { int cap=(int)(getResources().getDisplayMetrics().heightPixels*.86);if(View.MeasureSpec.getMode(h)!=View.MeasureSpec.UNSPECIFIED)cap=Math.min(cap,View.MeasureSpec.getSize(h));super.onMeasure(w,View.MeasureSpec.makeMeasureSpec(cap,View.MeasureSpec.AT_MOST)); }
        };
        sheet.setOrientation(LinearLayout.VERTICAL); sheet.setPadding(0,dp(9),0,dp(16));
        View handle=new View(this);handle.setBackground(background(BORDER,3));LinearLayout.LayoutParams hp=lp(34,4);hp.gravity=Gravity.CENTER_HORIZONTAL;sheet.addView(handle,hp);
        LinearLayout header=row();header.setPadding(dp(22),dp(10),dp(12),dp(3));TextView heading=text(name,20,TEXT);heading.setTypeface(null,Typeface.BOLD);header.addView(heading,new LinearLayout.LayoutParams(0,-2,1));IconView close=icon("close","关闭菜单",MUTED);header.addView(close,lp(44,44));sheet.addView(header);
        if(content instanceof ScrollView)sheet.addView(content,lp(-1,-2));else{ScrollView scroller=new ScrollView(this);scroller.setFillViewport(false);scroller.addView(content);sheet.addView(scroller,lp(-1,-2));}
        AlertDialog d=new RoundedDialogBuilder(this).setView(sheet).create();close.setOnClickListener(v->d.dismiss());if(!name.equals("设置")){IconView gear=icon("settings","设置",TEXT);header.addView(gear,header.getChildCount()-1,lp(44,44));gear.setOnClickListener(v->{d.dismiss();showUnifiedSettings();});}
        d.setOnShowListener(v->{Window window=d.getWindow();if(window!=null){window.setBackgroundDrawable(background(PANEL,24));window.setGravity(Gravity.BOTTOM);window.setLayout(-1,-2);window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);}});return d;
    }
    private String labelFor(String id) { switch(id) { case "deepseek-flash":return "DeepSeek Flash";case "kimi-k3.1-pro":return "Kimi 3.1 Pro";case "gpt-6.1-sol":return "ChatGPT 6.1 Sol";default:return "";} }
    void showUnifiedSettings(){
        if(assistantHub!=null)assistantHub.pause();
        LinearLayout body=dialogBody();AlertDialog settings=dialog("设置",body);
        menuEntry(body,"settings","隐私与数据","默认关闭自动接入和云端资料共享",()->{settings.dismiss();PrivacySettings.show(this,palette);});
        menuEntry(body,"assistant","自动智能管理","同步、AI 整理与后台运行",()->{settings.dismiss();communications.automation();});
        menuEntry(body,"inbox","通知与后台运行","应用提醒、后台开关与系统设置",()->{settings.dismiss();AssistantNotificationSettings.show(this,palette);});
        menuEntry(body,"inbox","信息来源与权限","短信、电话、通知与邮箱",()->{settings.dismiss();communications.show();});
        menuEntry(body,"mail","邮箱账户","添加或管理通用邮箱",()->{settings.dismiss();communications.accounts();});
        PocketUi.section(body,palette,"偏好与连接");
        menuEntry(body,"settings","模型与 API","手机聊天与AI助理共用配置",()->{settings.dismiss();if(assistantChat!=null&&featureDestination==AppNavigation.ASSISTANT)assistantChat.openApiSettings();else androidPage.openApiSettings();});
        menuEntry(body,"mic","语音输入","普通话识别、系统服务与离线模式",()->{settings.dismiss();RecognitionSettings.show(this,palette);});
        menuEntry(body,"theme","个性化",themeLabel(themeMode)+" · "+ThemePalette.colorName(palette.colorTheme),()->{settings.dismiss();showAppearance();});
        menuEntry(body,"system","电脑连接","Tailscale、配对与远程代理",()->{settings.dismiss();showSettings();});
        menuEntry(body,"heart","角色与语音","形象、音色、识别与角色记忆",()->{settings.dismiss();laboratory.openSettings();});
        gap(body,10);body.addView(text("Pocket Assistant Community 1.0.0",11,MUTED));settings.show();
    }
    private void showMenu() {
        LinearLayout body=dialogBody();AlertDialog menu=dialog("Pocket Assistant",body);
        menuEntry(body,"assistant","AI助理","待办、随手记、对话与文件",()->{menu.dismiss();showAssistant();});
        menuEntry(body,"new","新建对话","选择项目与运行模式",()->{menu.dismiss();newSession();});
        menuEntry(body,"menu","会话记录","继续电脑上保存的对话",()->{menu.dismiss();showSessions();});
        menuEntry(body,"check","切换模型",modelLabel,()->{menu.dismiss();chooseModel(null);});
        menuEntry(body,"file","当前对话","项目目录与运行模式",()->{menu.dismiss();showConversationInfo();});
        menuEntry(body,"proxy","电脑代理","Clash Verge · 远程系统代理开关",()->{menu.dismiss();showProxyControls();});
        menuEntry(body,"theme","个性化",themeLabel(themeMode)+" · "+ThemePalette.colorName(palette.colorTheme),()->{menu.dismiss();showAppearance();});
        menuEntry(body,"info","连接设置",connected?"电脑已连接 · Tailscale":"尚未连接电脑",()->{menu.dismiss();showSettings();});
        gap(body,20);body.addView(text("Pocket Assistant Community 1.0.0\n把想法、事务和陪伴，放在一起。",11,MUTED));menu.show();
    }
    private void showProxyControls() {
        if (!BridgeEndpoint.configured(base,token)) { showSettings(); return; }
        new ProxySheet().show();
    }
    private void showAssistant(){
        assistantConversationVisible=false;prefs.edit().putBoolean("assistantConversationOpen",false).apply();if(assistantConversation!=null)assistantConversation.setVisibility(View.GONE);assistantHome.setVisibility(View.VISIBLE);assistantHub.refresh();selectSection(AppNavigation.ASSISTANT,true);
    }
    private void showAssistantChat(String prompt){
        if(assistantChat==null){
            LinearLayout conversation=column();conversation.setBackgroundColor(BG);LinearLayout header=row();header.setPadding(dp(12),0,dp(60),0);
            IconView back=icon("chevron","返回AI助理",TEXT);back.setRotation(180);header.addView(back,lp(44,48));back.setOnClickListener(v->showAssistant());TextView heading=text("助理对话",17,TEXT);header.addView(heading,new LinearLayout.LayoutParams(0,-2,1));
            IconView inbox=icon("inbox","通讯与邮箱",TEXT);header.addView(inbox,lp(44,48));inbox.setOnClickListener(v->communications.show());conversation.addView(header);
            assistantChat=new AndroidChatView(this,palette,()->showAppearance(),true);assistantChat.onAssistant(this::showAssistant);assistantChat.onCommunicationSource(source->communications.openSource(source));conversation.addView(assistantChat,new LinearLayout.LayoutParams(-1,0,1));assistantConversation=conversation;assistantPage.addView(conversation,new FrameLayout.LayoutParams(-1,-1));
        }
        if(!prompt.isEmpty()&&assistantChat.isBusy()){toast("请先完成当前助理回复，再开始新的请求。");return;}
        assistantConversationVisible=true;prefs.edit().putBoolean("assistantConversationOpen",true).apply();assistantHome.setVisibility(View.GONE);assistantConversation.setVisibility(View.VISIBLE);selectSection(AppNavigation.ASSISTANT,true);
        if(!prompt.isEmpty()){assistantChat.prepareAssistantDraft(prompt);}
    }
    private boolean dispatchAssistantWork(String prompt,AssistantRouter.Decision decision){
        try{boolean phone=decision.kind==AssistantRouter.Kind.ANDROID;if(phone&&androidPage.isBusy()){toast("Android 正在处理另一项工作，指令已保留。");return false;}if(!phone&&(!composer.getText().toString().trim().isEmpty()||!attachments.isEmpty())&&!prompt.equals(composer.getText().toString().trim())){toast("Windows 有未发送的内容，请先处理，新的指令已保留。");return false;}
            JSONObject record=AssistantWork.store(this).create(phone?"android":"windows",prompt);if(phone){if(!androidPage.startWork(record))return false;selectWorkspace(WorkspaceSwitch.ANDROID,true);}else{openAssistantWork(record);}AssistantWork.changed(this);return true;
        }catch(Exception e){toast("工作记录保存失败，指令已保留。");return false;}
    }
    private void openAssistantWork(JSONObject record){
        if("android".equals(record.optString("route"))){if(androidPage.isBusy()){toast("请先等待当前 Android 工作完成。");return;}if(record.optString("chat").isEmpty()){androidPage.startWork(record);}else androidPage.openWorkChat(record.optString("chat"));selectWorkspace(WorkspaceSwitch.ANDROID,true);return;}
        selectWorkspace(WorkspaceSwitch.WINDOWS,true);String id=record.optString("chat");if(!id.isEmpty()){if(!busy)openSession(id);return;}
        String draft=composer.getText().toString().trim();if(!draft.isEmpty()&&!draft.equals(record.optString("prompt"))||!attachments.isEmpty()){toast("Windows 中有未发送的内容，工作指令仍保存在助理首页。");return;}
        composer.setText(record.optString("prompt"));prefs.edit().putString("draft",record.optString("prompt")).putString("assistant_work_draft",record.optString("id")).apply();toast("已准备电脑指令，请选择项目，检查后发送。");
    }
    private void handleAssistantIntent(Intent intent){
        if(intent==null)return;String action=intent.getAction();
        if("com.codexpocket.app.OPEN_ASSISTANT".equals(action)){showAssistant();}
        else if("com.codexpocket.app.OPEN_WORK".equals(action)){try{JSONObject r=AssistantWork.store(this).get(intent.getStringExtra("assistant_work"));if(r!=null)openAssistantWork(r);else showAssistant();}catch(Exception e){showAssistant();}}
        else if("com.codexpocket.app.OPEN_REPLY".equals(action)){String id=intent.getStringExtra("reply_chat");if(id!=null){if(intent.getBooleanExtra("reply_assistant",false)){showAssistantChat("");assistantChat.openWorkChat(id);}else{selectWorkspace(WorkspaceSwitch.ANDROID,true);androidPage.openWorkChat(id);}}}
        intent.setAction(null);
    }
    @Override protected void onNewIntent(Intent intent){super.onNewIntent(intent);setIntent(intent);handleAssistantIntent(intent);}
    private final class ProxySheet {
        final Api client = new Api(base, token);
        final LinearLayout body = dialogBody();
        final TextView status = text("正在读取电脑状态…", 17, MUTED);
        final TextView detail = text("", 12, MUTED);
        final TextView note = text("", 12, MUTED);
        final Switch toggle = new Switch(MainActivity.this);
        final Button refresh = button("刷新状态", false);
        final AlertDialog sheet;
        boolean applying = false, loading = false;
        JSONObject lastStatus;
        final Runnable autoRefresh = new Runnable() {
            @Override public void run() {
                if (isDestroyed() || !sheet.isShowing()) return;
                if (foreground && !loading) request(null);
                ui.postDelayed(this, 8000);
            }
        };
        ProxySheet() {
            body.addView(text("Clash Verge", 13, MUTED)); gap(body, 16);
            LinearLayout card = row(); card.setPadding(dp(16), dp(14), dp(12), dp(14)); card.setBackground(outline(BG, 14));
            LinearLayout labels = column(); labels.addView(text("电脑系统代理", 12, MUTED)); gap(labels, 8);
            status.setTypeface(null, Typeface.BOLD); labels.addView(status);
            card.addView(labels, new LinearLayout.LayoutParams(0, -2, 1));
            toggle.setContentDescription("电脑系统代理开关"); toggle.setEnabled(false); card.addView(toggle, lp(64, 52)); body.addView(card, lp(-1, -2));
            gap(body, 14); body.addView(detail); gap(body, 18);
            body.addView(text("ChatGPT 按需开启代理；DeepSeek、Kimi 通常可关闭。", 13, TEXT)); gap(body, 10);
            body.addView(note); gap(body, 20); body.addView(refresh, lp(-1, 48)); gap(body, 10);
            body.addView(text("修改作用于电脑。电脑端手动改动后，这里会刷新实际状态。", 11, MUTED));
            sheet = dialog("电脑代理", body);
            toggle.setOnCheckedChangeListener((buttonView, checked) -> { if (!applying && !loading) request(checked); });
            refresh.setOnClickListener(v -> request(null));
            sheet.setOnDismissListener(v -> ui.removeCallbacks(autoRefresh));
        }
        void show() { sheet.show(); request(null); ui.postDelayed(autoRefresh, 8000); }
        void render(JSONObject value) {
            lastStatus = value;
            boolean enabled = value.optBoolean("enabled");
            boolean synced = !value.has("synchronized") || value.optBoolean("synchronized");
            boolean vergeEnabled = value.optBoolean("vergeEnabled", enabled);
            applying = true; toggle.setChecked(vergeEnabled); applying = false;
            toggle.setEnabled(value.optBoolean("canControl") && !running && !busy);
            status.setText(!value.optBoolean("available") ? "未就绪" : !synced ? "状态未同步" : enabled ? "已开启 · 已同步" : "已关闭 · 已同步");
            status.setTextColor(!synced ? ERROR : enabled ? palette.success : MUTED);
            String port = value.optString("endpoint");
            detail.setText(getString(R.string.proxy_status_detail,getString(vergeEnabled?R.string.proxy_enabled:R.string.proxy_disabled),getString(enabled?R.string.proxy_enabled:R.string.proxy_disabled),port.isEmpty()?"":getString(R.string.proxy_status_port,port)));
            String message = value.optString("message");
            if (value.optBoolean("tunEnabled")) message += (message.isEmpty() ? "" : "\n") + "电脑的 TUN 配置已开启；此开关仅控制系统代理。";
            if (running || busy) message += (message.isEmpty() ? "" : "\n") + "任务完成或停止后，即可切换代理。";
            if (message.isEmpty()) message = synced ? "通过 Clash Verge 自身切换，电脑与手机保持同步。" : "两端状态不一致，可选择开关目标状态重新同步。";
            note.setText(message); note.setTextColor(value.optBoolean("canControl") ? MUTED : ERROR);
            refresh.setEnabled(true);
        }
        void request(Boolean enabled) {
            if (loading || isDestroyed() || !sheet.isShowing()) return;
            loading = true; toggle.setEnabled(false); refresh.setEnabled(false);
            if (enabled != null) { status.setText(enabled ? "正在开启…" : "正在关闭…"); note.setText("等待电脑确认设置…"); }
            requests.execute(() -> {
                try {
                    JSONObject result = enabled == null ? client.get("/api/proxy") : client.post("/api/proxy", new JSONObject().put("enabled", enabled));
                    ui.post(() -> {
                        loading = false; if (isDestroyed() || !sheet.isShowing()) return;
                        render(result);
                        if (enabled != null) toast(result.optBoolean("enabled") ? "电脑系统代理已开启" : "电脑系统代理已关闭");
                    });
                } catch (Exception e) {
                    final String message = e.getMessage() != null && e.getMessage().contains("接口不存在") ? "电脑端需要更新，请重新启动 Pocket Assistant 服务。" : friendly(e);
                    ui.post(() -> {
                        loading = false; if (isDestroyed() || !sheet.isShowing()) return;
                        applying = true; toggle.setChecked(lastStatus != null && lastStatus.optBoolean("vergeEnabled", lastStatus.optBoolean("enabled"))); applying = false;
                        toggle.setEnabled(false); refresh.setEnabled(true); status.setText("请刷新确认状态"); status.setTextColor(ERROR);
                        note.setText(message); note.setTextColor(ERROR);
                    });
                }
            });
        }
    }
    private void menuEntry(LinearLayout parent,String iconName,String name,String subtitle,Runnable action){
        PocketUi.entry(parent,palette,iconName,name,subtitle,action);
    }
    private void showConversationInfo(){
        LinearLayout body=dialogBody();body.addView(text(threadId.isEmpty()?"还没有开始对话":title.getText().toString(),16,TEXT));gap(body,20);
        body.addView(text("当前模型",11,MUTED));gap(body,5);body.addView(text(modelLabel,15,TEXT));gap(body,18);
        body.addView(text("电脑工作目录",11,MUTED));gap(body,5);TextView cwd=text(threadId.isEmpty()?prefs.getString("cwd","新建对话时选择"):project.getText().toString(),13,TEXT);cwd.setTextIsSelectable(true);body.addView(cwd);gap(body,18);
        body.addView(text("连接",11,MUTED));gap(body,5);body.addView(text(base.isEmpty()?getString(R.string.computer_not_configured):base,13,TEXT));gap(body,8);body.addView(text(connected?"电脑在线，任务将在电脑上执行。":"电脑未连接，可在连接设置中重新连接。",12,MUTED));dialog("对话信息",body).show();
    }
    private void showSettings() {
        if (isFinishing()) return;
        LinearLayout body = dialogBody();
        body.addView(text("手机和电脑先登录同一个 Tailscale 网络。\n电脑端运行 start-server.cmd，填写 pairing.txt 中的配对信息。", 13, MUTED)); gap(body, 20);
        EditText address = field(body, "电脑的 Tailscale 地址", base, false); address.setHint(R.string.computer_address_hint);
        EditText secret = field(body, "配对令牌", token, true);
        Button save = button("保存并连接电脑", true); body.addView(save, lp(-1, 48)); gap(body, 12);
        Button reconnect = button("重新连接", false); body.addView(reconnect, lp(-1, 46)); gap(body, 14);
        android.text.TextWatcher pairingWatcher=new android.text.TextWatcher(){
            @Override public void beforeTextChanged(CharSequence s,int start,int count,int after){}
            @Override public void onTextChanged(CharSequence s,int start,int before,int count){
                String entered=address.getText().toString().trim();
                if(!entered.contains("://"))entered="http://"+entered;
                save.setEnabled(BridgeEndpoint.configured(entered,secret.getText().toString()));
            }
            @Override public void afterTextChanged(android.text.Editable s){}
        };
        address.addTextChangedListener(pairingWatcher);secret.addTextChangedListener(pairingWatcher);
        pairingWatcher.onTextChanged("",0,0,0);reconnect.setEnabled(BridgeEndpoint.configured(base,token));
        body.addView(text("电脑需保持开机并运行服务。手机切到后台后，已发送的任务会继续。", 11, MUTED));
        ScrollView container = new ScrollView(this); container.addView(body); AlertDialog d = dialog("连接你的电脑", container); d.show();
        save.setOnClickListener(v -> {
            try {
                String value = address.getText().toString().trim().replaceAll("/+$", "");
                if(value.isEmpty())throw new Exception(getString(R.string.computer_address_hint));
                if (!value.contains("://")) value = "http://" + value;
                value=BridgeEndpoint.normalize(value);
                String valueToken = secret.getText().toString().trim(); if (valueToken.length() < 32) throw new Exception("请完整粘贴电脑生成的配对令牌（至少 32 个字符）");
                SecretStore.save(this, valueToken); boolean differentComputer = !base.equals(value);
                base = value; token = valueToken; prefs.edit().putString("base", base).apply();
                if (differentComputer) { threadId = ""; prefs.edit().remove("threadId").apply(); resetMessages(); }
                d.dismiss(); connect(); if(laboratorySelected&&laboratory!=null)laboratory.refreshConnection();
            } catch (Exception e) { toast(e.getMessage()); }
        });
        reconnect.setOnClickListener(v -> { if(!BridgeEndpoint.configured(base,token)){toast(getString(R.string.computer_not_configured));return;}d.dismiss();connect();if(laboratorySelected&&laboratory!=null)laboratory.refreshConnection(); });
    }
    private void newSession() {
        if (!connected) { showSettings(); return; }
        LinearLayout body = dialogBody(); body.addView(text("选择电脑上的项目目录，Codex 将在这里工作。", 13, MUTED)); gap(body, 16);
        EditText cwd = field(body, "电脑工作目录", prefs.getString("cwd", ""), false);
        Button models = button(modelLabel + "  ⌄", false); models.setTag(model); body.addView(models, lp(-1, 48)); models.setOnClickListener(v -> chooseModel(models)); gap(body, 14);
        Switch readOnly = new Switch(this); readOnly.setChecked(true); readOnly.setText("只读模式（仅分析代码）"); readOnly.setTextColor(TEXT); readOnly.setTextSize(13); body.addView(readOnly, lp(-1, 48)); gap(body, 14);
        Button create = button("创建会话", true); body.addView(create, lp(-1, 48));
        AlertDialog d = dialog("新建会话", body); d.show();
        create.setOnClickListener(v -> {
            final Api client = api; final String directory = cwd.getText().toString().trim(); final boolean isReadOnly = readOnly.isChecked(); final String selectedModel = (String)models.getTag(); create.setEnabled(false);
            requests.execute(() -> { try {
                JSONObject b = new JSONObject().put("cwd", directory).put("readOnly", isReadOnly); if (!selectedModel.isEmpty()) b.put("model", selectedModel);
                JSONObject result = client.post("/api/threads", b);
                ui.post(() -> { if (isDestroyed()) return; d.dismiss(); prefs.edit().putString("cwd", directory).apply(); selectSnapshot(result); });
            } catch (Exception e) { ui.post(() -> { if (isDestroyed()) return; create.setEnabled(true); toast(friendly(e)); }); } });
        });
    }
    private void chooseModel(Button label) {
        if(!connected||api==null){showSettings();return;} if(busy||running){toast("请等待任务完成或停止后再切换");return;}
        final Api client=api;
        perform(()->client.get("/api/models"),result->{
            JSONArray data=result.optJSONArray("data");if(data==null)return;modelOptions=data;
            LinearLayout body=dialogBody();body.addView(text("选择接下来使用的模型",12,MUTED));gap(body,18);AlertDialog sheet=dialog("切换模型",body);
            for(int i=0;i<data.length();i++){
                JSONObject option=data.getJSONObject(i);String id=option.optString("id"),name=labelFor(id);if(name.isEmpty())continue;
                boolean available=option.optBoolean("available");LinearLayout card=row();card.setPadding(dp(14),dp(16),dp(14),dp(16));card.setBackground(outline(id.equals(label==null?model:String.valueOf(label.getTag()))?palette.selected:PANEL,16));
                ImageView badge=brandImage(id.startsWith("deepseek")?"deepseek":id.startsWith("kimi")?"kimi":"chatgpt",32);badge.setAlpha(available?1f:.4f);LinearLayout.LayoutParams logoParams=lp(40,42);logoParams.rightMargin=dp(12);card.addView(badge,logoParams);
                LinearLayout labels=column();TextView heading=text(name,15,available?TEXT:MUTED);heading.setTypeface(null,Typeface.BOLD);labels.addView(heading);gap(labels,5);labels.addView(text(available?id:"暂不可用 · 检查电脑配置",11,MUTED));card.addView(labels,new LinearLayout.LayoutParams(0,-2,1));
                if(id.equals(label==null?model:String.valueOf(label.getTag())))card.addView(icon("check","当前选择",GREEN),lp(26,30));
                body.addView(card,lp(-1,-2));gap(body,10);
                card.setOnClickListener(v->{if(!available){toast(option.optString("unavailableReason","模型配置不可用"));return;}sheet.dismiss();if(label!=null){label.setTag(id);label.setText(getString(R.string.model_selector_label,name));return;}applyModel(id,name);});
            }
            gap(body,8);body.addView(text("切换模型会保留已有聊天上下文。图片识别能力取决于所选模型。",11,MUTED));sheet.show();
        });
    }
    private void applyModel(String id,String name){
        if(id.equals(model))return;
        if(threadId.isEmpty()){model=id;modelLabel=name;modelChip.setText(getString(R.string.model_selector_label,name));prefs.edit().putString("model",id).apply();return;}
        busy=true;busyLabel="正在切换模型…";updateControls();final Api client=api;final String previous=threadId;
        requests.execute(()->{try{JSONObject result=client.post(path(previous)+"/model",new JSONObject().put("model",id));ui.post(()->{if(isDestroyed())return;busy=false;busyLabel="正在发送…";selectSnapshot(result);toast("已切换到 "+name);});}catch(Exception e){ui.post(()->{if(isDestroyed())return;busy=false;busyLabel="正在发送…";updateControls();showError(friendly(e));});}});
    }
    private void showSessions() {
        if (!connected) { showSettings(); return; }
        LinearLayout body = dialogBody(); Button create = button("＋ 新建会话", true); body.addView(create, lp(-1, 48)); gap(body, 16);
        LinearLayout list = column(); body.addView(list); TextView loading = text("正在读取电脑上的会话…", 13, MUTED); list.addView(loading);
        ScrollView container = new ScrollView(this); container.addView(body); AlertDialog d = dialog("电脑上的会话", container); d.show();
        create.setOnClickListener(v -> { d.dismiss(); newSession(); }); loadSessions(list, d, "", true);
    }
    private void loadSessions(LinearLayout list, AlertDialog dialog, String cursor, boolean first) {
        final Api client = api;
        perform(() -> client.get("/api/threads" + (cursor.isEmpty() ? "" : "?cursor=" + enc(cursor))), result -> {
            if (!dialog.isShowing()) return;
            if (first) list.removeAllViews(); JSONArray data = result.optJSONArray("data");
            if (data == null || data.length() == 0) list.addView(text("还没有会话，创建一个开始吧。", 14, MUTED));
            if (data != null) for (int i = 0; i < data.length(); i++) {
                JSONObject t = data.getJSONObject(i); String id = t.getString("id");
                LinearLayout entry = column(); entry.setPadding(dp(14), dp(14), dp(14), dp(14)); entry.setBackground(outline(id.equals(threadId) ? palette.selected : BG, 12));
                String name = t.optString("name", ""); if (name.isEmpty() || name.equals("null")) name = t.optString("preview", "新会话");
                TextView heading = text(name.isEmpty() ? "新会话" : name, 14, TEXT); heading.setMaxLines(2); heading.setEllipsize(android.text.TextUtils.TruncateAt.END); entry.addView(heading); gap(entry, 5);
                TextView directory = text(t.optString("cwd"), 10, MUTED); directory.setMaxLines(1); directory.setEllipsize(android.text.TextUtils.TruncateAt.MIDDLE); entry.addView(directory);
                list.addView(entry, lp(-1, -2)); gap(list, 8); entry.setOnClickListener(v -> { dialog.dismiss(); openSession(id); });
            }
            String next = result.optString("nextCursor", "");
            if (!next.isEmpty() && !next.equals("null")) { Button more = button("加载更多", false); list.addView(more, lp(-1, 46)); more.setOnClickListener(v -> { list.removeView(more); loadSessions(list, dialog, next, false); }); }
        });
    }
    private void openSession(String id) {
        final Api client = api; busy = true; updateControls(); stopPolling();
        requests.execute(() -> { try { JSONObject result = client.post(path(id) + "/open", new JSONObject()); ui.post(() -> { if (!isDestroyed()) { busy = false; selectSnapshot(result); } }); }
            catch (Exception e) { ui.post(() -> { if (isDestroyed()) return; busy = false; updateControls(); showError(friendly(e)); startPolling(); }); } });
    }
    private void selectSnapshot(JSONObject result) {
        JSONObject t = result.optJSONObject("thread"); if (t == null) { showError("未能打开会话"); return; }
        threadId = t.optString("id"); prefs.edit().putString("threadId", threadId).apply();
        clearError(); resetMessages(); render(result); startPolling();
    }
    private void releaseMathReplies() { for(MathReplyView reply:mathReplies.values()){ViewGroup parent=(ViewGroup)reply.getParent();if(parent!=null)parent.removeView(reply);reply.destroy();}mathReplies.clear(); }
    private void resetMessages() { releaseMathReplies(); messages.removeAllViews(); approvals.removeAllViews(); itemTexts.clear(); itemAttachmentHashes.clear(); approvalHash = ""; renderedThread = ""; running = false; updateControls(); }
    private void sendMessage() {
        if(api==null||busy||running||!connected)return;
        final String prompt=composer.getText().toString().trim();if(prompt.isEmpty()&&attachments.isEmpty())return;
        if(attachments.stream().anyMatch(a->a.preparing)){toast("附件正在准备，请稍候");return;}
        if(threadId.isEmpty()){newSession();toast("先选择项目，消息和附件会保留");return;}
        final String id=threadId;final Api client=api;final List<Attachment> selected=new ArrayList<>(attachments);
        String fingerprint=prompt+"|"+selected.stream().map(a->a.localId).collect(java.util.stream.Collectors.joining(","));
        if(!fingerprint.equals(pendingFingerprint)||!id.equals(pendingThread)||pendingId.isEmpty()){pendingId=UUID.randomUUID().toString();pendingFingerprint=fingerprint;pendingText=prompt;pendingThread=id;}
        final String requestId=pendingId;
        final String assistantWorkId=prefs.getString("assistant_work_draft","");
        try{JSONObject handoff=AssistantWork.store(this).get(assistantWorkId);if(handoff!=null&&prompt.equals(handoff.optString("prompt")))AssistantWork.update(this,assistantWorkId,"prepared",id,"正在发送到电脑；发送结果不确定时请在此会话重试");}catch(Exception ignored){}
        prefs.edit().putString("pendingId",pendingId).putString("pendingText",pendingText).putString("pendingThread",pendingThread).putString("pendingFingerprint",pendingFingerprint).putString("draft",prompt).apply();
        busy=true;busyLabel=selected.isEmpty()?"正在发送…":"正在上传附件…";clearError();updateControls();
        requests.execute(()->{try{
            JSONArray ids=new JSONArray();int index=0;
            for(Attachment a:selected){
                final int position=++index;ui.post(()->{if(!isDestroyed()){busyLabel="正在上传附件 "+position+" / "+selected.size();updateControls();}});
                if(a.uploadId.isEmpty()||!a.uploadBase.equals(client.base)){JSONObject uploaded=client.upload(a);a.uploadId=uploaded.getString("id");a.uploadBase=client.base;ui.post(()->{if(!isDestroyed())persistAttachments();});}
                ids.put(a.uploadId);
            }
            JSONObject accepted=client.post(path(id)+"/messages",new JSONObject().put("text",prompt).put("requestId",requestId).put("attachments",ids));
            try{JSONObject handoff=AssistantWork.store(this).get(assistantWorkId);if(handoff!=null&&prompt.equals(handoff.optString("prompt"))){AssistantWork.store(this).bindTurn(assistantWorkId,accepted.getString("turnId"));AssistantWork.update(this,assistantWorkId,"running",id,"电脑正在处理");}}catch(Exception ignored){}
            ui.post(()->{if(isDestroyed())return;busy=false;busyLabel="正在发送…";
                if(threadId.equals(id)&&composer.getText().toString().trim().equals(prompt))composer.setText("");
                for(Attachment a:selected){attachments.remove(a);if(a.file!=null)a.file.delete();}
                persistAttachments();renderAttachmentStrip();pendingId="";pendingText="";pendingThread="";pendingFingerprint="";
                prefs.edit().remove("assistant_work_draft").remove("pendingId").remove("pendingText").remove("pendingThread").remove("pendingFingerprint").putString("draft",composer.getText().toString()).apply();updateControls();
            });
        }catch(Exception e){ui.post(()->{if(isDestroyed())return;busy=false;busyLabel="正在发送…";persistAttachments();renderAttachmentStrip();updateControls();showError(friendly(e)+"\n消息和附件已保留，可重试发送。");});}});
    }

    private void render(JSONObject snapshot) {
        AssistantWork.remote(getApplicationContext(),snapshot);
        JSONObject t = snapshot.optJSONObject("thread");
        if (t == null) return;
        String id = t.optString("id"); if (!id.equals(threadId)) return;
        if (!renderedThread.equals(id)) { resetMessages(); renderedThread = id; }
        boolean nearBottom = scroll.getChildAt(0) == null || scroll.getChildAt(0).getBottom() - scroll.getHeight() - scroll.getScrollY() < dp(160);
        running = !t.isNull("activeTurn") && !t.optString("activeTurn").isEmpty();
        String name = t.optString("title", "新会话"); title.setText(name.isEmpty() ? "新会话" : name);
        String activeModel=t.optString("model");String activeLabel=labelFor(activeModel);if(!activeLabel.isEmpty()){model=activeModel;modelLabel=activeLabel;modelChip.setText(getString(R.string.model_selector_label,modelLabel));prefs.edit().putString("model",model).apply();}
        project.setText(getString(R.string.workspace_directory,t.optString("cwd"),t.optBoolean("readOnly")?getString(R.string.workspace_read_only):""));
        JSONArray items = t.optJSONArray("items"); welcome.setVisibility(items == null || items.length() == 0 ? View.VISIBLE : View.GONE);
        Set<String> ids = new HashSet<>();
        if (items != null) for (int i = 0; i < items.length(); i++) {
            JSONObject item = items.optJSONObject(i); if (item == null) continue;
            String itemId = item.optString("id"); ids.add(itemId); TextView body = itemTexts.get(itemId);
            if (body == null) { body = createItem(item); itemTexts.put(itemId, body); }
            String value = item.optString("text"); String type = item.optString("type");
            if (type.equals("commandExecution")) {
                value = "$ " + value + "\n\n" + item.optString("output");
                if (!item.isNull("exitCode")) value += "\n[退出码 " + item.optInt("exitCode") + "]";
                else if (item.optString("status").equals("inProgress")) value += "\n[执行中…]";
            } else if (type.equals("mcpToolCall")) value += "\n" + item.optString("output");
            MathReplyView rich=mathReplies.get(itemId);
            if(type.equals("agentMessage")&&rich==null&&MathReplyView.needsMath(value)){
                rich=new MathReplyView(this,body,palette.dark,()->{boolean bottom=scroll.getChildAt(0)==null||scroll.getChildAt(0).getBottom()-scroll.getHeight()-scroll.getScrollY()<dp(160);if(bottom)scroll.post(()->scroll.fullScroll(View.FOCUS_DOWN));});
                ((LinearLayout)body.getParent()).addView(rich,lp(-1,1));mathReplies.put(itemId,rich);rich.setSource(value);
            }
            if (!value.equals(body.getTag())) { body.setTag(value); if(rich!=null)rich.setSource(value);else body.setText(type.equals("agentMessage") ? format(value) : value); }
            body.setVisibility(value.isEmpty()||(rich!=null&&rich.hasRendered())?View.GONE:View.VISIBLE);
            JSONArray files=new JSONArray();
            JSONArray uploaded=item.optJSONArray("attachments"),generated=item.optJSONArray("artifacts");
            if(uploaded!=null)for(int f=0;f<uploaded.length();f++)files.put(uploaded.optJSONObject(f));
            if(generated!=null)for(int f=0;f<generated.length();f++)files.put(generated.optJSONObject(f));
            String hash=files.toString();
            if(!hash.equals(itemAttachmentHashes.get(itemId))){
                itemAttachmentHashes.put(itemId,hash);LinearLayout card=(LinearLayout)body.getParent();
                if(card.getTag() instanceof View)card.removeView((View)card.getTag());
                if(files.length()>0){View fileList=remoteFiles.cards(files,api);card.addView(fileList);card.setTag(fileList);}else card.setTag(null);
            }
        }
        Iterator<Map.Entry<String, TextView>> iterator = itemTexts.entrySet().iterator();
        while (iterator.hasNext()) { Map.Entry<String, TextView> entry = iterator.next(); if (!ids.contains(entry.getKey())) { MathReplyView rich=mathReplies.remove(entry.getKey());if(rich!=null){((ViewGroup)rich.getParent()).removeView(rich);rich.destroy();}messages.removeView((View) entry.getValue().getParent());iterator.remove(); } }
        JSONArray pending = snapshot.optJSONArray("approvals");
        String hash = pending == null ? "" : pending.toString();
        if (!hash.equals(approvalHash)) { approvalHash = hash; renderApprovals(pending); }
        if (!t.optString("error").isEmpty()) showError(t.optString("error"));
        updateControls();
        if (nearBottom) scroll.post(() -> scroll.fullScroll(View.FOCUS_DOWN));
    }
    private TextView createItem(JSONObject item) {
        String type = item.optString("type"); boolean user = type.equals("userMessage"); boolean code = type.equals("commandExecution") || type.equals("fileChange");
        LinearLayout card = column(); card.setPadding(dp(14), dp(13), dp(14), dp(14)); card.setBackground(user?background(palette.userBubble,20):code?outline(PANEL,14):background(Color.TRANSPARENT,0));
        String label;
        switch (type) {
            case "userMessage": label = "你"; break;
            case "agentMessage": label = "⌘  CODEX"; break;
            case "commandExecution": label = "终端执行   ⌄"; break;
            case "fileChange": label = "文件改动   ⌄"; break;
            case "webSearch": label = "⌕  搜索"; break;
            case "plan": label = "☷  执行计划"; break;
            default: label = "◇  工具调用";
        }
        TextView heading = text(label, 10, user ? MUTED : GREEN); heading.setTypeface(null, Typeface.BOLD); card.addView(heading); gap(card, 8);
        TextView body = text("", code ? 12 : 15, TEXT); body.setTextIsSelectable(true); if (code) { body.setTypeface(Typeface.MONOSPACE); body.setMaxLines(2); heading.setOnClickListener(v -> body.setMaxLines(body.getMaxLines() == 2 ? Integer.MAX_VALUE : 2)); }
        if(item.optString("type").equals("agentMessage"))heading.setOnLongClickListener(v->{ClipboardManager clipboard=(ClipboardManager)getSystemService(CLIPBOARD_SERVICE);clipboard.setPrimaryClip(ClipData.newPlainText("Codex 回复",String.valueOf(body.getTag())));toast("已复制回复原文");return true;});
        card.addView(body, lp(-1, -2));
        LinearLayout.LayoutParams params = lp(-1, -2); params.bottomMargin = dp(12); if (user) params.leftMargin = dp(24); messages.addView(card, params); return body;
    }
    private CharSequence format(String value) {
        SpannableString s = new SpannableString(value);
        Matcher code = Pattern.compile("```[\\s\\S]*?```|`[^`\\n]+`").matcher(value);
        while (code.find()) { s.setSpan(new TypefaceSpan("monospace"), code.start(), code.end(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE); s.setSpan(new BackgroundColorSpan(palette.code), code.start(), code.end(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE); }
        Matcher bold = Pattern.compile("\\*\\*[^*]+\\*\\*|(?m)^#{1,4} .+$").matcher(value);
        while (bold.find()) s.setSpan(new StyleSpan(Typeface.BOLD), bold.start(), bold.end(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        return s;
    }
    private void renderApprovals(JSONArray pending) {
        approvals.removeAllViews(); if (pending == null) return;
        for (int i = 0; i < pending.length(); i++) {
            JSONObject a = pending.optJSONObject(i); if (a == null) continue;
            String id = a.optString("id"), method = a.optString("method"); JSONObject p = a.optJSONObject("params"); if (p == null) continue;
            LinearLayout card = column(); card.setPadding(dp(16), dp(16), dp(16), dp(16)); card.setBackground(outline(palette.warning, 16));
            TextView label = text(method.equals("item/tool/requestUserInput") ? "需要你的回答" : "需要你的确认", 16, palette.warningText); label.setTypeface(null, Typeface.BOLD); card.addView(label); gap(card, 12);
            if (method.equals("item/tool/requestUserInput")) {
                JSONArray questions = p.optJSONArray("questions"); Map<String, EditText> answers = new LinkedHashMap<>();
                if (questions != null) for (int q = 0; q < questions.length(); q++) {
                    JSONObject question = questions.optJSONObject(q); if (question == null) continue;
                    card.addView(text(question.optString("question"), 14, TEXT)); gap(card, 8);
                    EditText answer = field(card, question.optString("header", "回答"), "", question.optBoolean("isSecret")); answers.put(question.optString("id"), answer);
                    JSONArray options = question.optJSONArray("options");
                    if (options != null) for (int o = 0; o < options.length(); o++) {
                        JSONObject option = options.optJSONObject(o); if (option == null) continue;
                        String optionLabel = option.optString("label"); Button pick = button(optionLabel, false); card.addView(pick, lp(-1, 44)); gap(card, 5);
                        if (!option.optString("description").isEmpty()) { card.addView(text(option.optString("description"), 11, MUTED)); gap(card, 6); }
                        pick.setOnClickListener(v -> answer.setText(optionLabel));
                    }
                }
                Button submit = button("提交回答", true); card.addView(submit, lp(-1, 48));
                submit.setOnClickListener(v -> {
                    try {
                        JSONObject values = new JSONObject(); for (Map.Entry<String, EditText> entry : answers.entrySet()) { String answer = entry.getValue().getText().toString().trim(); if (answer.isEmpty()) { toast("请回答所有问题"); return; } values.put(entry.getKey(), new JSONArray().put(answer)); }
                        sendApproval(id, new JSONObject().put("answers", values), submit);
                    } catch (Exception e) { showError(e.getMessage()); }
                });
            } else {
                String reason = p.optString("reason", ""); if (!reason.isEmpty() && !reason.equals("null")) { card.addView(text(reason, 13, TEXT)); gap(card, 10); }
                String command = p.optString("command", ""); if (!command.isEmpty() && !command.equals("null")) { TextView code = text(command, 12, TEXT); code.setTypeface(Typeface.MONOSPACE); code.setTextIsSelectable(true); card.addView(code); gap(card, 10); }
                String cwd = p.optString("cwd", ""); if (!cwd.isEmpty() && !cwd.equals("null")) card.addView(text("目录：" + cwd, 11, MUTED));
                if (method.equals("item/fileChange/requestApproval")) card.addView(text("文件修改详情请查看上方的“文件改动”卡片。", 12, TEXT));
                gap(card, 14); LinearLayout actions = row(); Button deny = button("拒绝", false), allow = button("允许本次", true);
                actions.addView(deny, new LinearLayout.LayoutParams(0, dp(48), 1)); View gap = new View(this); actions.addView(gap, lp(10, 1)); actions.addView(allow, new LinearLayout.LayoutParams(0, dp(48), 1)); card.addView(actions);
                deny.setOnClickListener(v -> decision(id, "decline", deny)); allow.setOnClickListener(v -> decision(id, "accept", allow));
            }
            LinearLayout.LayoutParams params = lp(-1, -2); params.bottomMargin = dp(14); approvals.addView(card, params);
        }
    }
    private void showAttachmentMenu(){
        if(busy){toast("正在发送，请稍候");return;}if(attachments.size()>=5){toast("每条消息最多添加 5 个附件");return;}
        LinearLayout body=dialogBody();AlertDialog sheet=dialog("添加附件",body);
        menuEntry(body,"image","图片","从相册或文件中选择，可多选",()->{sheet.dismiss();pickAttachments(true);});
        menuEntry(body,"file","文件","文档、PDF、代码或其他文件",()->{sheet.dismiss();pickAttachments(false);});
        gap(body,16);body.addView(text("每条消息最多 5 个附件，单个不超过 25 MB。",11,MUTED));sheet.show();
    }
    private void pickAttachments(boolean image){
        Intent intent=new Intent(Intent.ACTION_OPEN_DOCUMENT);intent.addCategory(Intent.CATEGORY_OPENABLE);intent.setType(image?"image/*":"*/*");intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE,true);
        if(image)intent.putExtra(Intent.EXTRA_MIME_TYPES,new String[]{"image/jpeg","image/png","image/webp","image/gif"});
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        try{startActivityForResult(intent,image?7101:7102);}catch(Exception e){showError("无法打开文件选择器："+friendly(e));}
    }
    @Override protected void onActivityResult(int requestCode,int resultCode,Intent data){
        super.onActivityResult(requestCode,resultCode,data);if(assistantChat!=null&&assistantChat.onActivityResult(requestCode,resultCode,data))return;if(androidPage!=null&&androidPage.onActivityResult(requestCode,resultCode,data))return;if(remoteFiles!=null&&remoteFiles.onActivityResult(requestCode,resultCode,data))return;if((requestCode!=7101&&requestCode!=7102)||resultCode!=RESULT_OK||data==null)return;
        List<Uri> uris=new ArrayList<>();if(data.getClipData()!=null){for(int i=0;i<data.getClipData().getItemCount();i++)uris.add(data.getClipData().getItemAt(i).getUri());}else if(data.getData()!=null)uris.add(data.getData());
        if(uris.size()>5-attachments.size())toast("最多保留 5 个附件，超出部分未添加");
        for(Uri uri:uris){if(attachments.size()>=5)break;String name="附件";
            try(Cursor c=getContentResolver().query(uri,new String[]{OpenableColumns.DISPLAY_NAME,OpenableColumns.SIZE},null,null,null)){if(c!=null&&c.moveToFirst()){int n=c.getColumnIndex(OpenableColumns.DISPLAY_NAME);if(n>=0&&!c.isNull(n))name=c.getString(n);int sz=c.getColumnIndex(OpenableColumns.SIZE);if(sz>=0&&!c.isNull(sz)&&c.getLong(sz)>Attachment.MAX){toast(name+" 超过 25 MB，未添加");continue;}}}catch(Exception ignored){}
            final Attachment a=new Attachment(name,requestCode==7101?"image":"file");attachments.add(a);renderAttachmentStrip();updateControls();
            requests.execute(()->{try{a.copy(getApplicationContext(),uri);ui.post(()->{if(isDestroyed())return;persistAttachments();renderAttachmentStrip();updateControls();});}catch(Exception e){ui.post(()->{if(isDestroyed())return;attachments.remove(a);renderAttachmentStrip();updateControls();showError("无法添加 "+a.name+"："+friendly(e));});}});
        }
    }
    @Override public void onRequestPermissionsResult(int requestCode,String[] permissions,int[] grants){super.onRequestPermissionsResult(requestCode,permissions,grants);if(requestCode==AssistantNotifications.PERMISSION){if(grants.length>0&&grants[0]==android.content.pm.PackageManager.PERMISSION_GRANTED){AssistantNotifications.important(this);AssistantBackgroundService.start(this);}else toast("通知未获允许，仍可在助理首页查看重要事项。");}if(assistantHub!=null)assistantHub.onPermissionsResult(requestCode,grants);if(communications!=null)communications.onPermissionsResult(requestCode,grants);if(laboratory!=null)laboratory.onPermissionsResult(requestCode,grants);if(remoteFiles!=null)remoteFiles.onPermissionsResult(requestCode,grants);}
    private void persistAttachments(){try{JSONArray values=new JSONArray();for(Attachment a:attachments)if(!a.preparing)values.put(a.json());prefs.edit().putString("attachments",values.toString()).apply();}catch(Exception ignored){}}
    private void restoreAttachments(){try{JSONArray values=new JSONArray(prefs.getString("attachments","[]"));for(int i=0;i<values.length()&&attachments.size()<5;i++){try{attachments.add(Attachment.restore(values.getJSONObject(i),this));}catch(Exception ignored){}}}catch(Exception ignored){}renderAttachmentStrip();}
    private Bitmap thumbnail(File file,int limit){
        BitmapFactory.Options options=new BitmapFactory.Options();options.inJustDecodeBounds=true;BitmapFactory.decodeFile(file.getAbsolutePath(),options);options.inSampleSize=1;
        while(options.outWidth/options.inSampleSize>limit||options.outHeight/options.inSampleSize>limit)options.inSampleSize*=2;
        options.inJustDecodeBounds=false;return BitmapFactory.decodeFile(file.getAbsolutePath(),options);
    }
    private void renderAttachmentStrip(){
        if(attachmentStrip==null)return;attachmentStrip.removeAllViews();attachmentScroll.setVisibility(attachments.isEmpty()?View.GONE:View.VISIBLE);
        for(Attachment a:attachments){LinearLayout card=row();card.setPadding(dp(7),dp(7),dp(2),dp(7));card.setBackground(background(palette.attachment,14));
            if(a.kind.equals("image")&&!a.preparing&&a.file!=null){ImageView preview=new ImageView(this);RoundedDialogBuilder.clip(preview,10);preview.setImageBitmap(thumbnail(a.file,100));preview.setScaleType(ImageView.ScaleType.CENTER_CROP);card.addView(preview,lp(42,42));}else card.addView(icon(a.kind.equals("image")?"image":"file","附件",GREEN),lp(38,42));
            LinearLayout labels=column();labels.setPadding(dp(8),0,dp(4),0);TextView name=text(a.name,12,TEXT);name.setMaxLines(1);name.setEllipsize(android.text.TextUtils.TruncateAt.MIDDLE);labels.addView(name,lp(115,-2));labels.addView(text(a.preparing?"准备中…":a.sizeText(),10,MUTED));card.addView(labels);
            IconView remove=icon("close","移除 "+a.name,MUTED);card.addView(remove,lp(34,42));remove.setOnClickListener(v->{if(busy||a.preparing)return;attachments.remove(a);if(a.file!=null)a.file.delete();persistAttachments();renderAttachmentStrip();updateControls();});
            card.setOnClickListener(v->{if(a.preparing)return;if(a.kind.equals("image"))showImage(a.file,a.name);else showFileInfo(a.name,a.size);});
            LinearLayout.LayoutParams params=lp(-2,-2);params.rightMargin=dp(8);attachmentStrip.addView(card,params);
        }
    }
    private void showFileInfo(String name,long size){LinearLayout body=dialogBody();TextView filename=text(name,16,TEXT);filename.setTextIsSelectable(true);body.addView(filename);gap(body,12);body.addView(text(String.format(Locale.ROOT,"%.1f KB",size/1024.0),12,MUTED));gap(body,12);body.addView(text("文件已作为附件发送到电脑，可在对话中让模型读取与分析。",13,MUTED));dialog("文件附件",body).show();}
    private void showImage(File file,String name){
        if(file==null||!file.isFile()){toast("图片缓存不存在");return;}LinearLayout body=dialogBody();ImageView image=new ImageView(this);RoundedDialogBuilder.clip(image,18);image.setAdjustViewBounds(true);image.setScaleType(ImageView.ScaleType.FIT_CENTER);Bitmap bitmap=thumbnail(file,1600);if(bitmap==null){toast("此图片暂时无法预览");return;}image.setImageBitmap(bitmap);body.addView(image,lp(-1,360));gap(body,12);body.addView(text(name,12,MUTED));dialog("图片预览",body).show();
    }
    private void previewRemote(JSONObject metadata){
        if(!metadata.optString("kind").equals("image")){showFileInfo(metadata.optString("name"),metadata.optLong("size"));return;}
        if(api==null||!connected){toast("连接电脑后可查看图片");return;}final Api client=api;final String id=metadata.optString("id");if(!id.matches("[0-9a-f-]{36}"))return;
        final File cached=new File(getCacheDir(),"image-"+id);if(cached.isFile()){showImage(cached,metadata.optString("name"));return;}toast("正在读取图片…");
        requests.execute(()->{try{client.download(id,cached);ui.post(()->{if(!isDestroyed())showImage(cached,metadata.optString("name"));});}catch(Exception e){ui.post(()->{if(!isDestroyed())showError(friendly(e));});}});
    }
    private void decision(String id, String value, Button button) { try { sendApproval(id, new JSONObject().put("decision", value), button); } catch (Exception e) { showError(e.getMessage()); } }
    private void sendApproval(String id, JSONObject value, Button button) {
        final Api client = api; button.setEnabled(false);
        requests.execute(() -> { try { client.post("/api/approvals/" + enc(id), value); }
            catch (Exception e) { ui.post(() -> { if (isDestroyed()) return; button.setEnabled(true); showError(friendly(e)); }); } });
    }
}
