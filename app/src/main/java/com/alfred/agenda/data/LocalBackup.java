package com.alfred.agenda.data;

import android.content.Context;
import android.database.Cursor;

import androidx.annotation.Nullable;
import androidx.sqlite.db.SupportSQLiteDatabase;

import com.alfred.agenda.account.AccountStore;
import com.alfred.agenda.account.UserProfile;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.time.Instant;

/**
 * Backup local de TODOS os dados da conta logada, num único JSON legível:
 * cada tabela do banco (linha por linha, todas as colunas), o perfil e o plano alimentar.
 *
 * Serve de rede de segurança antes de migrar para a nuvem e é a mesma fonte usada na
 * migração. As tabelas são lidas de forma genérica (SELECT *), então uma coluna nova
 * entra no backup sem precisar mexer aqui; só uma tabela nova precisa entrar em TABLES.
 *
 * O arquivo NÃO é cifrado (é para a usuária guardar onde quiser); a tela avisa isso.
 */
public final class LocalBackup {

    /** Versão do formato do arquivo (muda se a estrutura do JSON mudar). */
    public static final int FORMAT = 1;

    /** Todas as tabelas do banco (versão 8), na ordem pais -> filhos. */
    static final String[] TABLES = {
            "tasks", "tags", "task_tags",
            "meals", "meal_items", "meal_logs",
            "habits", "habit_logs", "water_logs",
            "study_topics", "study_modules", "study_subjects", "study_logs"
    };

    private LocalBackup() {
    }

    /** Monta o backup da conta logada, lendo o banco. Chame fora da thread principal. */
    static JSONObject build(Context ctx, AppDatabase db, @Nullable String appVersion) throws JSONException {
        return build(db, AccountStore.currentUserId(ctx), AccountStore.currentUsername(ctx), UserProfile.current(ctx),
                NutritionPlan.get(ctx), appVersion);
    }

    /**
     * Backup de uma conta qualquer deste aparelho (também usado na migração de contas antigas).
     *
     * @param accountId número da conta neste aparelho: com ele, importar o arquivo e migrar a
     *                  conta geram os mesmos ids na nuvem (não duplica)
     */
    static JSONObject build(AppDatabase db, int accountId, String username, UserProfile p, NutritionPlan plan,
                            @Nullable String appVersion) throws JSONException {
        JSONObject root = new JSONObject();
        root.put("app", "Alfred");
        root.put("formato", FORMAT);
        root.put("versaoApp", appVersion == null ? "" : appVersion);
        root.put("versaoBanco", db.getOpenHelper().getReadableDatabase().getVersion());
        root.put("exportadoEm", Instant.now().toString());
        root.put("usuario", username);
        root.put("contaId", accountId);

        root.put("perfil", new JSONObject()
                .put("nome", p.name)
                .put("tratamento", p.form)
                .put("pesoKg", (double) p.weightKg)
                .put("avatar", p.avatar)
                .put("onboarding", p.onboarded));

        root.put("planoAlimentar", plan.isImported() ? plan.toJson() : JSONObject.NULL);

        JSONObject tables = new JSONObject();
        SupportSQLiteDatabase sql = db.getOpenHelper().getReadableDatabase();
        for (String table : TABLES) tables.put(table, readTable(sql, table));
        root.put("tabelas", tables);
        return root;
    }

    /** Todas as linhas da tabela, cada uma como objeto {coluna: valor}. */
    private static JSONArray readTable(SupportSQLiteDatabase sql, String table) throws JSONException {
        JSONArray rows = new JSONArray();
        try (Cursor c = sql.query("SELECT * FROM `" + table + "`")) {
            String[] cols = c.getColumnNames();
            while (c.moveToNext()) {
                JSONObject row = new JSONObject();
                for (int i = 0; i < cols.length; i++) {
                    switch (c.getType(i)) {
                        case Cursor.FIELD_TYPE_NULL: row.put(cols[i], JSONObject.NULL); break;
                        case Cursor.FIELD_TYPE_INTEGER: row.put(cols[i], c.getLong(i)); break;
                        case Cursor.FIELD_TYPE_FLOAT: row.put(cols[i], c.getDouble(i)); break;
                        default: row.put(cols[i], c.getString(i)); break;
                    }
                }
                rows.put(row);
            }
        }
        return rows;
    }

    /** Nome sugerido para o arquivo: alfred_backup_<usuario>_<data>.json */
    public static String suggestedFileName(String username, String isoDate) {
        String user = username.replaceAll("[^A-Za-z0-9_.-]", "");
        return "alfred_backup_" + (user.isEmpty() ? "conta" : user) + "_" + isoDate + ".json";
    }
}
