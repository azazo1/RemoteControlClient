package com.azazo1.remotecontrolclient;


import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigInteger;
import java.security.Key;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;

public class Encryptor {
    private static final Base64.Encoder base64Encoder = Base64.getEncoder();
    private static final Base64.Decoder base64Decoder = Base64.getDecoder();
    private static final MessageDigest md5Encoder = getMD5Algorithm();

    private static MessageDigest getMD5Algorithm() {
        try {
            return MessageDigest.getInstance("md5");
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
            return null;
        }
    }

    public static String base64Encode(byte[] data) {
        return new String(base64Encoder.encode(data), Config.charset);
    }

    public static byte[] base64Decode(String content) {
        return base64Decoder.decode(content);
    }

    public static String md5(byte[] data) {
        if (md5Encoder != null) {
            byte[] secretBytes;
            secretBytes = md5Encoder.digest(data);
            String md5code = new BigInteger(1, secretBytes).toString(16);
            for (int i = 0; i < 32 - md5code.length(); i++) {
                md5code = "0".concat(md5code);
            }
            return md5code;
        } else {
            return null;
        }
    }

    public static byte[] addTo16(byte[] data) {
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            bytes.write(data);
            while (bytes.size() % 16 != 0) {
                bytes.write('\0');
            }

            return bytes.toByteArray();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }

    public static String AESBase64Encode(String thisKey, String data) {
        try {
            Key key = new SecretKeySpec(addTo16(thisKey.getBytes(Config.charset)), Config.algorithm);
            Cipher cipher = Cipher.getInstance(Config.algorithmAll);
            cipher.init(Cipher.ENCRYPT_MODE, key);
            byte[] result = cipher.doFinal(addTo16(data.getBytes(Config.charset)));
            return base64Encode(result);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    public static String Base64AESDecode(String thisKey, String base64Data) {
        try {
            byte[] preparedData = base64Decode(base64Data);
            Key key = new SecretKeySpec(addTo16(thisKey.getBytes(Config.charset)), Config.algorithm);
            Cipher cipher = Cipher.getInstance(Config.algorithmAll);
            cipher.init(Cipher.DECRYPT_MODE, key);
            byte[] result = cipher.doFinal(addTo16(preparedData));
            String stringResult = new String(result, Config.charset);
            return stringResult.replaceAll("[\0\10]", "");
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

}
