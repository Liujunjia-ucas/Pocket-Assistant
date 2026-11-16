package com.codexpocket.app;

import android.content.Context;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;
import java.security.KeyStore;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

/** Credentials are encrypted with a non-exportable Android Keystore key. */
final class SecretStore {
    private static final String ALIAS = "codex_pocket_pairing";
    static synchronized SecretKey key() throws Exception {
        KeyStore store = KeyStore.getInstance("AndroidKeyStore"); store.load(null);
        if (store.containsAlias(ALIAS)) return (SecretKey) store.getKey(ALIAS, null);
        KeyGenerator generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore");
        generator.init(new KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build());
        return generator.generateKey();
    }
    static void save(Context context, String value) throws Exception {
        save(context, "token", value);
    }
    static void save(Context context, String slot, String value) throws Exception {
        if (value.isEmpty()) {
            if (!context.getSharedPreferences("credentials", Context.MODE_PRIVATE).edit().remove(slot).commit()) throw new Exception("无法保存凭据");
            return;
        }
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding"); cipher.init(Cipher.ENCRYPT_MODE, key());
        if (!"token".equals(slot)) cipher.updateAAD(slot.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        String encrypted = Base64.encodeToString(cipher.getIV(), Base64.NO_WRAP) + ":" + Base64.encodeToString(cipher.doFinal(value.getBytes(java.nio.charset.StandardCharsets.UTF_8)), Base64.NO_WRAP);
        if (!context.getSharedPreferences("credentials", Context.MODE_PRIVATE).edit().putString(slot, encrypted).commit()) throw new Exception("无法保存凭据");
    }
    static String load(Context context) throws Exception {
        return load(context, "token");
    }
    static String load(Context context, String slot) throws Exception {
        String value = context.getSharedPreferences("credentials", Context.MODE_PRIVATE).getString(slot, "");
        if (value.isEmpty()) return "";
        String[] parts = value.split(":", 2);
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding"); cipher.init(Cipher.DECRYPT_MODE, key(), new GCMParameterSpec(128, Base64.decode(parts[0], Base64.NO_WRAP)));
        if (!"token".equals(slot)) cipher.updateAAD(slot.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        return new String(cipher.doFinal(Base64.decode(parts[1], Base64.NO_WRAP)), java.nio.charset.StandardCharsets.UTF_8);
    }
}
