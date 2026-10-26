package com.alfred.agenda.account;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.StringRes;

import com.alfred.agenda.R;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Contas locais do Alfred e a sessão atual. Não há servidor: as contas existem só neste celular.
 *
 *  - Cada conta tem usuário, sal e hash da senha (PasswordHasher); a senha nunca é gravada.
 *  - Cada conta tem o PRÓPRIO banco de dados (tarefas, dieta, hábitos, estudos): ao trocar de
 *    usuário, ninguém vê os dados do outro. A 1ª conta criada fica com o banco que já existia
 *    no celular ("alfred.db"), para quem atualizou o app não perder nada.
 *  - A sessão fica aberta até o usuário tocar em "Sair" no perfil.
 *
 * register() e login() são lentos (hash da senha): chame fora da thread principal.
 */
public final class AccountStore {

    private static final String PREFS = "alfred_accounts";
    private static final String KEY_NEXT_ID = "next_id";
    private static final String KEY_CURRENT = "current_user";

    private static final Pattern USERNAME = Pattern.compile("[A-Za-z0-9._-]{3,30}");
    public static final int MIN_PASSWORD = 4;
    public static final int MAX_PASSWORD = 64;

    /** Erro de cadastro/login com a mensagem que a tela deve mostrar. */
    public static final class AccountException extends Exception {
        @StringRes
        public final int messageRes;

        AccountException(@StringRes int messageRes) {
            super("account error " + messageRes);
            this.messageRes = messageRes;
        }
    }

    private AccountStore() {
    }

    private static SharedPreferences prefs(Context ctx) {
        return ctx.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    private static String indexKey(String username) {
        return "index_" + username.toLowerCase(Locale.ROOT);
    }

    /** Valida o formato do usuário (devolve 0 se estiver ok, senão a mensagem de erro). */
    @StringRes
    public static int validateUsername(String username) {
        return USERNAME.matcher(username).matches() ? 0 : R.string.auth_error_username;
    }

    @StringRes
    public static int validatePassword(String password) {
        return password.length() >= MIN_PASSWORD && password.length() <= MAX_PASSWORD
                ? 0 : R.string.auth_error_password;
    }

    /** Cria a conta e já entra nela. Devolve o id do usuário. */
    public static synchronized int register(Context ctx, String username, String password) throws AccountException {
        String user = username.trim();
        if (validateUsername(user) != 0) throw new AccountException(validateUsername(user));
        if (validatePassword(password) != 0) throw new AccountException(validatePassword(password));
        SharedPreferences p = prefs(ctx);
        if (p.getInt(indexKey(user), 0) != 0) throw new AccountException(R.string.auth_error_taken);

        int id = p.getInt(KEY_NEXT_ID, 1);
        String salt = PasswordHasher.newSalt();
        String hash = PasswordHasher.hash(password, salt);
        boolean ok = p.edit()
                .putInt(KEY_NEXT_ID, id + 1)
                .putInt(indexKey(user), id)
                .putString("user_" + id + "_name", user)
                .putString("user_" + id + "_salt", salt)
                .putString("user_" + id + "_hash", hash)
                .putInt("user_" + id + "_iter", PasswordHasher.ITERATIONS)
                .putInt(KEY_CURRENT, id)
                .commit();
        if (!ok) throw new AccountException(R.string.state_error_title);
        return id;
    }

    /** Confere usuário e senha e abre a sessão. Devolve o id do usuário. */
    public static int login(Context ctx, String username, String password) throws AccountException {
        SharedPreferences p = prefs(ctx);
        int id = p.getInt(indexKey(username.trim()), 0);
        String salt = p.getString("user_" + id + "_salt", null);
        String hash = p.getString("user_" + id + "_hash", null);
        int iterations = p.getInt("user_" + id + "_iter", PasswordHasher.ITERATIONS);
        // Mesma mensagem para usuário inexistente e senha errada (não revela quais contas existem)
        if (id == 0 || salt == null || hash == null || !PasswordHasher.matches(password, salt, hash, iterations)) {
            throw new AccountException(R.string.auth_error_wrong);
        }
        p.edit().putInt(KEY_CURRENT, id).apply(); // apply já vale na memória; o disco grava em seguida
        return id;
    }

    public static void logout(Context ctx) {
        prefs(ctx).edit().remove(KEY_CURRENT).apply();
    }

    public static boolean isLoggedIn(Context ctx) {
        return currentUserId(ctx) != 0;
    }

    /** 0 = ninguém logado. */
    public static int currentUserId(Context ctx) {
        return prefs(ctx).getInt(KEY_CURRENT, 0);
    }

    public static String currentUsername(Context ctx) {
        return prefs(ctx).getString("user_" + currentUserId(ctx) + "_name", "");
    }

    /** Arquivo do banco do usuário logado. A conta 1 herda o banco que já existia no celular. */
    public static String databaseName(Context ctx) {
        int id = currentUserId(ctx);
        if (id == 0) throw new IllegalStateException("Nenhum usuário logado");
        return id == 1 ? "alfred.db" : "alfred_u" + id + ".db";
    }
}
