package com.alfred.agenda.cloud;

import static org.junit.Assert.assertEquals;

import com.alfred.agenda.R;

import org.junit.Test;

/** Validação do login na nuvem e tradução dos erros do Firebase Auth. */
public class CloudAuthTest {

    @Test
    public void emailMustLookLikeAnEmail() {
        assertEquals(0, CloudAuth.validateEmail("ana@exemplo.com"));
        assertEquals(0, CloudAuth.validateEmail("ana.souza+alfred@mail.exemplo.com.br"));
        assertEquals(R.string.auth_error_required, CloudAuth.validateEmail(""));
        assertEquals(R.string.auth_error_email, CloudAuth.validateEmail("ana"));
        assertEquals(R.string.auth_error_email, CloudAuth.validateEmail("ana@exemplo"));
        assertEquals(R.string.auth_error_email, CloudAuth.validateEmail("ana @exemplo.com"));
        assertEquals(R.string.auth_error_email, CloudAuth.validateEmail("ana@@exemplo.com"));
    }

    @Test
    public void cloudPasswordNeedsEightToSixtyFourChars() {
        assertEquals(R.string.auth_error_cloud_password, CloudAuth.validatePassword("1234567"));
        assertEquals(0, CloudAuth.validatePassword("12345678"));
        assertEquals(0, CloudAuth.validatePassword("x".repeat(64)));
        assertEquals(R.string.auth_error_cloud_password, CloudAuth.validatePassword("x".repeat(65)));
    }

    @Test
    public void wrongEmailAndWrongPasswordLookTheSame() {
        // Não revela se o e-mail tem conta
        int expected = R.string.auth_error_wrong_email;
        assertEquals(expected, CloudAuth.messageFor("ERROR_WRONG_PASSWORD"));
        assertEquals(expected, CloudAuth.messageFor("ERROR_USER_NOT_FOUND"));
        assertEquals(expected, CloudAuth.messageFor("ERROR_INVALID_CREDENTIAL"));
    }

    @Test
    public void knownErrorsHaveTheirMessagesAndUnknownIsGeneric() {
        assertEquals(R.string.auth_error_email_taken, CloudAuth.messageFor("ERROR_EMAIL_ALREADY_IN_USE"));
        assertEquals(R.string.auth_error_cloud_password, CloudAuth.messageFor("ERROR_WEAK_PASSWORD"));
        assertEquals(R.string.auth_error_method_off, CloudAuth.messageFor("ERROR_OPERATION_NOT_ALLOWED"));
        assertEquals(R.string.auth_error_generic, CloudAuth.messageFor("ERROR_ALGO_NOVO"));
        assertEquals(R.string.auth_error_generic, CloudAuth.messageFor((String) null));
    }
}
