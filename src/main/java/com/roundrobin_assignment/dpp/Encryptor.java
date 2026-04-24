package com.roundrobin_assignment.dpp;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;

public class Encryptor {

    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final String SECRET_KEY_ALGORITHM = "AES";
    private static final String SHA_512 = "SHA-512";
    private static final String SHA_256 = "SHA-256";

    private static final int AES_KEY_256_BIT_LENGTH = 32;
    private static final int GCM_IV_LENGTH = 12;
    private static final int GCM_TAG_LENGTH = 128;
    private static final int MAX_CODE_LENGTH = 128;

    public static String generateCodeVerifier(String keyString) {
        String result = generateShaDigest(keyString, SHA_512);
        if (result.length() < MAX_CODE_LENGTH / 2) {
            result = rightPad(result, MAX_CODE_LENGTH / 2, keyString.charAt(keyString.length() - 1));
        }
        if (result.length() > MAX_CODE_LENGTH) {
            result = result.substring(0, MAX_CODE_LENGTH);
        }
        return result;
    }

    public static String generateCodeChallenge(String codeVerifier) {
        return generateShaDigest(codeVerifier, SHA_256);
    }

    public static String encrypt(String secret, String plainText) {
        if (plainText == null || plainText.isEmpty()) {
            throw new RuntimeException("No data to encrypt!");
        }
        try {
            SecretKeySpec newSecretKey = createSecretKey(secret);
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            byte[] initializationVector = generateInitializationVector();

            GCMParameterSpec gcmParameterSpec = new GCMParameterSpec(GCM_TAG_LENGTH, initializationVector);
            cipher.init(Cipher.ENCRYPT_MODE, newSecretKey, gcmParameterSpec);
            byte[] cipherText = cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8));

            byte[] combined = new byte[initializationVector.length + cipherText.length];
            System.arraycopy(initializationVector, 0, combined, 0, initializationVector.length);
            System.arraycopy(cipherText, 0, combined, initializationVector.length, cipherText.length);
            return base64UrlEncode(combined);
        } catch (Exception e) {
            throw new RuntimeException("Error in encrypt: " + e.getMessage(), e);
        }
    }

    public static String decrypt(String secret, String encryptedText) {
        if (encryptedText == null || encryptedText.isEmpty()) {
            throw new RuntimeException("No data to encrypt!");
        }
        try {
            SecretKeySpec newSecretKey = createSecretKey(secret);
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);

            byte[] combined = base64UrlDecode(encryptedText);
            byte[] initializationVector = new byte[GCM_IV_LENGTH];
            byte[] encryptedBytes = new byte[combined.length - GCM_IV_LENGTH];
            System.arraycopy(combined, 0, initializationVector, 0, GCM_IV_LENGTH);
            System.arraycopy(combined, GCM_IV_LENGTH, encryptedBytes, 0, encryptedBytes.length);

            GCMParameterSpec gcmParameterSpec = new GCMParameterSpec(GCM_TAG_LENGTH, initializationVector);
            cipher.init(Cipher.DECRYPT_MODE, newSecretKey, gcmParameterSpec);

            return new String(cipher.doFinal(encryptedBytes));
        } catch (Exception e) {
            throw new RuntimeException("Error in decrypt: " + e.getMessage(), e);
        }
    }

    private static SecretKeySpec createSecretKey(final String secret) {
        try {
            byte[] key = secret.getBytes(StandardCharsets.UTF_8);
            MessageDigest sha = MessageDigest.getInstance(SHA_512);

            key = sha.digest(key);
            key = Arrays.copyOf(key, AES_KEY_256_BIT_LENGTH);

            return new SecretKeySpec(key, SECRET_KEY_ALGORITHM);
        } catch (Exception e) {
            throw new RuntimeException("Error in createSecretKey: " + e.getMessage());
        }
    }

    private static byte[] generateInitializationVector() {
        try {
            byte[] iv = new byte[GCM_IV_LENGTH];
            SecureRandom.getInstanceStrong().nextBytes(iv);
            return iv;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static String generateShaDigest(String text, String algorithm) {
        try {
            MessageDigest digest = MessageDigest.getInstance(algorithm);
            byte[] hash = digest.digest(text.getBytes(StandardCharsets.UTF_8));
            return base64UrlEncode(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }

    public static String urlEncode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    public static String urlDecode(String value) {
        return URLDecoder.decode(value, StandardCharsets.UTF_8);
    }

    public static String base64UrlEncode(String value) {
        return base64UrlEncode(value.getBytes(StandardCharsets.UTF_8));
    }

    public static String base64UrlEncode(byte[] byteValue) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(byteValue);
    }

    private static byte[] base64UrlDecode(String value) {
        return base64UrlDecode(value.getBytes(StandardCharsets.UTF_8));
    }

    private static byte[] base64UrlDecode(byte[] byteValue) {
        return Base64.getUrlDecoder().decode(byteValue);
    }

    public static String base64UrlDecodeString(String value) {
        return new String(base64UrlDecode(value.getBytes(StandardCharsets.UTF_8)), StandardCharsets.UTF_8);
    }

    private static String rightPad(final String str, final int size, final char padChar) {
        if (str == null) {
            return null;
        }
        final int pads = size - str.length();
        if (pads <= 0) {
            return str; // returns original String when possible
        }
        return str.concat(repeat(padChar, pads));
    }

    private static String repeat(final char ch, final int repeat) {
        if (repeat <= 0) {
            return "";
        }
        final char[] buf = new char[repeat];
        Arrays.fill(buf, ch);
        return new String(buf);
    }
}
