package com.alfred.agenda.ui.auth;

import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
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
import com.alfred.agenda.util.UiUtils;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputLayout;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Login e criação de conta. As contas são locais (AccountStore): ficam só neste celular,
 * cada uma com os próprios dados. O hash da senha é lento de propósito, então roda
 * numa thread separada enquanto a barra de progresso aparece.
 */
public class LoginFragment extends Fragment {

    private static final String STATE_REGISTER = "register_mode";

    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());

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
        usernameLayout = view.findViewById(R.id.layout_username);
        passwordLayout = view.findViewById(R.id.layout_password);
        confirmLayout = view.findViewById(R.id.layout_confirm);
        username = view.findViewById(R.id.input_username);
        password = view.findViewById(R.id.input_password);
        confirm = view.findViewById(R.id.input_confirm);
        submit = view.findViewById(R.id.button_submit);
        switchMode = view.findViewById(R.id.button_switch);
        subtitle = view.findViewById(R.id.login_subtitle);
        progress = view.findViewById(R.id.progress);

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

    private void applyMode() {
        confirmLayout.setVisibility(registerMode ? View.VISIBLE : View.GONE);
        password.setImeOptions(registerMode ? EditorInfo.IME_ACTION_NEXT : EditorInfo.IME_ACTION_DONE);
        submit.setText(registerMode ? R.string.auth_register : R.string.auth_login);
        switchMode.setText(registerMode ? R.string.auth_switch_to_login : R.string.auth_switch_to_register);
        subtitle.setText(registerMode ? R.string.auth_register_subtitle : R.string.auth_login_subtitle);
        passwordLayout.setHelperText(registerMode
                ? getString(R.string.auth_password_rule, AccountStore.MIN_PASSWORD) : null);
        usernameLayout.setHelperText(registerMode ? getString(R.string.auth_username_rule) : null);
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

        // Validação local antes de gastar tempo com o hash
        if (user.isEmpty()) {
            usernameLayout.setError(getString(R.string.auth_error_required));
            return;
        }
        if (pass.isEmpty()) {
            passwordLayout.setError(getString(R.string.auth_error_required));
            return;
        }
        if (registerMode) {
            int userError = AccountStore.validateUsername(user);
            if (userError != 0) {
                usernameLayout.setError(getString(userError));
                return;
            }
            int passError = AccountStore.validatePassword(pass);
            if (passError != 0) {
                passwordLayout.setError(getString(passError, AccountStore.MIN_PASSWORD));
                return;
            }
            String again = confirm.getText() == null ? "" : confirm.getText().toString();
            if (!again.equals(pass)) {
                confirmLayout.setError(getString(R.string.auth_error_mismatch));
                return;
            }
        }

        setBusy(true);
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

    private void onResult(int errorRes) {
        if (!isAdded()) return;
        setBusy(false);
        if (errorRes == 0) {
            password.setText(null);
            confirm.setText(null);
            ((MainActivity) requireActivity()).onLoggedIn();
            return;
        }
        if (errorRes == R.string.auth_error_taken || errorRes == R.string.auth_error_username) {
            usernameLayout.setError(getString(errorRes));
        } else if (errorRes == R.string.auth_error_password) {
            passwordLayout.setError(getString(errorRes, AccountStore.MIN_PASSWORD));
        } else {
            passwordLayout.setError(getString(errorRes));
        }
    }

    private void setBusy(boolean value) {
        busy = value;
        progress.setVisibility(value ? View.VISIBLE : View.INVISIBLE);
        submit.setEnabled(!value);
        switchMode.setEnabled(!value);
    }
}
