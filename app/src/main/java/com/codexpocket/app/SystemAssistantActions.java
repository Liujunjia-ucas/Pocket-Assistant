package com.codexpocket.app;

import android.app.*;
import android.content.*;
import android.net.Uri;
import android.text.InputType;
import android.widget.*;
import org.json.*;
import java.io.File;

/** Editable drafts followed by system UI. No direct SMS send, call or SMTP capability. */
final class SystemAssistantActions {
    static String title(JSONObject action){switch(action.optString("type")){case "communications":String source=action.optString("source");return "查看"+(source.equals("sms")?"短信":source.equals("calls")?"通话记录":source.matches("email|ucas|qq")?"邮箱":"通讯通知")+"  ↗";case "sms":return "检查短信草稿  ↗";case "call":return "打开拨号盘  ↗";case "email":return "检查邮件草稿  ↗";default:return "保存这条待办  ↗";}}
    static void open(Activity a,ThemePalette p,JSONObject original){
        if(original.optString("type").equals("communications"))return;
        try{JSONObject data=new JSONObject(original.toString());String type=data.optString("type");LinearLayout panel=PocketUi.column(a);panel.setPadding(dp(a,22),dp(a,8),dp(a,22),dp(a,12));
            EditText recipient=null,subject=null,body=null;
            if(type.equals("sms")||type.equals("call"))recipient=field(a,p,panel,"电话号码",data.optString("phone"),false);
            if(type.equals("email")){recipient=field(a,p,panel,"收件邮箱",data.optString("to"),false);subject=field(a,p,panel,"主题",data.optString("subject"),false);}
            if(!type.equals("call"))body=field(a,p,panel,type.equals("task")?"待办":"正文",data.optString(type.equals("task")?"text":"body"),true);
            ScrollView scroll=new ScrollView(a);scroll.addView(panel);TextView error=PocketUi.text(a,"",12,p.error);panel.addView(error);
            final EditText target=recipient,heading=subject,content=body;
            AlertDialog dialog=new RoundedDialogBuilder(a).setTitle(title(data).replace("  ↗","")).setView(scroll).setNegativeButton("取消",null).setPositiveButton(type.equals("task")?"保存待办":type.equals("call")?"前往拨号盘":"前往系统应用",null).create();dialog.show();
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{try{
                if(target!=null)data.put(type.equals("email")?"to":"phone",target.getText().toString().trim());if(heading!=null)data.put("subject",heading.getText().toString());if(content!=null)data.put(type.equals("task")?"text":"body",content.getText().toString());AssistantActions.validate(data);
                if(type.equals("task")){new AssistantStore(new File(a.getFilesDir(),"assistant")).add("task",data.getString("text"));Toast.makeText(a,"待办已保存",Toast.LENGTH_SHORT).show();}
                else if(type.equals("call"))a.startActivity(new Intent(Intent.ACTION_DIAL,Uri.fromParts("tel",AssistantActions.phone(data.getString("phone")),null)));
                else if(type.equals("sms")){Intent intent=new Intent(Intent.ACTION_SENDTO,Uri.fromParts("smsto",AssistantActions.phone(data.getString("phone")),null));intent.putExtra("sms_body",data.getString("body"));a.startActivity(intent);}
                else{String address=AssistantActions.email(data.getString("to"));Uri uri=Uri.parse("mailto:"+Uri.encode(address,"@")+"?subject="+Uri.encode(data.optString("subject"))+"&body="+Uri.encode(data.getString("body")));a.startActivity(new Intent(Intent.ACTION_SENDTO,uri));}
                dialog.dismiss();
            }catch(ActivityNotFoundException e){error.setText("手机没有可处理此操作的系统应用，请安装短信、电话或邮箱应用。");}catch(Exception e){error.setText(e instanceof java.io.IOException?e.getMessage():"无法准备草稿，请检查输入。");}});
        }catch(Exception e){Toast.makeText(a,"无法准备操作",Toast.LENGTH_SHORT).show();}
    }
    private static int dp(Activity a,int n){return PocketUi.dp(a,n);}
    private static EditText field(Activity a,ThemePalette p,LinearLayout panel,String title,String value,boolean multi){panel.addView(PocketUi.text(a,title,12,p.muted));EditText e=new EditText(a);e.setTextColor(p.text);e.setTextSize(14);e.setInputType(InputType.TYPE_CLASS_TEXT|(multi?InputType.TYPE_TEXT_FLAG_MULTI_LINE:0));e.setSingleLine(!multi);if(multi){e.setMinLines(3);e.setMaxLines(7);}e.setText(value);panel.addView(e,new LinearLayout.LayoutParams(-1,-2));PocketUi.gap(panel,12);return e;}
}
