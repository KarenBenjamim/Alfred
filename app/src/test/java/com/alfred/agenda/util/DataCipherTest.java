package com.alfred.agenda.util;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;

import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;

/**
 * Formato da criptografia dos dados pessoais (perfil e plano alimentar).
 * No app a chave vem do Android Keystore; aqui uma chave AES comum testa o mesmo formato.
 */
public class DataCipherTest {

    private static SecretKey newKey() throws GeneralSecurityException {
        KeyGenerator gen = KeyGenerator.getInstance("AES");
        gen.init(256);
        return gen.generateKey();
    }

    private static final byte[] PLAIN = "{\"meals\":[\"Café da manhã\"],\"peso\":70.5}".getBytes(StandardCharsets.UTF_8);

    @Test
    public void sealedDataOpensBackToTheOriginal() throws Exception {
        SecretKey key = newKey();
        byte[] sealed = DataCipher.seal(key, PLAIN);
        assertTrue(DataCipher.isSealed(sealed));
        assertArrayEquals(PLAIN, DataCipher.open(key, sealed));
    }

    @Test
    public void sealedDataDoesNotContainThePlainText() throws Exception {
        String sealed = new String(DataCipher.seal(newKey(), PLAIN), StandardCharsets.ISO_8859_1);
        assertFalse(sealed.contains("Café"));
        assertFalse(sealed.contains("peso"));
    }

    @Test
    public void sameTextTwiceGivesDifferentBytes() throws Exception {
        SecretKey key = newKey();
        assertFalse(java.util.Arrays.equals(DataCipher.seal(key, PLAIN), DataCipher.seal(key, PLAIN)));
    }

    @Test
    public void tamperedDataIsRejected() throws Exception {
        SecretKey key = newKey();
        byte[] sealed = DataCipher.seal(key, PLAIN);
        sealed[sealed.length - 1] ^= 1;
        try {
            DataCipher.open(key, sealed);
            fail("dado alterado não pode abrir");
        } catch (GeneralSecurityException expected) {
            // ok
        }
    }

    @Test
    public void wrongKeyIsRejected() throws Exception {
        byte[] sealed = DataCipher.seal(newKey(), PLAIN);
        try {
            DataCipher.open(newKey(), sealed);
            fail("outra chave não pode abrir");
        } catch (GeneralSecurityException expected) {
            // ok
        }
    }

    @Test
    public void legacyPlainJsonIsRecognizedAsNotSealed() throws Exception {
        assertFalse(DataCipher.isSealed(PLAIN)); // arquivo antigo: lido como JSON e regravado cifrado
        assertFalse(DataCipher.isSealed(new byte[0]));
        assertFalse(DataCipher.isSealed(null));
        try {
            DataCipher.open(newKey(), new byte[]{'A', 'L', 'F', '1', 0, 1});
            fail("cabeçalho sem conteúdo não pode abrir");
        } catch (GeneralSecurityException expected) {
            // ok
        }
    }
}
