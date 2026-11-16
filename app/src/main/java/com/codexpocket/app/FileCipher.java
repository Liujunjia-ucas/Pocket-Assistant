package com.codexpocket.app;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.Arrays;
import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

/** Versioned authenticated encryption; no plaintext fallback. */
final class FileCipher {
    private static final byte[] MAGIC={'P','A','E','1'};
    static byte[] encrypt(SecretKey key,String identity,byte[] plaintext)throws GeneralSecurityException{
        Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE,key);
        cipher.updateAAD(identity.getBytes(StandardCharsets.UTF_8));
        byte[] iv=cipher.getIV(),encrypted=cipher.doFinal(plaintext);
        if(iv.length!=12)throw new GeneralSecurityException("Unexpected GCM IV");
        byte[] output=new byte[16+encrypted.length];
        System.arraycopy(MAGIC,0,output,0,4);System.arraycopy(iv,0,output,4,12);
        System.arraycopy(encrypted,0,output,16,encrypted.length);return output;
    }
    static byte[] decrypt(SecretKey key,String identity,byte[] encrypted)throws GeneralSecurityException{
        if(encrypted.length<32||!Arrays.equals(MAGIC,Arrays.copyOf(encrypted,4)))
            throw new GeneralSecurityException("Not a community encrypted file");
        Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE,key,new GCMParameterSpec(128,Arrays.copyOfRange(encrypted,4,16)));
        cipher.updateAAD(identity.getBytes(StandardCharsets.UTF_8));
        return cipher.doFinal(encrypted,16,encrypted.length-16);
    }
    private FileCipher(){}
}
