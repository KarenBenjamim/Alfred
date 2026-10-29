package com.alfred.agenda.account;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** Hash de senha: a senha certa confere, a errada não, e cada conta tem sal próprio. */
public class PasswordHasherTest {

    @Test
    public void correctPasswordMatches() {
        String salt = PasswordHasher.newSalt();
        String hash = PasswordHasher.hash("segredo1", salt);
        assertTrue(PasswordHasher.matches("segredo1", salt, hash, PasswordHasher.ITERATIONS));
    }

    @Test
    public void wrongPasswordDoesNotMatch() {
        String salt = PasswordHasher.newSalt();
        String hash = PasswordHasher.hash("segredo1", salt, 1000);
        assertFalse(PasswordHasher.matches("segredo2", salt, hash, 1000));
        assertFalse(PasswordHasher.matches("Segredo1", salt, hash, 1000));
    }

    @Test
    public void sameSaltSameHashDifferentSaltDifferentHash() {
        String salt = PasswordHasher.newSalt();
        assertEquals(PasswordHasher.hash("abc123", salt, 1000), PasswordHasher.hash("abc123", salt, 1000));
        assertNotEquals(PasswordHasher.hash("abc123", salt, 1000),
                PasswordHasher.hash("abc123", PasswordHasher.newSalt(), 1000));
    }

    @Test
    public void corruptedStoredDataIsWrongPasswordNotACrash() {
        String salt = PasswordHasher.newSalt();
        String hash = PasswordHasher.hash("segredo1", salt, 1000);
        assertFalse(PasswordHasher.matches("segredo1", "sal@@inválido", hash, 1000));
        assertFalse(PasswordHasher.matches("segredo1", salt, "hash@@inválido", 1000));
        assertFalse(PasswordHasher.matches("segredo1", salt, hash, 0)); // iterações corrompidas
    }

    @Test
    public void hashNeverContainsThePassword() {
        String salt = PasswordHasher.newSalt();
        assertFalse(PasswordHasher.hash("minhasenha", salt, 1000).contains("minhasenha"));
    }
}
