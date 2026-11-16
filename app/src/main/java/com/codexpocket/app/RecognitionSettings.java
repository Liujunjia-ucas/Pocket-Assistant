package com.codexpocket.app;

import android.app.*;
import android.content.*;
import android.provider.Settings;
import android.widget.*;
import java.util.*;

/** Shared speech input preference for the assistant and character conversations. */
final class RecognitionSettings {
    static void show(Activity a,ThemePalette p){
        List<String> ids=new ArrayList<>(),names=new ArrayList<>();
        ids.add(PhoneRecognition.AUTO);names.add("自动选择");
        if(OfflineRecognition.available(a)){ids.add(PhoneRecognition.INTERNAL);names.add("内置普通话 · 离线");}
        if(PhoneRecognition.onDevice(a)){ids.add(PhoneRecognition.DEVICE);names.add("系统本机识别");}
        for(ComponentName c:PhoneRecognition.services(a)){
            String name=c.getPackageName();try{name=a.getPackageManager().getServiceInfo(c,0).loadLabel(a.getPackageManager()).toString();}catch(Exception ignored){}
            ids.add(c.flattenToString());names.add(name+" · "+c.getPackageName());
        }
        SharedPreferences prefs=a.getSharedPreferences("phone_asr",0);
        LinearLayout body=PocketUi.column(a);int pad=PocketUi.dp(a,20);body.setPadding(pad,0,pad,pad);
        body.addView(PocketUi.text(a,"社区版使用手机系统语音服务，不附带离线模型。系统服务可能把录音发送到其云端；可选择系统提供的离线服务。若没有可用服务，请使用输入法语音输入或文字。",13,p.text));
        RadioGroup group=new RadioGroup(a);String chosen=prefs.getString("service",PhoneRecognition.AUTO);
        for(int i=0;i<ids.size();i++){String id=ids.get(i);RadioButton item=new RadioButton(a);item.setId(android.view.View.generateViewId());item.setText(names.get(i));item.setTextColor(p.text);item.setPadding(0,12,0,12);group.addView(item);item.setChecked(id.equals(chosen));item.setOnCheckedChangeListener((b,on)->{if(on)prefs.edit().putString("service",id).apply();});}body.addView(group);
        body.addView(PocketUi.text(a,"已适当延长句中停顿。识别把握较低时，助理会保留文字供你核对后发送。系统词语提示是否生效取决于服务支持。",12,p.muted));
        Button system=new Button(a);system.setText("系统语音输入设置");PocketUi.button(system,p,false);body.addView(system);system.setOnClickListener(v->{try{a.startActivity(new Intent(Settings.ACTION_VOICE_INPUT_SETTINGS));}catch(Exception e){try{a.startActivity(new Intent(Settings.ACTION_INPUT_METHOD_SETTINGS));}catch(Exception ignored){Toast.makeText(a,"请在系统语言与输入法设置中启用中文语音输入",Toast.LENGTH_LONG).show();}}});
        ScrollView scroll=new ScrollView(a);scroll.addView(body);new RoundedDialogBuilder(a).setTitle("语音输入").setView(scroll).setPositiveButton("完成",null).show();
    }
}
