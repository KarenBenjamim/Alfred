package com.alfred.agenda.account;

import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

/**
 * Hash de senha com PBKDF2-HMAC-SHA256 e sal aleatório por conta.
 * A senha em si nunca é gravada; só o sal e o hash (em Base64).
 * É lento de propósito: chame fora da thread principal.
 */
public final class PasswordHasher {

    static final int ITERATIONS = 100_000;
    private static final int SALT_BYTES = 16;
    private static final int HASH_BITS = 256;
    private static final SecureRandom RANDOM = new SecureRandom();

    private PasswordHasher() {
    }

    public static String newSalt() {
        byte[] salt = new byte[SALT_BYTES];
        RANDOM.nextBytes(salt);
        return Base64.getEncoder().encodeToString(salt);
    }

    public static String hash(String password, String saltBase64) {
        return hash(password, saltBase64, ITERATIONS);
    }

    static String hash(String password, String saltBase64, int iterations) {
        char[] chars = password.toCharArray();
        PBEKeySpec spec = new PBEKeySpec(chars, Base64.getDecoder().decode(saltBase64), iterations, HASH_BITS);
        try {
            byte[] key = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded();
            return Base64.getEncoder().encodeToString(key);
        } catch (GeneralSecurityException e) {
            // PBKDF2WithHmacSHA256 existe em todo Android 8+; se faltar, é defeito do aparelho
            throw new IllegalStateException("PBKDF2 indisponível", e);
        } finally {
            spec.clearPassword();
            Arrays.fill(chars, '\0');
        }
    }

    /**
     * Compara em tempo constante (não revela quantos caracteres batem).
     * Sal ou hash gravados corrompidos (Base64 inválido) contam como senha errada, sem derrubar o app.
     */
    public static boolean matches(String password, String saltBase64, String expectedHash, int iterations) {
        try {
            byte[] actual = Base64.getDecoder().decode(hash(password, saltBase64, iterations));
            byte[] expected = Base64.getDecoder().decode(expectedHash);
            return MessageDigest.isEqual(actual, expected);
        } catch (IllegalArgumentException e) {
            return false;
        }
    }
}
