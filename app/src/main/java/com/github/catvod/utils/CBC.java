package com.github.catvod.utils;

import com.github.catvod.crawler.SpiderDebug;
import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.security.Key;

public class CBC {
    public static byte[] CBC(byte[] data, byte[] key, byte[] iv) {
        try {
            IvParameterSpec ivParameterSpec = new IvParameterSpec(iv);
            SecretKeySpec secretKeySpec = new SecretKeySpec(key, "AES");
            Cipher cipher = Cipher.getInstance("AES/CBC/PKCS7Padding");
            cipher.init(2, secretKeySpec, ivParameterSpec);
            return cipher.doFinal(data);
        } catch (Exception e) {
            SpiderDebug.log(e);
            return null;
        }
    }

    private static final char[] ue = "0123456789ABCDEF".toCharArray();

    public static String hexs(String str, String str2, String str3) {
        try {
            SecretKeySpec secretKeySpec = new SecretKeySpec(Pd(str2.getBytes()).getEncoded(), "AES");
            Cipher cipher = Cipher.getInstance("AES/CBC/PKCS7Padding");
            cipher.init(1, secretKeySpec, new IvParameterSpec(str3.getBytes()));
            return ue(cipher.doFinal(str.getBytes("UTF-8")));
        } catch (Exception e) {
            SpiderDebug.log(e);
            return null;
        }
    }

    private static Key Pd(byte[] bArr) {
        return new SecretKeySpec(bArr, "AES");
    }

    public static String ue(byte[] bArr) {
        char[] cArr = new char[bArr.length * 2];
        int i = 0;
        for (byte b : bArr) {
            int i2 = i + 1;
            char[] cArr2 = ue;
            cArr[i] = cArr2[(b >> 4) & 15];
            i = i2 + 1;
            cArr[i2] = cArr2[(b >> 0) & 15];
        }
        return new String(cArr);
    }
}