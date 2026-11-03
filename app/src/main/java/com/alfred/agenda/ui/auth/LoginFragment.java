package com.alfred.agenda.ui.auth;

import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.alfred.agenda.MainActivity;
import com.alfred.agenda.R;
import com.alfred.agenda.account.AccountStore;
import com.alfred.agenda.cloud.CloudAuth;
import com.alfred.agenda.cloud.CloudConfig;
import com.alfred.agenda.data.CloudSync;
import com.alfred.agenda.util.Honorific;
import com.alfred.agenda.util.UiUtils;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputLayout;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.FirebaseUserMetadata;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Login e criação de conta.
 *  - Com Firebase (projeto real ou emuladores locais): e-mail e senha, ou conta Google.
 *    Os dados passam a ficar na conta, sincronizados com a versão web.
 *  - Sem Firebase (versão sem google-services.json): contas locais (AccountStore), só neste
 *    celular. O hash da senha é lento de propósito, então roda numa thread separada.
 */
public class LoginFragment extends Fragment {

    private static final String STATE_REGISTER = "register_mode";

    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());

    private boolean cloud;
    private boolean registerMode;
    private boolean busy;

    private TextInputLayout usernameLayout;
    private TextInputLayout passwordLayout;
    private TextInputLayout confirmLayout;
    private EditText username;
    private EditText password;
    private EditText confirm;
    private MaterialButton submit;
    private MaterialButton switchMode;
    private MaterialButton forgot;
    private MaterialButton google;
    private TextView subtitle;
    private View progress;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_login, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        cloud = CloudConfig.isAvailable(requireContext());
        usernameLayout = view.findViewById(R.id.layout_username);
        passwordLayout = view.findViewById(R.id.layout_password);
        confirmLayout = view.findViewById(R.id.layout_confirm);
        username = view.findViewById(R.id.input_username);
        password = view.findViewById(R.id.input_password);
        confirm = view.findViewById(R.id.input_confirm);
        submit = view.findViewById(R.id.button_submit);
        switchMode = view.findViewById(R.id.button_switch);
        forgot = view.findViewById(R.id.button_forgot);
        google = view.findViewById(R.id.button_google);
        subtitle = view.findViewById(R.id.login_subtitle);
        progress = view.findViewById(R.id.progress);

        if (cloud) {
            // O campo de usuário vira e-mail
            usernameLayout.setHint(R.string.auth_email);
            usernameLayout.setStartIconDrawable(R.drawable.ic_mail);
            username.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS);
            username.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(254)});
            username.setAutofillHints(View.AUTOFILL_HINT_EMAIL_ADDRESS);
            ((TextView) view.findViewById(R.id.login_note)).setText(CloudConfig.isUsingEmulator()
                    ? R.string.auth_emulator_note : R.string.auth_cloud_note);
            boolean withGoogle = CloudAuth.isGoogleAvailable(requireContext());
            view.findViewById(R.id.group_google).setVisibility(withGoogle ? View.VISIBLE : View.GONE);
            google.setOnClickListener(v -> signInWithGoogle());
            forgot.setOnClickListener(v -> resetPassword());
        }

        registerMode = savedInstanceState != null && savedInstanceState.getBoolean(STATE_REGISTER);
        applyMode();

        submit.setOnClickListener(v -> submit());
        switchMode.setOnClickListener(v -> {
            registerMode = !registerMode;
            clearErrors();
            applyMode();
        });
        TextView.OnEditorActionListener done = (v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                submit();
                return true;
            }
            return false;
        };
        password.setOnEditorActionListener(done);
        confirm.setOnEditorActionListener(done);
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putBoolean(STATE_REGISTER, registerMode);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        worker.shutdown();
    }

    private int minPassword() {
        return cloud ? CloudAuth.MIN_PASSWORD : AccountStore.MIN_PASSWORD;
    }

    private void applyMode() {
        confirmLayout.setVisibility(registerMode ? View.VISIBLE : View.GONE);
        password.setImeOptions(registerMode ? EditorInfo.IME_ACTION_NEXT : EditorInfo.IME_ACTION_DONE);
        submit.setText(registerMode ? R.string.auth_register : R.string.auth_login);
        switchMode.setText(registerMode ? R.string.auth_switch_to_login : R.string.auth_switch_to_register);
        if (cloud) {
            subtitle.setText(registerMode ? R.string.auth_cloud_register_subtitle : R.string.auth_cloud_login_subtitle);
            forgot.setVisibility(registerMode ? View.GONE : View.VISIBLE);
            usernameLayout.setHelperText(null);
        } else {
            subtitle.setText(registerMode ? R.string.auth_register_subtitle : R.string.auth_login_subtitle);
            usernameLayout.setHelperText(registerMode ? getString(R.string.auth_username_rule) : null);
        }
        passwordLayout.setHelperText(registerMode ? getString(R.string.auth_password_rule, minPassword()) : null);
    }

    private void clearErrors() {
        usernameLayout.setError(null);
        passwordLayout.setError(null);
        confirmLayout.setError(null);
    }

    private void submit() {
        if (busy) return;
        clearErrors();
        String user = UiUtils.text(username);
        String pass = password.getText() == null ? "" : password.getText().toString();

        // Validação local antes de chamar o servidor (ou o hash lento)
        if (user.isEmpty()) {
            usernameLayout.setError(getString(R.string.auth_error_required));
            return;
        }
        if (cloud && CloudAuth.validateEmail(user) != 0) {
            usernameLayout.setError(getString(CloudAuth.validateEmail(user)));
            return;
        }
        if (pass.isEmpty()) {
            passwordLayout.setError(getString(R.string.auth_error_required));
            return;
        }
        if (registerMode) {
            int userError = cloud ? 0 : AccountStore.validateUsername(user);
            if (userError != 0) {
                usernameLayout.setError(getString(userError));
                return;
            }
            int passError = cloud ? CloudAuth.validatePassword(pass) : AccountStore.validatePassword(pass);
            if (passError != 0) {
                passwordLayout.setError(getString(passError, minPassword()));
                return;
            }
            String again = confirm.getText() == null ? "" : confirm.getText().toString();
            if (!again.equals(pass)) {
                confirmLayout.setError(getString(R.string.auth_error_mismatch));
                return;
            }
        }

        setBusy(true);
        if (cloud) {
            boolean newAccount = registerMode;
            CloudAuth.Callback cb = (errorRes, cancelled) -> onCloudResult(errorRes, newAccount);
            if (registerMode) CloudAuth.register(requireContext(), user, pass, cb);
            else CloudAuth.signIn(requireContext(), user, pass, cb);
            return;
        }
        Context app = requireContext().getApplicationContext();
        boolean register = registerMode;
        worker.execute(() -> {
            int errorRes = 0;
            try {
                if (register) AccountStore.register(app, user, pass);
                else AccountStore.login(app, user, pass);
            } catch (AccountStore.AccountException e) {
                errorRes = e.messageRes;
            } catch (RuntimeException e) {
                errorRes = R.string.state_error_title;
            }
            int result = errorRes;
            main.post(() -> onResult(result));
        });
    }

    private void signInWithGoogle() {
        if (busy) return;
        clearErrors();
        setBusy(true);
        CloudAuth.signInWithGoogle(requireActivity(), (errorRes, cancelled) -> {
            if (cancelled) {
                if (isAdded()) setBusy(false);
                return;
            }
            onCloudResult(errorRes, false); // conta Google nova: descoberta pelos metadados abaixo
        });
    }

    private void resetPassword() {
        if (busy) return;
        clearErrors();
        String email = UiUtils.text(username);
        int error = CloudAuth.validateEmail(email);
        if (error != 0) {
            usernameLayout.setError(getString(error));
            username.requestFocus();
            return;
        }
        setBusy(true);
        CloudAuth.resetPassword(requireContext(), email, (errorRes, cancelled) -> {
            if (!isAdded()) return;
            setBusy(false);
            if (errorRes == 0) {
                // Ainda sem perfil: o tratamento sai da frase ("Se houver..., enviei o link...")
                UiUtils.toast(requireContext(), Honorific.apply(getString(R.string.auth_reset_sent),
                        Honorific.FORM_NAME, ""));
            } else {
                usernameLayout.setError(getString(errorRes));
            }
        });
    }

    /** Firebase respondeu: liga a conta da nuvem a este aparelho e entra. */
    private void onCloudResult(int errorRes, boolean newAccount) {
        if (!isAdded()) return;
        if (errorRes == 0) {
            FirebaseUser user = CloudAuth.currentUser(requireContext());
            if (user == null) {
                errorRes = R.string.auth_error_generic;
            } else {
                // Primeiro login (criação e último acesso no mesmo instante) também conta como conta nova
                FirebaseUserMetadata meta = user.getMetadata();
                boolean fresh = meta != null
                        && Math.abs(meta.getLastSignInTimestamp() - meta.getCreationTimestamp()) < 5_000;
                AccountStore.linkCloudUser(requireContext(), user.getUid(), user.getEmail(), newAccount || fresh);
                // Perfil da nuvem primeiro: quem já tem conta não refaz o onboarding num aparelho novo
                setBusy(true);
                CloudSync.fetchProfile(requireContext(), () -> onResult(0));
                return;
            }
        }
        onResult(errorRes);
    }

    private void onResult(int errorRes) {
        if (!isAdded()) return;
        setBusy(false);
        if (errorRes == 0) {
            password.setText(null);
            confirm.setText(null);
            ((MainActivity) requireActivity()).onLoggedIn();
            return;
        }
        if (errorRes == R.string.auth_error_taken || errorRes == R.string.auth_error_username
                || errorRes == R.string.auth_error_email || errorRes == R.string.auth_error_email_taken) {
            usernameLayout.setError(getString(errorRes));
        } else if (errorRes == R.string.auth_error_password || errorRes == R.string.auth_error_cloud_password) {
            passwordLayout.setError(getString(errorRes, minPassword()));
        } else {
            passwordLayout.setError(getString(errorRes));
        }
    }

    private void setBusy(boolean value) {
        busy = value;
        progress.setVisibility(value ? View.VISIBLE : View.INVISIBLE);
        submit.setEnabled(!value);
        switchMode.setEnabled(!value);
        forgot.setEnabled(!value);
        google.setEnabled(!value);
    }
}
