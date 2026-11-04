package com.alfred.agenda.cloud;

import android.app.Activity;
import android.content.Context;
import android.os.CancellationSignal;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.core.content.ContextCompat;
import androidx.credentials.ClearCredentialStateRequest;
import androidx.credentials.Credential;
import androidx.credentials.CredentialManager;
import androidx.credentials.CredentialManagerCallback;
import androidx.credentials.CustomCredential;
import androidx.credentials.GetCredentialRequest;
import androidx.credentials.GetCredentialResponse;
import androidx.credentials.exceptions.ClearCredentialException;
import androidx.credentials.exceptions.GetCredentialCancellationException;
import androidx.credentials.exceptions.GetCredentialException;
import androidx.credentials.exceptions.NoCredentialException;

import com.alfred.agenda.R;
import com.alfred.agenda.account.AccountStore;
import com.alfred.agenda.util.AppLog;
import com.google.android.gms.tasks.Task;
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption;
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential;
import com.google.firebase.FirebaseNetworkException;
import com.google.firebase.FirebaseTooManyRequestsException;
import com.google.firebase.auth.AuthResult;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthException;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.GoogleAuthProvider;

/**
 * Login na nuvem (Firebase Authentication): e-mail/senha ou conta Google.
 * Os resultados voltam na thread principal. Mensagens de erro já em português.
 */
public final class CloudAuth {

    /** Resultado de uma operação: errorRes = 0 deu certo; cancelled = a usuária desistiu. */
    public interface Callback {
        void done(@StringRes int errorRes, boolean cancelled);
    }

    private CloudAuth() {
    }

    // ===================== Validação e erros (regras puras, testadas) =====================

    @StringRes
    public static int validateEmail(String email) {
        if (email.isEmpty()) return R.string.auth_error_required;
        return looksLikeEmail(email) ? 0 : R.string.auth_error_email;
    }

    static boolean looksLikeEmail(String email) {
        // Regra simples e pura (testável na JVM): algo@algo.algo, sem espaços; o Firebase confere de vez
        return email.length() <= 254 && email.matches("[^@\\s]+@[^@\\s]+\\.[^@\\s]+");
    }

    @StringRes
    public static int validatePassword(String password) {
        // Mesmo tamanho das contas locais (o Firebase exige 6; usamos 8)
        return AccountStore.validatePassword(password) == 0 ? 0 : R.string.auth_error_cloud_password;
    }

    /** Código de erro do Firebase Auth -> mensagem para a tela. */
    @StringRes
    static int messageFor(@Nullable String errorCode) {
        if (errorCode == null) return R.string.auth_error_generic;
        switch (errorCode) {
            case "ERROR_INVALID_EMAIL": return R.string.auth_error_email;
            // Mesma mensagem para e-mail inexistente e senha errada (não revela quais contas existem)
            case "ERROR_WRONG_PASSWORD":
            case "ERROR_USER_NOT_FOUND":
            case "ERROR_INVALID_CREDENTIAL": return R.string.auth_error_wrong_email;
            case "ERROR_EMAIL_ALREADY_IN_USE": return R.string.auth_error_email_taken;
            case "ERROR_WEAK_PASSWORD": return R.string.auth_error_cloud_password;
            case "ERROR_USER_DISABLED": return R.string.auth_error_disabled;
            case "ERROR_ACCOUNT_EXISTS_WITH_DIFFERENT_CREDENTIAL": return R.string.auth_error_other_method;
            case "ERROR_OPERATION_NOT_ALLOWED": return R.string.auth_error_method_off;
            default: return R.string.auth_error_generic;
        }
    }

    @StringRes
    static int messageFor(@Nullable Exception e) {
        if (e instanceof FirebaseNetworkException) return R.string.auth_error_network;
        if (e instanceof FirebaseTooManyRequestsException) return R.string.auth_error_too_many;
        if (e instanceof FirebaseAuthException) return messageFor(((FirebaseAuthException) e).getErrorCode());
        return R.string.auth_error_generic;
    }

    // ===================== Sessão =====================

    @Nullable
    public static FirebaseUser currentUser(Context ctx) {
        FirebaseAuth auth = CloudConfig.auth(ctx);
        return auth == null ? null : auth.getCurrentUser();
    }

    // ===================== E-mail e senha =====================

    public static void signIn(Context ctx, String email, String password, Callback cb) {
        FirebaseAuth auth = CloudConfig.auth(ctx);
        if (auth == null) {
            cb.done(R.string.auth_error_unavailable, false);
            return;
        }
        deliver(auth.signInWithEmailAndPassword(email.trim(), password), cb);
    }

