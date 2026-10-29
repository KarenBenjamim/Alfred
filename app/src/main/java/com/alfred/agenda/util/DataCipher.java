package com.alfred.agenda.util;

import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

/**
 * Criptografia dos dados pessoais gravados fora do banco (perfil e plano alimentar).
 *
 * AES-256-GCM com a chave no Android Keystore: a chave é criada no próprio aparelho e nunca
 * sai do hardware de segurança, então o arquivo copiado para outro lugar não pode ser lido.
 * (Faz o papel do EncryptedSharedPreferences/EncryptedFile, cuja biblioteca foi descontinuada.)
 *
 * Formato: "ALF1" + 12 bytes de IV + texto cifrado (com a tag de autenticação do GCM).
 * O formato (seal/open) não depende do Keystore e é testado com uma chave comum.
 */
public final class DataCipher {

    private static final byte[] MAGIC = {'A', 'L', 'F', '1'};
    private static final int IV_BYTES = 12;
    private static final int TAG_BITS = 128;
    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final String KEYSTORE = "AndroidKeyStore";
    private static final String KEY_ALIAS = "alfred_data_v1";
    private static final SecureRandom RANDOM = new SecureRandom();

    private DataCipher() {
    }

    // ===================== Formato (testável sem Android) =====================

    /** Cifra com a chave dada. */
    static byte[] seal(SecretKey key, byte[] plain) throws GeneralSecurityException {
        byte[] iv = new byte[IV_BYTES];
        RANDOM.nextBytes(iv);
        Cipher cipher = Cipher.getInstance(TRANSFORMATION);
        cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
        return pack(iv, cipher.doFinal(plain));
    }

    /** Junta cabeçalho, IV e texto cifrado. */
    private static byte[] pack(byte[] iv, byte[] body) {
        byte[] out = new byte[MAGIC.length + iv.length + body.length];
        System.arraycopy(MAGIC, 0, out, 0, MAGIC.length);
        System.arraycopy(iv, 0, out, MAGIC.length, iv.length);
        System.arraycopy(body, 0, out, MAGIC.length + iv.length, body.length);
        return out;
    }

    /** Decifra; dados alterados ou chave errada lançam GeneralSecurityException. */
    static byte[] open(SecretKey key, byte[] sealed) throws GeneralSecurityException {
        if (!isSealed(sealed) || sealed.length < MAGIC.length + IV_BYTES + TAG_BITS / 8) {
            throw new GeneralSecurityException("Formato inválido");
        }
        byte[] iv = Arrays.copyOfRange(sealed, MAGIC.length, MAGIC.length + IV_BYTES);
        Cipher cipher = Cipher.getInstance(TRANSFORMATION);
        cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
        return cipher.doFinal(sealed, MAGIC.length + IV_BYTES, sealed.length - MAGIC.length - IV_BYTES);
    }

    /** Diz se os bytes estão no formato cifrado (arquivos antigos, de antes da criptografia, não estão). */
    public static boolean isSealed(byte[] data) {
        if (data == null || data.length < MAGIC.length) return false;
        for (int i = 0; i < MAGIC.length; i++) if (data[i] != MAGIC[i]) return false;
        return true;
    }

    // ===================== Com a chave do Keystore =====================

    /** Cifra com a chave do Keystore; o IV é sorteado pelo próprio Cipher (o Keystore não aceita IV de fora). */
    public static byte[] encrypt(byte[] plain) throws GeneralSecurityException {
        Cipher cipher = Cipher.getInstance(TRANSFORMATION);
        cipher.init(Cipher.ENCRYPT_MODE, key());
        return pack(cipher.getIV(), cipher.doFinal(plain));
    }

    public static byte[] decrypt(byte[] sealed) throws GeneralSecurityException {
        return open(key(), sealed);
    }

    /** Texto -> Base64 cifrado (para SharedPreferences). */
    public static String encryptString(String plain) throws GeneralSecurityException {
        return Base64.getEncoder().encodeToString(encrypt(plain.getBytes(StandardCharsets.UTF_8)));
    }

    /** Base64 cifrado -> texto. Base64 inválido também vira GeneralSecurityException. */
    public static String decryptString(String sealedBase64) throws GeneralSecurityException {
        try {
            return new String(decrypt(Base64.getDecoder().decode(sealedBase64)), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            throw new GeneralSecurityException("Base64 inválido", e);
        }
    }

    /** Chave do app no Keystore (criada na primeira vez). */
    private static synchronized SecretKey key() throws GeneralSecurityException {
        try {
            KeyStore ks = KeyStore.getInstance(KEYSTORE);
            ks.load(null);
            KeyStore.Entry entry = ks.getEntry(KEY_ALIAS, null);
            if (entry instanceof KeyStore.SecretKeyEntry) return ((KeyStore.SecretKeyEntry) entry).getSecretKey();
            KeyGenerator gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE);
            gen.init(new KeyGenParameterSpec.Builder(KEY_ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256)
                    .build());
            return gen.generateKey();
        } catch (java.io.IOException e) {
            throw new GeneralSecurityException("Keystore indisponível", e);
        }
    }
}
