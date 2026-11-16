package com.codexpocket.app;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

/** Private structured records only; binds ciphertext to its final application path. */
final class FileVault {
    private static volatile File root;
    static void initialize(File files)throws IOException{root=files.getCanonicalFile();}
    private static String identity(File file)throws IOException{
        File base=root;
        if(base==null)throw new IOException("Private storage was not initialized");
        String target=file.getCanonicalPath(),prefix=base.getPath()+File.separator;
        if(!target.startsWith(prefix))throw new IOException("Outside private application storage");
        return "pocket-assistant-community/"+target.substring(prefix.length()).replace(File.separatorChar,'/');
    }
    static byte[] encrypt(File file,byte[] plaintext)throws IOException{
        try{return FileCipher.encrypt(SecretStore.key(),identity(file),plaintext);}
        catch(Exception e){throw new IOException("无法加密保存本机资料；没有写入明文。",e);}
    }
    static byte[] read(File file)throws IOException{
        try{
            String name=identity(file);
            if(file.length()>6*1024*1024)throw new IOException("Private record too large");
            return FileCipher.decrypt(SecretStore.key(),name,Files.readAllBytes(file.toPath()));
        }catch(Exception e){throw new IOException("本机资料无法解密；原文件已保留，请勿覆盖。",e);}
    }
    private FileVault(){}
}
