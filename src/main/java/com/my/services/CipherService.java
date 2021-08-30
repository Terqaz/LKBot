package com.my.services;

import com.my.models.AuthenticationData;

import javax.crypto.*;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.KeySpec;
import java.util.Base64;

public final class CipherService {

    private static CipherService instance = null;

    public static CipherService getInstance ()
            throws NoSuchPaddingException, NoSuchAlgorithmException, InvalidKeySpecException {
        if (instance == null)
            instance = new CipherService();
        return instance;
    }

    private static final String ALGORITHM = "AES/CBC/PKCS5Padding";

    private static Cipher cipher = null;
    private static SecretKey secretKey = null;
    private static IvParameterSpec iv = null;

    private CipherService () throws NoSuchPaddingException, NoSuchAlgorithmException, InvalidKeySpecException {
        cipher = Cipher.getInstance(ALGORITHM);

        final String encryptionKey = System.getenv("PASSWORDS_ENCRYPTION_KEY");

        KeySpec spec = new PBEKeySpec(encryptionKey.toCharArray(), encryptionKey.getBytes(), 8096, 128);
        secretKey = new SecretKeySpec(
                SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded(),
                "AES");

        // Iv не генерируется рандомно, чтобы после возможного перезапуска приложения
        // не пришлось перезашифровывать все пароли
        iv = new IvParameterSpec(encryptionKey.substring(0, 16).getBytes());
    }

    public AuthenticationData encrypt (String login, String password) {
        changeMode(Cipher.ENCRYPT_MODE);
        return new AuthenticationData(
                encryptString(login),
                encryptString(password));
    }

    public AuthenticationData decrypt (AuthenticationData data) {
        changeMode(Cipher.DECRYPT_MODE);
        return new AuthenticationData(
                decryptString(data.getLogin()),
                decryptString(data.getPassword()));
    }

    private void changeMode (int decryptMode) {
        try {
            cipher.init(decryptMode, secretKey, iv);
        } catch (InvalidAlgorithmParameterException | InvalidKeyException e) {
            e.printStackTrace();
        }
    }

    // Если начальная настройка корректна и ключ взят из окружения, то исключения не должны выбрасываться
    private String encryptString (String s) {
        try {
            return Base64.getEncoder().encodeToString(cipher.doFinal(s.getBytes()));
        } catch (IllegalBlockSizeException | BadPaddingException e) {
            e.printStackTrace();
            return null;
        }
    }

    // Если начальная настройка корректна и ключ взят из окружения, то исключения не должны выбрасываться
    private String decryptString (String s) {
        try {
            return new String(cipher.doFinal(Base64.getDecoder().decode(s)));
        } catch (IllegalBlockSizeException | BadPaddingException e) {
            e.printStackTrace();
            return null;
        }
    }
}
