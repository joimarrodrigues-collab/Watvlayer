package com.watv.player;
import android.content.Context;
import android.security.keystore.*;
import android.util.AtomicFile;
import java.io.*;
import java.security.*;
import javax.crypto.*;
import javax.crypto.spec.GCMParameterSpec;
import java.nio.charset.StandardCharsets;
import org.json.*;

final class Vault {
    private final Context context;
    Vault(Context context){this.context=context;}
    private SecretKey key() throws Exception {
        KeyStore ks=KeyStore.getInstance("AndroidKeyStore");ks.load(null);
        if(!ks.containsAlias("watv-local")){
            KeyGenerator gen=KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES,"AndroidKeyStore");
            gen.init(new KeyGenParameterSpec.Builder("watv-local",KeyProperties.PURPOSE_ENCRYPT|KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build());gen.generateKey();
        }
        return (SecretKey)ks.getKey("watv-local",null);
    }
    synchronized String read(String file) throws Exception {
        File f=new File(context.getFilesDir(),file);
        AtomicFile atomic=new AtomicFile(f);
        if(!f.exists() && !new File(f+".bak").exists())return null;
        byte[] b=atomic.readFully();
        Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE,key(),new GCMParameterSpec(128,java.util.Arrays.copyOfRange(b,0,12)));
        return new String(cipher.doFinal(b,12,b.length-12),StandardCharsets.UTF_8);
    }
    synchronized void write(String file,String text) throws Exception {
        Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");cipher.init(Cipher.ENCRYPT_MODE,key());
        AtomicFile atomic=new AtomicFile(new File(context.getFilesDir(),file));FileOutputStream out=null;
        try{out=atomic.startWrite();out.write(cipher.getIV());out.write(cipher.doFinal(text.getBytes(StandardCharsets.UTF_8)));atomic.finishWrite(out);}
        catch(Exception e){if(out!=null)atomic.failWrite(out);throw e;}
    }
    void remove(String file){new AtomicFile(new File(context.getFilesDir(),file)).delete();}
    static String pinHash(String pin,String salt) throws Exception {
        javax.crypto.spec.PBEKeySpec spec=new javax.crypto.spec.PBEKeySpec(pin.toCharArray(),salt.getBytes(StandardCharsets.UTF_8),60000,256);
        try{return android.util.Base64.encodeToString(SecretKeyFactory.getInstance("PBKDF2WithHmacSHA1").generateSecret(spec).getEncoded(),android.util.Base64.NO_WRAP);}
        finally{spec.clearPassword();}
    }
    static JSONObject object(JSONObject o,String key) {
        JSONObject v=o.optJSONObject(key);if(v==null){v=new JSONObject();put(o,key,v);}return v;
    }
    static void put(JSONObject o,String key,Object value){try{o.put(key,value);}catch(JSONException e){throw new IllegalStateException(e);}}
}
