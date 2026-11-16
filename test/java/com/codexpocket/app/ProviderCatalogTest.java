package com.codexpocket.app;
import java.util.*;
import java.net.URI;
public class ProviderCatalogTest {
    static void check(boolean ok,String why){if(!ok)throw new AssertionError(why);}
    public static void main(String[] args)throws Exception{
        Set<String> expected=new HashSet<>(Arrays.asList("deepseek","qwen","moonshot","ark","glm","minimax","qianfan","hunyuan","spark","penguin"));Set<String> ids=new HashSet<>();int domestic=0;
        for(ProviderCatalog.Entry e:ProviderCatalog.all()){
            check(ids.add(e.id),"unique stable provider IDs");check(expected.contains(e.id),"mainstream-only catalog");if(e.domestic)domestic++;
            check(DirectEndpoint.normalize(e.base).equals(e.base),"final HTTPS base preserved for "+e.id);check(e.base.indexOf('@')<0,"no inline credential");
            check(new URI(e.console).getScheme().equals("https")&&new URI(e.docs).getScheme().equals("https"),"HTTPS public links");check(e.protocol.equals(e.domestic?"chat":"responses"),"correct default wire format");
            for(String model:e.examples)check(!model.isEmpty()&&!model.matches(".*\\s.*"),"usable model identifier syntax");
        }
        check(ids.equals(expected)&&domestic==9,"nine mainstream domestic platforms and existing ChatGPT");
        check(ProviderCatalog.search("千问").get(0).id.equals("qwen")&&ProviderCatalog.search("KIMI").get(0).id.equals("moonshot"),"Chinese/English search");
        check(ProviderCatalog.search("ark").get(0).id.equals("ark")&&ProviderCatalog.search("not-a-provider").isEmpty(),"stable ID search and empty result");
        check(ProviderCatalog.find("glm").base.endsWith("/api/paas/v4")&&ProviderCatalog.find("ark").base.endsWith("/api/v3")&&ProviderCatalog.find("qianfan").base.endsWith("/v2"),"provider-specific API prefixes not rewritten as /v1");
        check(ProviderCatalog.find("spark").hint.contains("APIPassword")&&ProviderCatalog.find("hunyuan").hint.contains("SecretId"),"noninterchangeable credential types explained");
        try{ProviderCatalog.require("unknown");throw new AssertionError("unknown provider accepted");}catch(java.io.IOException expectedError){}
        try{ProviderCatalog.all().clear();throw new AssertionError("mutable registry");}catch(UnsupportedOperationException expectedError){}
        check(ProviderCatalog.defaultValue("deepseek","model").equals("deepseek-flash")&&ProviderCatalog.find("penguin").protocol.equals("responses"),"legacy defaults stable");
        System.out.println("PASS: exact mainstream catalog, stable IDs, HTTPS/API prefixes, credential types, model syntax, search, immutable registry and legacy defaults.");
    }
}
