package com.codexpocket.app;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
public final class FileCipherTest {
    interface Action {void run()throws Exception;}
    private static void rejected(Action action)throws Exception{try{action.run();throw new AssertionError("Accepted invalid ciphertext");}catch(java.security.GeneralSecurityException expected){}}
    public static void main(String[] args)throws Exception{
        KeyGenerator generator=KeyGenerator.getInstance("AES");generator.init(256);SecretKey key=generator.generateKey();
        byte[] plain="private conversation and task".getBytes(StandardCharsets.UTF_8);
        byte[] one=FileCipher.encrypt(key,"chat/a",plain),two=FileCipher.encrypt(key,"chat/a",plain);
        if(Arrays.equals(one,two))throw new AssertionError("GCM nonce reused");
        if(!Arrays.equals(plain,FileCipher.decrypt(key,"chat/a",one)))throw new AssertionError("Round trip failed");
        rejected(()->FileCipher.decrypt(key,"chat/b",one));
        rejected(()->FileCipher.decrypt(generator.generateKey(),"chat/a",one));
        byte[] tampered=one.clone();tampered[tampered.length-1]^=1;rejected(()->FileCipher.decrypt(key,"chat/a",tampered));
        rejected(()->FileCipher.decrypt(key,"chat/a",plain));rejected(()->FileCipher.decrypt(key,"chat/a",new byte[0]));
        System.out.println("FileCipher: authenticated roundtrip, random nonce, path binding, wrong-key, tamper and plaintext rejection passed");
    }
}
