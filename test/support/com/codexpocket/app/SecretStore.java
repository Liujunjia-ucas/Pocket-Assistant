package com.codexpocket.app;
/** JVM-only key adapter: Android production sources never include test/support. */
final class SecretStore {
    private static javax.crypto.SecretKey instance;
    static synchronized javax.crypto.SecretKey key()throws Exception{
        if(instance==null){javax.crypto.KeyGenerator generator=javax.crypto.KeyGenerator.getInstance("AES");generator.init(256);instance=generator.generateKey();}
        return instance;
    }
}
