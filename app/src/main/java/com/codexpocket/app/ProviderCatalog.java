package com.codexpocket.app;

import java.util.*;

/** Mainstream public API presets. Entries are options, never a claim of account entitlement. */
final class ProviderCatalog {
    static final class Entry {
        final String id,name,shortName,base,protocol,console,docs,hint;
        final boolean domestic;final List<String> examples;
        Entry(String id,String name,String shortName,String base,String protocol,String console,String docs,String hint,boolean domestic,String... models){
            this.id=id;this.name=name;this.shortName=shortName;this.base=base;this.protocol=protocol;this.console=console;this.docs=docs;this.hint=hint;this.domestic=domestic;this.examples=Collections.unmodifiableList(Arrays.asList(models));
        }
        String defaultValue(String field){switch(field){case "base":return base;case "protocol":return protocol;case "model":return examples.isEmpty()?"":examples.get(0);default:return "";}}
    }
    private static final List<Entry> ENTRIES=Collections.unmodifiableList(Arrays.asList(
        new Entry("deepseek","DeepSeek","DeepSeek","https://api.deepseek.com/v1","chat","https://platform.deepseek.com/api_keys","https://api-docs.deepseek.com/zh-cn/","使用 DeepSeek 开放平台的 API Key。",true,"deepseek-flash","deepseek-v4-flash","deepseek-v4-pro"),
        new Entry("qwen","通义千问 · 阿里云百炼","通义千问","https://dashscope.aliyuncs.com/compatible-mode/v1","chat","https://bailian.console.aliyun.com/","https://help.aliyun.com/zh/model-studio/compatibility-of-openai-with-dashscope","默认中国内地接口；其他地域需按百炼文档修改地址并使用对应地域密钥。",true,"qwen-plus","qwen3.7-plus","qwen3.8-max","qwen3.5-flash","qwen-max"),
        new Entry("moonshot","Kimi · 月之暗面","Kimi","https://api.moonshot.cn/v1","chat","https://platform.kimi.com/console/api-keys","https://platform.kimi.com/docs/api/chat","填写 Kimi 开放平台 API Key，网页会员不等于 API 调用额度。",true,"kimi-k3.1-pro","kimi-k3.1-pro-highspeed","kimi-k2.6"),
        new Entry("ark","豆包 · 火山方舟","豆包","https://ark.cn-beijing.volces.com/api/v3","chat","https://console.volcengine.com/ark/","https://docs.volcengine.com/docs/zh/ark/chat-api.md","使用火山方舟 API Key；模型须已开通，也可填写控制台的 ep- 接入点 ID。绘图和语音接口不在此处调用。",true,"doubao-seed-2-1-pro-260915","doubao-seed-2-1-lite-260915"),
        new Entry("glm","智谱 GLM · 智谱开放平台","智谱 GLM","https://open.bigmodel.cn/api/paas/v4","chat","https://bigmodel.cn/usercenter/proj-mgmt/apikeys","https://docs.bigmodel.cn/cn/guide/develop/openai/introduction","默认通用模型 API；Coding Plan 专用接口须按套餐文档配置地址和模型权限。",true,"glm-5.3","glm-5.3-flash","glm-5.2"),
        new Entry("minimax","MiniMax · 开放平台","MiniMax","https://api.minimax.cn/v1","chat","https://platform.minimax.cn/console/home","https://platform.minimax.cn/docs/api-reference/text-openai-api","使用中国内地开放平台 API Key；国际站或 Coding Plan 请按对应文档设置最终接口地址。",true,"MiniMax-M3.1-Flash","MiniMax-M3"),
        new Entry("qianfan","百度文心 · 千帆","百度文心","https://qianfan.baidubce.com/v2","chat","https://console.bce.baidu.com/qianfan/","https://cloud.baidu.com/doc/qianfan-api/s/3m7of64lb","填写千帆 API Key，不是旧版 AK/SK 或 OAuth Access Token。按已开通的文心模型选择 ID。",true),
        new Entry("hunyuan","腾讯混元","腾讯混元","https://api.hunyuan.cloud.tencent.com/v1","chat","https://console.cloud.tencent.com/hunyuan/","https://cloud.tencent.com/document/product/1729/111007","使用混元兼容接口的 API Key，不是腾讯云 SecretId / SecretKey。",true,"hunyuan-turbos-latest","hunyuan-turbos","hunyuan-lite"),
        new Entry("spark","讯飞星火","讯飞星火","https://spark-api-open.xf-yun.com/v1","chat","https://console.xfyun.cn/services/bm35","https://www.xfyun.cn/doc/spark/HTTP调用文档.html","填写 HTTP 接口的 APIPassword，不是 WebSocket 的 APPID / APISecret。",true,"generalv3.5","4.0Ultra","max-32k","generalv3","pro-128k","lite"),
        new Entry("penguin","OpenAI API","OpenAI","https://api.openai.com/v1","responses","https://platform.openai.com/api-keys","https://platform.openai.com/docs/","使用你自己的 OpenAI API Key；不预置第三方中转服务。",false)
    ));
    static List<Entry> all(){return ENTRIES;}
    static Entry find(String id){for(Entry e:ENTRIES)if(e.id.equals(id))return e;return null;}
    static String validOrDefault(String id){return find(id)==null?"deepseek":id;}
    static Entry require(String id)throws java.io.IOException{Entry e=find(id);if(e==null)throw new java.io.IOException("未知模型平台，请重新选择模型与 API。");return e;}
    static String defaultValue(String id,String field){Entry e=find(id);return e==null?"":e.defaultValue(field);}
    static String name(String id){Entry e=find(id);return e==null?"未知平台":e.name;}
    static List<Entry> search(String query){String q=query.trim().toLowerCase(Locale.ROOT);List<Entry> out=new ArrayList<>();for(Entry e:ENTRIES)if(q.isEmpty()||(e.id+" "+e.name+" "+e.base).toLowerCase(Locale.ROOT).contains(q))out.add(e);return out;}
    private ProviderCatalog(){}
}