    public static void register(Context ctx, String email, String password, Callback cb) {
        FirebaseAuth auth = CloudConfig.auth(ctx);
        if (auth == null) {
            cb.done(R.string.auth_error_unavailable, false);
            return;
        }
        deliver(auth.createUserWithEmailAndPassword(email.trim(), password), cb);
    }

    /** Envia o e-mail de redefinição. Responde "ok" mesmo se o e-mail não tiver conta. */
    public static void resetPassword(Context ctx, String email, Callback cb) {
        FirebaseAuth auth = CloudConfig.auth(ctx);
        if (auth == null) {
            cb.done(R.string.auth_error_unavailable, false);
            return;
        }
        auth.sendPasswordResetEmail(email.trim()).addOnCompleteListener(t -> {
            Exception e = t.getException();
            boolean noAccount = e instanceof FirebaseAuthException
                    && "ERROR_USER_NOT_FOUND".equals(((FirebaseAuthException) e).getErrorCode());
            cb.done(t.isSuccessful() || noAccount ? 0 : messageFor(e), false);
        });
    }

    // ===================== Google =====================

    /**
     * Id do cliente web do projeto, gerado pelo plugin google-services a partir do
     * google-services.json. Sem ele (ex.: emuladores locais), não há login com Google.
     */
    @Nullable
    public static String googleClientId(Context ctx) {
        @SuppressWarnings("DiscouragedApi") // o recurso só existe quando há google-services.json
        int id = ctx.getResources().getIdentifier("default_web_client_id", "string", ctx.getPackageName());
        return id == 0 ? null : ctx.getString(id);
    }

    public static boolean isGoogleAvailable(Context ctx) {
        return CloudConfig.isAvailable(ctx) && !CloudConfig.isUsingEmulator() && googleClientId(ctx) != null;
    }

    /** Abre a escolha de conta Google do sistema (Credential Manager) e entra no Firebase. */
    public static void signInWithGoogle(Activity activity, Callback cb) {
        FirebaseAuth auth = CloudConfig.auth(activity);
        String clientId = googleClientId(activity);
        if (auth == null || clientId == null) {
            cb.done(R.string.auth_error_unavailable, false);
            return;
        }
        GetCredentialRequest request = new GetCredentialRequest.Builder()
                .addCredentialOption(new GetSignInWithGoogleOption.Builder(clientId).build())
                .build();
        CredentialManager.create(activity).getCredentialAsync(activity, request, new CancellationSignal(),
                ContextCompat.getMainExecutor(activity),
                new CredentialManagerCallback<GetCredentialResponse, GetCredentialException>() {
                    @Override
                    public void onResult(GetCredentialResponse response) {
                        Credential c = response.getCredential();
                        if (!(c instanceof CustomCredential)
                                || !GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL.equals(c.getType())) {
                            cb.done(R.string.auth_error_generic, false);
                            return;
                        }
                        try {
                            String idToken = GoogleIdTokenCredential.createFrom(c.getData()).getIdToken();
                            deliver(auth.signInWithCredential(GoogleAuthProvider.getCredential(idToken, null)), cb);
                        } catch (RuntimeException e) {
                            AppLog.e("Credencial Google inválida", e);
                            cb.done(R.string.auth_error_generic, false);
                        }
                    }

                    @Override
                    public void onError(@NonNull GetCredentialException e) {
                        if (e instanceof GetCredentialCancellationException) cb.done(0, true);
                        else if (e instanceof NoCredentialException) cb.done(R.string.auth_error_no_google, false);
                        else {
                            AppLog.e("Falha no login com Google", e);
                            cb.done(R.string.auth_error_generic, false);
                        }
                    }
                });
    }

    // ===================== Sair =====================

    /** Sai do Firebase e esquece a conta Google escolhida (a próxima vez pergunta de novo). */
    public static void signOut(Context ctx) {
        FirebaseAuth auth = CloudConfig.auth(ctx);
        if (auth == null) return;
        auth.signOut();
        CredentialManager.create(ctx).clearCredentialStateAsync(new ClearCredentialStateRequest(),
                new CancellationSignal(), ContextCompat.getMainExecutor(ctx),
                new CredentialManagerCallback<Void, ClearCredentialException>() {
                    @Override
                    public void onResult(Void result) {
                        // nada a fazer
                    }

                    @Override
                    public void onError(@NonNull ClearCredentialException e) {
                        AppLog.e("Falha ao limpar a credencial", e);
                    }
                });
    }

    private static void deliver(Task<AuthResult> task, Callback cb) {
        task.addOnCompleteListener(t -> cb.done(t.isSuccessful() ? 0 : messageFor(t.getException()), false));
    }
}
