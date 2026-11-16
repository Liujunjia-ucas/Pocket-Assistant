package com.codexpocket.app;

import android.Manifest;
import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.*;
import android.provider.*;
import android.text.InputType;
import android.widget.*;
import org.json.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;

/** Read-only source browser and generic account/automation settings. */
final class CommunicationCenter {
    static final int SMS_PERMISSION=7301,CALL_PERMISSION=7302;
    private final Activity activity;private final ThemePalette p;
    private final Handler ui=new Handler(Looper.getMainLooper());private final ExecutorService io=Executors.newSingleThreadExecutor();
    private final List<AlertDialog> dialogs=new ArrayList<>();private volatile boolean closed;private volatile ImapMailbox currentMailbox;
    private static final class ReadTask{volatile boolean cancelled;}
    private volatile ReadTask activeTask;
    private final SharedPreferences prefs;
    CommunicationCenter(Activity a,ThemePalette p){activity=a;this.p=p;prefs=a.getSharedPreferences("assistant_connections",Activity.MODE_PRIVATE);}
    void openSource(String source){switch(source){case "sms":sms();break;case "calls":calls();break;case "email":case "ucas":case "qq":accounts();break;case "notifications":try{JSONArray saved=AssistantNotificationStore.list(activity);List<JSONObject> rows=new ArrayList<>();for(int i=0;i<saved.length();i++)rows.add(saved.getJSONObject(i));records("通讯通知",rows,null);}catch(Exception e){toast("通知缓存暂时无法读取");}break;default:show();}}
    private int dp(int n){return PocketUi.dp(activity,n);}private LinearLayout column(){LinearLayout b=PocketUi.column(activity);b.setPadding(dp(20),dp(8),dp(20),dp(18));return b;}
    private TextView text(String s,int size,int color){return PocketUi.text(activity,s,size,color);}private void gap(LinearLayout b,int n){PocketUi.gap(b,n);}
    private Button button(String s,boolean primary){Button b=new Button(activity);b.setText(s);PocketUi.button(b,p,primary);b.setMinHeight(dp(46));return b;}
    private AlertDialog show(AlertDialog d){dialogs.add(d);d.setOnDismissListener(v->dialogs.remove(d));d.show();return d;}
    private AlertDialog sheet(String title,LinearLayout body){ScrollView scroll=new ScrollView(activity);scroll.setVerticalScrollBarEnabled(false);scroll.addView(body);return show(new RoundedDialogBuilder(activity).setTitle(title).setView(scroll).setNegativeButton("返回",null).create());}
    private void toast(String s){Toast.makeText(activity,s,Toast.LENGTH_SHORT).show();}
    void show(){
        LinearLayout body=column();PocketUi.entry(body,p,"sms","短信",permission(Manifest.permission.READ_SMS)?"已授权，自动整理最近短信":"授权读取或使用短信通知",this::sms);
        PocketUi.entry(body,p,"phone","电话",permission(Manifest.permission.READ_CALL_LOG)?"最近通话与未接来电":"授权通话记录、拨号与回访",this::calls);
        PocketUi.entry(body,p,"mail","邮箱账户","添加和管理任意支持 IMAP 的邮箱",this::accounts);
        PocketUi.section(body,p,"接入与控制");PocketUi.entry(body,p,"inbox","通知接入",notificationEnabled()?"通知访问已开启":"短信、邮件与未接来电的新通知",this::notifications);
        PocketUi.entry(body,p,"settings","手机授权","短信、通话记录和通知状态",this::permissions);
        body.addView(text("权限不会自动启用同步；请在自动智能管理中自行开启。",12,p.muted));sheet("通讯与邮箱",body);
    }
    void automation(){
        LinearLayout body=column();Switch sync=new Switch(activity);sync.setText(R.string.auto_sync_management);sync.setTextColor(p.text);sync.setChecked(AssistantSync.enabled(activity));body.addView(sync);gap(body,14);Switch ai=new Switch(activity);ai.setText(R.string.allow_cloud_communication);ai.setTextColor(p.text);ai.setChecked(prefs.getBoolean("ai_triage",false));body.addView(ai);gap(body,14);
        body.addView(text("授权后自动汇总重要信息并生成明确的待办。AI 整理使用手机已配置的模型；验证码和营销通知会过滤，未接来电与明确取件通知可在本机识别。",12,p.muted));gap(body,16);
        sync.setOnCheckedChangeListener((v,on)->{prefs.edit().putBoolean("auto_sync",on).apply();AssistantSync.schedule(activity);if(on)AssistantSync.request(activity,true);else AssistantSync.cancel();});ai.setOnCheckedChangeListener((v,on)->{if(on){new RoundedDialogBuilder(activity).setTitle("允许上传通讯内容？").setMessage("候选短信、通知或邮件的发送者、标题和正文将发送给你配置的模型服务商，可能包含你或他人的个人信息。过滤规则不能保证去除全部敏感信息。关闭后仅使用本机规则。服务商可能保存请求并产生费用。").setPositiveButton("允许",(d,w)->{prefs.edit().putBoolean("ai_triage",true).apply();AssistantSync.request(activity,true);}).setNegativeButton("取消",(d,w)->ai.setChecked(false)).setOnCancelListener(d->ai.setChecked(false)).show();}else{prefs.edit().putBoolean("ai_triage",false).apply();AssistantSync.cancel();}});
        PocketUi.entry(body,p,"history","立即同步","刷新重要信息与待办",()->{AssistantSync.request(activity,true);toast("正在自动整理");});PocketUi.entry(body,p,"inbox","同步状态","查看各信息来源的连接情况",this::syncStatus);
        PocketUi.entry(body,p,"settings","通知与后台运行","应用提醒、常驻后台与电池设置",()->AssistantNotificationSettings.show(activity,p));
        body.addView(text("应用打开时监听已授权数据的变化；新通知会触发整理，后台定期任务约每 15 分钟检查一次。系统休眠、省电或强制停止会延迟后台同步，重新打开时会补充更新。",12,p.muted));sheet("自动智能管理",body);
    }
    private void syncStatus(){LinearLayout body=column();try{JSONObject sources=SmartRepository.snapshot(activity).getJSONObject("sources");for(Iterator<String> keys=sources.keys();keys.hasNext();){String key=keys.next();JSONObject status=sources.getJSONObject(key);String name=key.equals("engine")?"自动管理":key.equals("sms")?"短信":key.equals("calls")?"电话":key.equals("ai")?"AI 整理":key.startsWith("mail:")?accountName(key.substring(5)):key.equals("mail")?"邮箱":"通知";body.addView(text(name,14,p.text));body.addView(text(status.optString("status")+" · "+date(status.optLong("at")),12,p.muted));gap(body,16);}if(sources.length()==0)body.addView(text("尚未同步，连接来源后会自动整理。",13,p.muted));}catch(Exception e){body.addView(text("同步状态暂不可用",13,p.error));}sheet("同步状态",body);}
    private boolean permission(String name){return activity.checkSelfPermission(name)==PackageManager.PERMISSION_GRANTED;}
    private boolean notificationEnabled(){String enabled=Settings.Secure.getString(activity.getContentResolver(),"enabled_notification_listeners");ComponentName target=new ComponentName(activity,AssistantNotificationListener.class);if(enabled!=null)for(String entry:enabled.split(":"))if(target.equals(ComponentName.unflattenFromString(entry)))return true;return false;}
    private void request(String name,int code){activity.requestPermissions(new String[]{name},code);}
    void onPermissionsResult(int code,int[] result){if(code!=SMS_PERMISSION&&code!=CALL_PERMISSION)return;if(result.length>0&&result[0]==PackageManager.PERMISSION_GRANTED){AssistantSync.request(activity,true);toast("已授权，正在读取");if(code==CALL_PERMISSION)ui.postDelayed(()->{if(!closed&&!activity.isFinishing())calls();},250);}else denied(code==SMS_PERMISSION);}
    private void denied(boolean sms){
        LinearLayout body=column();body.addView(text(sms?"系统尚未允许读取短信。HyperOS 可能限制普通应用获得此权限，可使用已授权的短信通知，或在手机应用权限中检查。":"尚未获得读取通话记录权限。短信权限与通话记录权限分别授予，请在系统应用权限及 HyperOS 安全中心中单独检查通话记录。",14,p.text));gap(body,16);
        PocketUi.entry(body,p,"settings","应用权限设置","由手机系统管理授权",this::appSettings);PocketUi.entry(body,p,"inbox",sms?"短信通知":"未接来电通知","开启后可整理新收到的通知",this::notifications);sheet(sms?"短信授权":"通话记录授权",body);
    }
    private void permissions(){
        LinearLayout body=column();PocketUi.entry(body,p,"sms","短信",permission(Manifest.permission.READ_SMS)?"已授权":"未授权",()->{if(permission(Manifest.permission.READ_SMS))sms();else request(Manifest.permission.READ_SMS,SMS_PERMISSION);});
        PocketUi.entry(body,p,"phone","通话记录",permission(Manifest.permission.READ_CALL_LOG)?"已授权":"未授权",()->{if(permission(Manifest.permission.READ_CALL_LOG))calls();else request(Manifest.permission.READ_CALL_LOG,CALL_PERMISSION);});
        PocketUi.entry(body,p,"inbox","通知访问",notificationEnabled()?"已开启":"未开启",this::notifications);PocketUi.entry(body,p,"settings","系统应用设置","撤销或修改手机权限",this::appSettings);sheet("手机授权",body);
    }
    private void appSettings(){try{activity.startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,Uri.fromParts("package",activity.getPackageName(),null)));}catch(Exception e){toast("请在系统设置中打开 Pocket Assistant 的应用权限");}}
    private interface Work<T>{T run()throws Exception;}
    private <T> void load(String title,Work<T> work,Consumer<T> success){
        AlertDialog loading=new RoundedDialogBuilder(activity).setTitle(title).setMessage("正在读取…").setNegativeButton("取消",null).create();ReadTask task=new ReadTask();show(loading);loading.setOnDismissListener(d->{dialogs.remove(loading);task.cancelled=true;ImapMailbox connection=currentMailbox;if(activeTask==task&&connection!=null)connection.cancel();});
        io.execute(()->{if(closed||task.cancelled)return;activeTask=task;try{T result=work.run();ui.post(()->{if(closed||task.cancelled)return;loading.setOnDismissListener(d->dialogs.remove(loading));loading.dismiss();success.accept(result);});}
            catch(Exception e){ui.post(()->{if(closed||task.cancelled)return;loading.setOnDismissListener(d->dialogs.remove(loading));loading.dismiss();if(e instanceof CallHistory.AccessException){try{SmartRepository.status(activity,"calls",e.getMessage());}catch(Exception ignored){}callAccess((CallHistory.AccessException)e);return;}String message=e instanceof SecurityException?"手机没有允许读取这类资料，请检查系统授权。":e instanceof javax.net.ssl.SSLException?"邮箱 TLS 校验失败，请核对服务器、手机时间和网络。":e instanceof java.net.SocketTimeoutException||e instanceof java.net.ConnectException||e instanceof java.net.UnknownHostException?"无法连接邮箱，请核对 IMAP 服务器、端口与网络。":"读取失败，请检查授权或邮箱配置。原始短信、电话与邮件没有被修改。";show(new RoundedDialogBuilder(activity).setTitle(title).setMessage(message).setPositiveButton("知道了",null).create());});}finally{if(activeTask==task)activeTask=null;}});
    }
    private void sms(){
        if(!permission(Manifest.permission.READ_SMS)){request(Manifest.permission.READ_SMS,SMS_PERMISSION);return;}
        load("最近短信",()->{
            List<JSONObject> rows=new ArrayList<>();try(Cursor c=activity.getContentResolver().query(Telephony.Sms.CONTENT_URI,new String[]{"_id","address","body","date","type"},null,null,"date DESC")){
                while(c!=null&&c.moveToNext()&&rows.size()<50){String number=value(c,"address");int type=c.getInt(c.getColumnIndexOrThrow("type"));rows.add(new JSONObject().put("kind",type==2?"已发送短信":"收到短信").put("source",number).put("phone",number).put("title",number).put("body",value(c,"body")).put("date",date(c.getLong(c.getColumnIndexOrThrow("date")))));}
            }return rows;
        },rows->records("最近短信",rows,()->SystemAssistantActions.open(activity,p,json("sms"))));
    }
    private void calls(){
        if(!permission(Manifest.permission.READ_CALL_LOG)){
            LinearLayout body=column();PocketUi.entry(body,p,"phone","授权最近通话","查看来电、去电与未接记录",()->request(Manifest.permission.READ_CALL_LOG,CALL_PERMISSION));PocketUi.entry(body,p,"phone","拨打电话","在系统拨号盘检查后呼叫",()->SystemAssistantActions.open(activity,p,json("call")));PocketUi.entry(body,p,"inbox","未接来电通知","权限受限时接入新通知",this::notifications);sheet("电话",body);return;
        }
        load("最近通话",()->CallHistory.read(activity,0,50),rows->{try{SmartRepository.status(activity,"calls",rows.isEmpty()?"读取成功，系统返回 0 条；如系统电话中有记录，请检查安全中心的数据访问限制":"读取成功 · 最近 "+rows.size()+" 条");}catch(Exception ignored){}records("最近通话",rows,()->SystemAssistantActions.open(activity,p,json("call")));});
    }
    private void callAccess(CallHistory.AccessException error){LinearLayout panel=column();panel.addView(text(error.getMessage(),14,p.text));gap(panel,12);PocketUi.entry(panel,p,"settings","检查通话记录权限","系统设置与 HyperOS 安全中心",this::appSettings);PocketUi.entry(panel,p,"phone","重新读取","检查本次权限与读取结果",this::calls);PocketUi.entry(panel,p,"inbox","接入未接来电通知","完整历史受限时可整理新收到的通知",this::notifications);panel.addView(text("不需要切换默认电话应用。通知接入只能读取新通知，不能补齐完整通话历史。",12,p.muted));sheet("通话记录读取状态",panel);}
    private static String value(Cursor c,String name){String s=c.getString(c.getColumnIndexOrThrow(name));return s==null?"":s;}
    private static String date(long at){return new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm",Locale.CHINA).format(new Date(at));}
    private static JSONObject json(String type){JSONObject o=new JSONObject();try{o.put("type",type);}catch(Exception ignored){}return o;}
    private void records(String title,List<JSONObject> rows,Runnable create){
        LinearLayout body=column();if(create!=null){Button add=button(title.contains("通话")?"打开拨号盘":title.contains("短信")?"写短信":"写邮件",false);body.addView(add);add.setOnClickListener(v->create.run());gap(body,12);}
        if(rows.isEmpty())body.addView(text(title.contains("通话")?"系统返回 0 条通话记录。如果系统电话里有记录，请检查安全中心的数据访问限制。":"暂无可读取的记录。",14,p.muted));
        for(JSONObject data:rows){
            LinearLayout row=PocketUi.column(activity);row.setPadding(dp(14),dp(12),dp(14),dp(12));row.setBackground(PocketUi.surface(activity,p.panel,18,p.border));
            TextView heading=text(data.optString("title","（无标题）"),14,p.text);heading.setMaxLines(2);row.addView(heading);TextView sub=text(data.optString("source")+" · "+data.optString("date"),11,p.muted);sub.setMaxLines(2);row.addView(sub);
            String snippet=data.optString("body");if(!snippet.isEmpty()){TextView preview=text(snippet,12,p.muted);preview.setMaxLines(3);preview.setEllipsize(android.text.TextUtils.TruncateAt.END);row.addView(preview);}
            row.setOnClickListener(v->detail(data));body.addView(row);gap(body,8);
        }
        if(title.contains("通话")){PocketUi.entry(body,p,"settings","通话记录权限与限制","检查系统权限与 HyperOS 安全中心",this::appSettings);PocketUi.entry(body,p,"inbox","未接来电通知接入","历史受限时整理新通知",this::notifications);}
        sheet(title,body);
    }
    private void detail(JSONObject record){
        fetchBodies(new JSONArray().put(record),ready->{JSONObject entry=ready.optJSONObject(0);LinearLayout body=column();body.addView(text(entry.optString("source")+"\n"+entry.optString("date"),12,p.muted));gap(body,12);TextView content=text(entry.optString("body"),14,p.text);content.setTextIsSelectable(true);body.addView(content);
            if(!entry.optString("phone").isEmpty()){gap(body,18);Button reply=button(entry.optString("kind").equals("通话记录")?"回拨电话":"起草短信",false);body.addView(reply);reply.setOnClickListener(v->{JSONObject action=json(entry.optString("kind").equals("通话记录")?"call":"sms");try{action.put("phone",entry.optString("phone"));}catch(Exception ignored){}SystemAssistantActions.open(activity,p,action);});}
            sheet(entry.optString("title","通讯详情"),body);
        });
    }
    private JSONObject account(String id)throws Exception{return MailAccounts.get(activity,id);}
    private String accountName(String id){try{JSONObject a=account(id);if(a!=null)return a.optString("label",a.optString("user","邮箱"));}catch(Exception ignored){}return "邮箱";}
    void accounts(){
        LinearLayout body=column();try{JSONArray entries=MailAccounts.list(activity);for(int i=0;i<entries.length();i++){JSONObject entry=entries.getJSONObject(i);String id=entry.getString("id");JSONObject saved=account(id);if(saved!=null)PocketUi.entry(body,p,"mail",accountName(id),saved.optString("user"),()->accountMenu(id));}if(entries.length()==0)body.addView(text("添加邮箱后，重要邮件会自动汇总到首页。",14,p.muted));}catch(Exception e){body.addView(text("邮箱列表暂时无法读取，已保存的配置仍保留。",13,p.error));}
        PocketUi.entry(body,p,"plus","添加邮箱","QQ、网易、学校、企业邮箱及其他 IMAP 服务",()->mailSettings(MailAccounts.newId()));
        PocketUi.entry(body,p,"mail","起草新邮件","在邮箱应用选择发件账号并发送",()->SystemAssistantActions.open(activity,p,json("email")));body.addView(text("读取 INBOX 最近 30 封邮件，不标记已读。正文按需获取，附件请在邮箱应用查看。",12,p.muted));sheet("我的邮箱",body);
    }
    private void accountMenu(String id){show(new RoundedDialogBuilder(activity).setTitle(accountName(id)).setItems(new String[]{"查看收件箱","连接设置与测试","移除本机邮箱凭据"},(d,i)->{if(i==0)inbox(id);else if(i==1)mailSettings(id);else show(new RoundedDialogBuilder(activity).setTitle("移除邮箱凭据？").setMessage("停止同步此邮箱，原始邮箱不受影响。").setNegativeButton("取消",null).setPositiveButton("移除",(x,y)->{try{MailAccounts.remove(activity,id);toast("邮箱已移除");AssistantSync.request(activity,true);}catch(Exception e){toast("移除失败");}}).create());}).setNegativeButton("返回",null).create());}
    private EditText field(LinearLayout panel,String title,String value,boolean password){panel.addView(text(title,12,p.muted));EditText e=new EditText(activity);e.setTextColor(p.text);e.setHintTextColor(p.muted);e.setTextSize(14);e.setSingleLine(true);e.setInputType(InputType.TYPE_CLASS_TEXT|(password?InputType.TYPE_TEXT_VARIATION_PASSWORD:InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD));e.setText(value);if(password){e.setSaveEnabled(false);e.setImportantForAutofill(android.view.View.IMPORTANT_FOR_AUTOFILL_NO);}panel.addView(e,new LinearLayout.LayoutParams(-1,-2));gap(panel,12);return e;}
    private void mailSettings(String id){
        JSONObject stored;try{stored=account(id);}catch(Exception e){stored=null;}final JSONObject old=stored;
        LinearLayout body=column();EditText label=field(body,"名称（可选）",old==null?"":old.optString("label"),false),user=field(body,"邮箱账号","",false),host=field(body,"IMAP 服务器（TLS）",old==null?"":old.optString("host"),false),port=field(body,"TLS 端口",old==null?"993":String.valueOf(old.optInt("port",993)),false),password=field(body,"客户端授权码 / IMAP 密码", "",true);user.setText(old==null?"":old.optString("user"));port.setInputType(InputType.TYPE_CLASS_NUMBER);password.setHint(old==null?"填写邮箱的客户端授权码或 IMAP 密码":"已加密保存，留空保留（账号和服务器须相同）");
        user.setOnFocusChangeListener((v,focus)->{if(!focus&&host.getText().toString().trim().isEmpty())host.setText(MailAccounts.suggestedHost(user.getText().toString()));});
        TextView help=text("支持标准 IMAP + TLS。常见邮箱可自动填入服务器，学校和企业邮箱按管理员提供的信息填写。需先开启 IMAP；仅支持 OAuth 登录的账号可使用邮箱通知接入。",12,p.muted);body.addView(help);gap(body,14);TextView feedback=text("保存后自动同步与整理；凭据只加密保存在手机。",12,p.muted);body.addView(feedback);gap(body,10);Button test=button("测试连接",false);body.addView(test);
        ScrollView scroll=new ScrollView(activity);scroll.addView(body);AlertDialog settings=show(new RoundedDialogBuilder(activity).setTitle(accountName(id)+"连接设置").setView(scroll).setNegativeButton("取消",null).setPositiveButton("保存",null).create());
        Work<JSONObject> values=()->{String hostname=host.getText().toString().trim(),username=user.getText().toString().trim();int number;try{number=Integer.parseInt(port.getText().toString());}catch(Exception e){throw new java.io.IOException("端口无效");}String secret=password.getText().toString();if(secret.isEmpty()&&old!=null&&hostname.equalsIgnoreCase(old.optString("host"))&&username.equals(old.optString("user"))&&number==old.optInt("port"))secret=old.getString("password");ImapMailbox.validate(hostname,number,username,secret);return new JSONObject().put("host",hostname).put("port",number).put("user",username).put("password",secret);};
        settings.setOnDismissListener(d->{dialogs.remove(settings);password.setText("");});
        settings.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{try{JSONObject next=values.run();next.put("label",label.getText().toString().trim().isEmpty()?next.getString("user"):label.getText().toString().trim());MailAccounts.save(activity,id,next);settings.dismiss();toast("邮箱已添加，正在自动整理");AssistantSync.request(activity,true);}catch(Exception e){feedback.setText(e instanceof java.io.IOException?e.getMessage():"配置保存失败，请重试");}});
        test.setOnClickListener(v->{try{JSONObject next=values.run();load("测试邮箱连接",()->{try(ImapMailbox connection=new ImapMailbox()){currentMailbox=connection;connect(connection,next);connection.list();return true;}finally{currentMailbox=null;}},ok->{if(settings.isShowing())feedback.setText("连接与收件箱读取成功，可以保存配置。");});}catch(Exception e){feedback.setText(e.getMessage()==null?"请检查输入":e.getMessage());}});
    }
    private void connect(ImapMailbox c,JSONObject config)throws Exception{if(closed||(activeTask!=null&&activeTask.cancelled)){c.cancel();throw new java.io.IOException("读取已取消");}c.connect(config.getString("host"),config.getInt("port"),config.getString("user"),config.getString("password"));if(closed||(activeTask!=null&&activeTask.cancelled)){c.cancel();throw new java.io.IOException("读取已取消");}}
    private void inbox(String id){
        JSONObject config;try{config=account(id);}catch(Exception e){config=null;}if(config==null){mailSettings(id);return;}final JSONObject saved=config;
        load(accountName(id),()->{try(ImapMailbox connection=new ImapMailbox()){currentMailbox=connection;connect(connection,saved);List<JSONObject> rows=connection.list();for(JSONObject row:rows)row.put("account",id).put("mailboxIdentity",identity(saved));return rows;}finally{currentMailbox=null;}},rows->records(accountName(id)+"收件箱",rows,()->SystemAssistantActions.open(activity,p,json("email"))));
    }
    private static String identity(JSONObject config){return config.optString("host").toLowerCase(Locale.ROOT)+":"+config.optInt("port")+"/"+config.optString("user");}
    private void fetchBodies(JSONArray items,Consumer<JSONArray> done){
        String id=items.length()==0?"":items.optJSONObject(0).optString("account");if(id.isEmpty()){done.accept(items);return;}
        boolean missing=false;for(int i=0;i<items.length();i++)if(items.optJSONObject(i).optString("body").isEmpty())missing=true;if(!missing){done.accept(items);return;}
        load("读取邮件正文",()->{JSONObject config=account(id);if(config==null)throw new java.io.IOException("请重新配置邮箱");for(int i=0;i<items.length();i++)if(!identity(config).equals(items.getJSONObject(i).optString("mailboxIdentity")))throw new java.io.IOException("邮箱配置已变化，请重新读取收件箱");try(ImapMailbox c=new ImapMailbox()){currentMailbox=c;connect(c,config);for(int i=0;i<items.length();i++){JSONObject m=items.getJSONObject(i);if(!id.equals(m.optString("account")))throw new java.io.IOException("账户不匹配");if(m.optString("body").isEmpty())m.put("body",c.body(m.getString("uid"),m.optString("uidValidity")));}return items;}finally{currentMailbox=null;}},done);
    }
    private void notifications(){
        LinearLayout body=column();Switch email=new Switch(activity);email.setText("保存邮箱应用的新通知");email.setTextColor(p.text);email.setChecked(prefs.getBoolean("mail_notifications",false));body.addView(email);gap(body,10);Switch sms=new Switch(activity);sms.setText("保存短信应用的新通知");sms.setTextColor(p.text);sms.setChecked(prefs.getBoolean("sms_notifications",false));body.addView(sms);gap(body,14);
        Switch call=new Switch(activity);call.setText("保存电话应用的未接来电通知");call.setTextColor(p.text);call.setChecked(prefs.getBoolean("call_notifications",false));body.addView(call);gap(body,14);call.setOnCheckedChangeListener((v,on)->prefs.edit().putBoolean("call_notifications",on).apply());
        body.addView(text("系统通知访问需手动开启。这里只保存上述应用的新通知摘要，不读取其他应用，不补抓历史通知。通知可能不含完整正文。",12,p.muted));gap(body,12);Button settings=button(notificationEnabled()?"检查系统通知访问":"开启系统通知访问",true);body.addView(settings);settings.setOnClickListener(v->{prefs.edit().putBoolean("mail_notifications",email.isChecked()).putBoolean("sms_notifications",sms.isChecked()).putBoolean("call_notifications",call.isChecked()).apply();try{Intent intent=new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS);activity.startActivity(intent);}catch(Exception e){toast("请在手机设置搜索“通知使用权”");}});
        email.setOnCheckedChangeListener((v,on)->prefs.edit().putBoolean("mail_notifications",on).apply());sms.setOnCheckedChangeListener((v,on)->prefs.edit().putBoolean("sms_notifications",on).apply());gap(body,10);Button view=button("查看已接入的通知",false);body.addView(view);view.setOnClickListener(v->{try{JSONArray saved=AssistantNotificationStore.list(activity);List<JSONObject> rows=new ArrayList<>();for(int i=0;i<saved.length();i++)rows.add(saved.getJSONObject(i));records("通讯通知",rows,null);}catch(Exception e){toast("通知缓存暂时无法读取");}});gap(body,10);Button clear=button("清空本机通知缓存",false);body.addView(clear);clear.setOnClickListener(v->show(new RoundedDialogBuilder(activity).setTitle("清空通知缓存？").setNegativeButton("取消",null).setPositiveButton("清空",(d,i)->{try{AssistantNotificationStore.clear(activity);toast("已清空");}catch(Exception e){toast("清空失败");}}).create()));sheet("通知接入",body);
    }
    private void dismissAll(){for(AlertDialog d:new ArrayList<>(dialogs))d.dismiss();}
    void close(){closed=true;ImapMailbox c=currentMailbox;if(c!=null)c.cancel();dismissAll();io.shutdownNow();}
}
