package com.codexpocket.app;
import java.io.File;
import java.nio.file.Files;
import java.nio.charset.StandardCharsets;
import org.json.JSONObject;
public final class PrivateRecordsTest {
    private static void encrypted(File file)throws Exception{
        byte[] bytes=Files.readAllBytes(file.toPath());
        if(bytes.length<32||bytes[0]!='P'||bytes[1]!='A'||bytes[2]!='E'||bytes[3]!='1'||new String(bytes,StandardCharsets.UTF_8).contains("PRIVATE_SENTINEL"))throw new AssertionError("Plaintext file: "+file.getName());
    }
    public static void main(String[] args)throws Exception{
        File root=Files.createTempDirectory("community-records-").toFile();
        try{
            FileVault.initialize(root);
            DirectChats chats=new DirectChats(new File(root,"chats"));JSONObject chat=chats.create("deepseek","https://api.deepseek.com/v1","fixture","chat");chat.put("draft","PRIVATE_SENTINEL");chats.save(chat);
            File chatFile=new File(root,"chats/"+chat.getString("id")+".json");encrypted(chatFile);
            if(!chats.load(chat.getString("id")).getString("draft").equals("PRIVATE_SENTINEL"))throw new AssertionError("Chat round trip");
            AssistantStore tasks=new AssistantStore(new File(root,"assistant"));tasks.add("note","PRIVATE_SENTINEL");encrypted(new File(root,"assistant/organizer.json"));if(tasks.list().length()!=1)throw new AssertionError("Task round trip");
            AssistantWorkStore work=new AssistantWorkStore(new File(root,"work"));work.create("android","PRIVATE_SENTINEL");encrypted(new File(root,"work/work.json"));if(work.list().length()!=1)throw new AssertionError("Work round trip");
            LocalCompanion companion=new LocalCompanion(new File(root,"companion"));encrypted(new File(root,"companion/companion.json"));if(companion.view().getBoolean("enabled"))throw new AssertionError("Memory collection defaults on");
            SharedCompanionMemory memory=new SharedCompanionMemory(new File(root,"shared"),root);encrypted(new File(root,"shared/memory.json"));if(memory.view().getBoolean("enabled"))throw new AssertionError("Shared memory defaults on");
            File wrong=new File(root,"wrong.json");Files.copy(chatFile.toPath(),wrong.toPath());try{FileVault.read(wrong);throw new AssertionError("Ciphertext transplanted between files");}catch(java.io.IOException expected){}
            File plain=new File(root,"plain.json");Files.write(plain.toPath(),"PRIVATE_SENTINEL".getBytes(StandardCharsets.UTF_8));try{FileVault.read(plain);throw new AssertionError("Plaintext fallback");}catch(java.io.IOException expected){}
            System.out.println("PrivateRecords: chat/task/work/companion/shared storage encrypted, round trips, memory opt-in, transplant and plaintext rejection passed");
        }finally{try(java.util.stream.Stream<java.nio.file.Path> paths=Files.walk(root.toPath())){for(java.nio.file.Path p:paths.sorted(java.util.Comparator.reverseOrder()).toList())Files.delete(p);}}
    }
}
