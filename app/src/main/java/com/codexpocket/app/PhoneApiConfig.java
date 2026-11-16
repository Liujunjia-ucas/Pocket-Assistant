package com.codexpocket.app;

import android.content.Context;
import android.content.SharedPreferences;
import org.json.JSONObject;
import java.io.IOException;

/** Reads the same origin-bound encrypted configuration used by the Android workbench. */
final class PhoneApiConfig {
    final String provider,base,model,protocol,key;
    private PhoneApiConfig(String provider,String base,String model,String protocol,String key){this.provider=provider;this.base=base;this.model=model;this.protocol=protocol;this.key=key;}
    static PhoneApiConfig load(Context activity)throws Exception{
        return load(activity,"");
    }
    static PhoneApiConfig load(Context activity,String requiredProvider)throws Exception{
        SharedPreferences prefs=activity.getSharedPreferences("android_direct",Context.MODE_PRIVATE);
        String provider=requiredProvider.isEmpty()?prefs.getString("provider","deepseek"):requiredProvider;
        ProviderCatalog.Entry entry=ProviderCatalog.require(provider);
        String base=DirectEndpoint.normalize(prefs.getString(provider+".base",entry.base));
        String model=prefs.getString(provider+".model",entry.defaultValue("model"));
        String protocol=prefs.getString(provider+".protocol",entry.protocol);
        String saved=SecretStore.load(activity,"direct_"+provider);if(saved.isEmpty()||model.isEmpty())throw new IOException("请先在模型与 API 中设置"+entry.name+"的密钥和模型。");
        if(!protocol.equals("chat")&&!protocol.equals("responses"))throw new IOException("接口格式无效，请重新保存 API 设置。");
        JSONObject credential=new JSONObject(saved);if(!DirectEndpoint.sameOrigin(credential.getString("base"),base))throw new IOException("API 密钥与地址不匹配，请在 Android 页重新保存。");
        String key=credential.getString("key");if(key.trim().isEmpty())throw new IOException("请先在 Android 页设置 API 密钥。");
        return new PhoneApiConfig(provider,base,model,protocol,key);
    }
    DirectApi client(){return new DirectApi(base,key);}
}
