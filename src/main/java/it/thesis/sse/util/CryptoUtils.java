package it.thesis.sse.util;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.Mac;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;

/**
 * Cryptographic utilities for SSE operations.
 * Wraps common crypto primitives used across SSE schemes.
 */
public class CryptoUtils {

    private static final String AES_ALGORITHM = "AES";
    private static final String AES_GCM_ALGORITHM = "AES/GCM/NoPadding";
    private static final String HMAC_ALGORITHM = "HmacSHA256";
    private static final String HASH_ALGORITHM = "SHA-256";

    private static final int AES_KEY_SIZE = 256;
    private static final int GCM_IV_LENGTH = 12;
    private static final int GCM_TAG_LENGTH = 128;

    private static final SecureRandom secureRandom = new SecureRandom();

    /**
     * Generate a new AES key.
     */
    public static byte[] generateKey() throws NoSuchAlgorithmException {
        KeyGenerator keyGen = KeyGenerator.getInstance(AES_ALGORITHM);
        keyGen.init(AES_KEY_SIZE, secureRandom);
        SecretKey key = keyGen.generateKey();
        return key.getEncoded();
    }

    /**
     * Generate a random byte array.
     */
    public static byte[] generateRandom(int length) {
        byte[] bytes = new byte[length];
        secureRandom.nextBytes(bytes);
        return bytes;
    }

    /**
     * Encrypt data using AES-GCM.
     */
    public static byte[] encryptAesGcm(byte[] plaintext, byte[] key) throws Exception {
        SecretKeySpec keySpec = new SecretKeySpec(key, AES_ALGORITHM);
        Cipher cipher = Cipher.getInstance(AES_GCM_ALGORITHM);

        byte[] iv = new byte[GCM_IV_LENGTH];
        secureRandom.nextBytes(iv);

        GCMParameterSpec parameterSpec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
        cipher.init(Cipher.ENCRYPT_MODE, keySpec, parameterSpec);

        byte[] ciphertext = cipher.doFinal(plaintext);

        // Prepend IV to ciphertext
        byte[] result = new byte[iv.length + ciphertext.length];
        System.arraycopy(iv, 0, result, 0, iv.length);
        System.arraycopy(ciphertext, 0, result, iv.length, ciphertext.length);

        return result;
    }

    /**
     * Decrypt data using AES-GCM.
     */
    public static byte[] decryptAesGcm(byte[] ciphertext, byte[] key) throws Exception {
        SecretKeySpec keySpec = new SecretKeySpec(key, AES_ALGORITHM);
        Cipher cipher = Cipher.getInstance(AES_GCM_ALGORITHM);

        // Extract IV from beginning of ciphertext
        byte[] iv = Arrays.copyOfRange(ciphertext, 0, GCM_IV_LENGTH);
        byte[] encryptedData = Arrays.copyOfRange(ciphertext, GCM_IV_LENGTH, ciphertext.length);

        GCMParameterSpec parameterSpec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
        cipher.init(Cipher.DECRYPT_MODE, keySpec, parameterSpec);

        return cipher.doFinal(encryptedData);
    }

    /**
     * Compute HMAC-SHA256.
     */
    public static byte[] hmac(byte[] key, byte[] data) throws Exception {
        SecretKeySpec keySpec = new SecretKeySpec(key, HMAC_ALGORITHM);
        Mac mac = Mac.getInstance(HMAC_ALGORITHM);
        mac.init(keySpec);
        return mac.doFinal(data);
    }

    /**
     * Compute HMAC-SHA256 for string data.
     */
    public static byte[] hmac(byte[] key, String data) throws Exception {
        return hmac(key, data.getBytes("UTF-8"));
    }

    /**
     * Compute SHA-256 hash.
     */
    public static byte[] hash(byte[] data) throws NoSuchAlgorithmException {
        MessageDigest digest = MessageDigest.getInstance(HASH_ALGORITHM);
        return digest.digest(data);
    }

    /**
     * Compute SHA-256 hash for string.
     */
    public static byte[] hash(String data) throws NoSuchAlgorithmException {
        return hash(data.getBytes());
    }

    /**
     * Compute PRF (Pseudorandom Function) output.
     * Uses HMAC-SHA256.
     */
    public static byte[] prf(byte[] key, byte[] input) throws Exception {
        return hmac(key, input);
    }

    /**
     * Compute PRF for string input.
     */
    public static byte[] prf(byte[] key, String input) throws Exception {
        return prf(key, input.getBytes("UTF-8"));
    }

    /**
     * Derive a key from a master key and label.
     */
    public static byte[] deriveKey(byte[] masterKey, String label) throws Exception {
        return hmac(masterKey, label);
    }

    /**
     * Convert bytes to hex string.
     */
    public static String toHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    /**
     * Convert hex string to bytes.
     */
    public static byte[] fromHex(String hex) {
        int len = hex.length();
        byte[] data = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            data[i / 2] = (byte) ((Character.digit(hex.charAt(i), 16) << 4)
                    + Character.digit(hex.charAt(i + 1), 16));
        }
        return data;
    }

    /**
     * Convert bytes to Base64 string.
     */
    public static String toBase64(byte[] bytes) {
        return Base64.getEncoder().encodeToString(bytes);
    }

    /**
     * Convert Base64 string to bytes.
     */
    public static byte[] fromBase64(String base64) {
        return Base64.getDecoder().decode(base64);
    }

    /**
     * Securely compare two byte arrays in constant time.
     */
    public static boolean constantTimeEquals(byte[] a, byte[] b) {
        if (a.length != b.length) {
            return false;
        }

        int result = 0;
        for (int i = 0; i < a.length; i++) {
            result |= a[i] ^ b[i];
        }
        return result == 0;
    }

    /**
     * Securely erase a byte array.
     */
    public static void secureErase(byte[] data) {
        if (data != null) {
            Arrays.fill(data, (byte) 0);
        }
    }

    /**
     * XOR two byte arrays.
     */
    public static byte[] xor(byte[] a, byte[] b) {
        if (a.length != b.length) {
            throw new IllegalArgumentException("Arrays must have same length");
        }

        byte[] result = new byte[a.length];
        for (int i = 0; i < a.length; i++) {
            result[i] = (byte) (a[i] ^ b[i]);
        }
        return result;
    }

    /**
     * Generate a deterministic token from key and input.
     * Used for search tokens in SSE.
     */
    public static String generateToken(byte[] key, String input) throws Exception {
        byte[] prfOutput = prf(key, input);
        return toBase64(prfOutput);
    }

    /**
     * Encrypt a string using AES-GCM.
     */
    public static String encryptString(String plaintext, byte[] key) throws Exception {
        byte[] encrypted = encryptAesGcm(plaintext.getBytes("UTF-8"), key);
        return toBase64(encrypted);
    }

    /**
     * Decrypt a string using AES-GCM.
     */
    public static String decryptString(String ciphertext, byte[] key) throws Exception {
        byte[] decrypted = decryptAesGcm(fromBase64(ciphertext), key);
        return new String(decrypted, "UTF-8");
    }
}
