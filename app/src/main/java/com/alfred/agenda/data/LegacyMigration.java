package com.alfred.agenda.data;

import android.content.Context;

import androidx.annotation.Nullable;

import com.alfred.agenda.account.AccountStore;
import com.alfred.agenda.account.UserProfile;
import com.alfred.agenda.cloud.CloudSchema;
import com.alfred.agenda.data.entity.Habit;
import com.alfred.agenda.data.entity.Meal;
import com.alfred.agenda.data.entity.MealItem;
import com.alfred.agenda.data.entity.StudyModule;
import com.alfred.agenda.data.entity.StudySubject;
import com.alfred.agenda.data.entity.StudyTopic;
import com.alfred.agenda.data.entity.Tag;
import com.alfred.agenda.data.entity.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.SetOptions;
import com.google.firebase.firestore.WriteBatch;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Envia, uma vez, os dados de uma conta local antiga (de antes da nuvem) para a conta da
 * nuvem logada. A fonte é o MESMO JSON do backup local (LocalBackup): o que vai para a
 * nuvem é exatamente o que o backup guarda.
 *
 * Os ids na nuvem são derivados de forma fixa da conta antiga e do id antigo (stableId):
 * se a conexão cair no meio e a migração for repetida, os mesmos documentos são regravados,
 * sem duplicar. Os dados locais antigos não são apagados.
 *
 * plan(...) é puro (testado em LegacyMigrationTest); run(...) envia e espera a confirmação.
 */
public final class LegacyMigration {

    /** Uma gravação no Firestore, relativa a usuarios/{uid} ("" = o próprio perfil). */
    static final class Write {
        final String collection;
        final String docId;
        final Map<String, Object> data;
        final boolean merge;

        Write(String collection, String docId, Map<String, Object> data, boolean merge) {
            this.collection = collection;
            this.docId = docId;
            this.data = data;
            this.merge = merge;
        }
    }

    /**
     * O que a conta da nuvem já tem (lido do espelho local), para a migração somar em vez de
     * sobrescrever: hábitos de mesmo nome, perfil, plano e água por dia.
     */
    static final class Existing {
        final Map<String, Long> habits = new HashMap<>();
        final Map<String, Integer> waterByDay = new HashMap<>();
        boolean profile;
        boolean plan;
    }

    /** Quantos itens foram enviados, para a mensagem final. */
    public static final class Result {
        public int tasks;
        public int meals;
        public int habits;
        public int subjects;
        public int waterDays;
    }

    /** O Firestore aceita até 500 operações por lote; sobra margem. */
    private static final int BATCH = 400;
    private static final long CONFIRM_SECONDS = 60;

    private LegacyMigration() {
    }

    // ===================== Envio =====================

    /**
     * Lê a conta antiga, envia para usuarios/{uid} e espera o servidor confirmar.
     * Só então marca a conta como migrada. Chame fora da thread principal.
     *
     * @param existingHabits hábitos que a conta da nuvem já tem (nome -> id): os de mesmo nome
     *                       recebem as marcações antigas em vez de virar um hábito repetido
     * @throws Exception sem conexão, tempo esgotado ou recusa das regras
     */
    static Result run(Context ctx, int legacyId, String uid, Existing existing,
                      @Nullable String appVersion) throws Exception {
        AppDatabase legacyDb = AppDatabase.openForAccount(ctx, legacyId);
        JSONObject backup;
        try {
            String username = "";
            for (AccountStore.LegacyAccount a : AccountStore.pendingLegacyAccounts(ctx)) {
                if (a.id == legacyId) username = a.username;
            }
            backup = LocalBackup.build(legacyDb, legacyId, username, UserProfile.forAccount(ctx, legacyId),
                    NutritionPlan.forAccount(ctx, legacyId), appVersion);
        } finally {
            legacyDb.close();
        }
        Result result = new Result();
        send(uid, plan(backup, scopeOf(backup), existing, result));
        AccountStore.markMigrated(ctx, legacyId, uid);
        return result;
    }

    /**
     * Importa um arquivo de backup (exportado pelo app) para a conta da nuvem logada.
     * O mesmo arquivo importado duas vezes regrava os mesmos documentos (não duplica).
     * Chame fora da thread principal.
     */
    static Result importBackup(JSONObject backup, String uid, Existing existing) throws Exception {
        Result result = new Result();
        send(uid, plan(backup, scopeOf(backup), existing, result));
        return result;
    }

    /** Envia as gravações em lotes e espera o servidor confirmar cada um. */
    private static void send(String uid, List<Write> writes) throws Exception {
        DocumentReference user = FirebaseFirestore.getInstance().collection(CloudSchema.USERS).document(uid);
        for (int i = 0; i < writes.size(); i += BATCH) {
            WriteBatch batch = user.getFirestore().batch();
            for (Write w : writes.subList(i, Math.min(writes.size(), i + BATCH))) {
                DocumentReference ref = w.collection.isEmpty() ? user : user.collection(w.collection).document(w.docId);
                if (w.merge) batch.set(ref, w.data, SetOptions.merge());
                else batch.set(ref, w.data);
            }
            // Offline, o commit só confirma quando a internet voltar: aqui esperamos no máximo 1 min
            Tasks.await(batch.commit(), CONFIRM_SECONDS, TimeUnit.SECONDS);
        }
    }

    // ===================== Validação do arquivo (pura) =====================

    /** Arquivo de backup inválido (não é do Alfred, formato novo demais ou danificado). */
    static final class InvalidBackup extends Exception {
        InvalidBackup(String message) {
            super(message);
        }
    }

    /** Confere se o JSON é um backup do Alfred que esta versão entende. */
    static void validate(JSONObject backup) throws InvalidBackup {
        if (!"Alfred".equals(backup.optString("app"))) throw new InvalidBackup("não é um backup do Alfred");
        int format = backup.optInt("formato", 0);
        if (format < 1 || format > LocalBackup.FORMAT) throw new InvalidBackup("formato " + format + " não suportado");
        if (backup.optJSONObject("tabelas") == null) throw new InvalidBackup("sem tabelas");
    }

    /**
     * Prefixo dos ids na nuvem: o mesmo para a migração da conta e para o backup dela
     * ("u" + número da conta). Backups sem o número (versões antigas) usam o usuário.
     */
    static String scopeOf(JSONObject backup) {
        int account = backup.optInt("contaId", 0);
        if (account > 0) return "u" + account;
        return "backup:" + backup.optString("usuario").trim().toLowerCase(Locale.ROOT);
    }

    // ===================== Montagem (pura) =====================

    /** Id fixo na nuvem para um registro antigo: o mesmo a cada tentativa. */
    static long stableId(String scope, String table, long oldId) {
        try {
            byte[] h = MessageDigest.getInstance("SHA-256")
                    .digest((scope + "|" + table + "|" + oldId).getBytes(StandardCharsets.UTF_8));
            long v = 0;
            for (int i = 0; i < 8; i++) v = (v << 8) | (h[i] & 0xFF);
            v &= (1L << 53) - 1;
            return v == 0 ? 1 : v;
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e); // SHA-256 existe em toda JVM/Android
        }
    }

    static List<Write> plan(JSONObject backup, String scope, Existing existing, Result result)
            throws JSONException {
        JSONObject t = backup.getJSONObject("tabelas");
        List<Write> out = new ArrayList<>();

        // ---- Perfil e plano
        JSONObject perfil = backup.optJSONObject("perfil");
        // Perfil e plano só sobem se a conta da nuvem ainda não tiver os dela (não sobrescreve)
        if (!existing.profile && perfil != null && perfil.optBoolean("onboarding")) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("nome", perfil.optString("nome"));
            m.put("tratamento", perfil.optInt("tratamento"));
            m.put("pesoKg", perfil.optDouble("pesoKg", 0));
            m.put("avatar", perfil.optInt("avatar"));
            m.put("onboarding", true);
            out.add(new Write("", "", m, true));
        }
        JSONObject plano = backup.optJSONObject("planoAlimentar");
        if (!existing.plan && plano != null) out.add(new Write(CloudSchema.PLAN, CloudSchema.PLAN_DOC, CloudSync.toMap(plano), false));

        // ---- Tags e tarefas
        Map<Long, Long> tagIds = new HashMap<>();
        for (JSONObject r : rows(t, "tags")) {
            Tag tag = new Tag();
            tag.id = stableId(scope, "tags", r.getLong("id"));
            tag.name = r.optString("name");
            tag.colorIndex = r.optInt("colorIndex");
            tagIds.put(r.getLong("id"), tag.id);
            out.add(new Write(CloudSchema.TAGS, CloudSchema.docId(tag.id), CloudSchema.tag(tag), false));
        }
        Map<Long, List<Long>> tagsOfTask = new HashMap<>();
        for (JSONObject r : rows(t, "task_tags")) {
            Long tag = tagIds.get(r.getLong("tagId"));
            if (tag != null) listOf(tagsOfTask, r.getLong("taskId")).add(tag);
        }
        for (JSONObject r : rows(t, "tasks")) {
            Task task = new Task();
            task.id = stableId(scope, "tasks", r.getLong("id"));
            task.title = r.optString("title");
            task.description = r.optString("description");
            task.dueEpochDay = r.getLong("dueEpochDay");
            task.priority = r.optInt("priority", Task.PRIORITY_MEDIUM);
            task.status = r.optInt("status", Task.STATUS_PENDING);
            task.category = r.optInt("category", Task.CATEGORY_WORK);
            task.period = r.optInt("period", Task.PERIOD_MORNING);
            List<Long> tags = tagsOfTask.get(r.getLong("id"));
            out.add(new Write(CloudSchema.TASKS, CloudSchema.docId(task.id),
                    CloudSchema.task(task, tags == null ? new ArrayList<>() : tags), false));
            result.tasks++;
        }

        // ---- Dieta
        Map<Long, List<MealItem>> itemsOf = new HashMap<>();
        for (JSONObject r : rows(t, "meal_items")) {
            MealItem it = new MealItem();
            it.position = r.optInt("position");
            it.description = r.optString("description");
            it.quantity = r.optString("quantity");
            it.substitutions = r.optString("substitutions");
            it.groupNumber = r.optInt("groupNumber");
            it.groupPortions = r.optString("groupPortions");
            listOf(itemsOf, r.getLong("mealId")).add(it);
        }
        Map<Long, List<String>> mealDone = days(rows(t, "meal_logs"), "mealId");
        for (JSONObject r : rows(t, "meals")) {
            long old = r.getLong("id");
            Meal meal = new Meal();
            meal.id = stableId(scope, "meals", old);
            meal.name = r.optString("name");
            meal.timeMinutes = r.optInt("timeMinutes", 8 * 60);
            meal.notifyEnabled = r.optInt("notifyEnabled", 1) != 0;
            meal.notes = r.optString("notes");
            List<MealItem> items = itemsOf.containsKey(old) ? itemsOf.get(old) : new ArrayList<>();
            items.sort((a, b) -> Integer.compare(a.position, b.position));
            Map<String, Object> m = CloudSchema.meal(meal, items);
            putDays(m, CloudSchema.MEAL_DONE, mealDone.get(old));
            out.add(new Write(CloudSchema.MEALS, CloudSchema.docId(meal.id), m, true));
            result.meals++;
        }

        // ---- Hábitos (o de mesmo nome já existente na nuvem recebe as marcações antigas)
        Map<Long, List<String>> habitDone = days(rows(t, "habit_logs"), "habitId");
        for (JSONObject r : rows(t, "habits")) {
            long old = r.getLong("id");
            List<String> done = orEmpty(habitDone.get(old));
            Long same = existing.habits.get(normalize(r.optString("name")));
            if (same != null) {
                if (!done.isEmpty()) {
                    Map<String, Object> m = new HashMap<>();
                    m.put(CloudSchema.HABIT_DONE, FieldValue.arrayUnion(done.toArray()));
                    out.add(new Write(CloudSchema.HABITS, CloudSchema.docId(same), m, true));
                }
                continue;
            }
            Habit h = new Habit();
            h.id = stableId(scope, "habits", old);
            h.name = r.optString("name");
            h.position = r.optInt("position");
            Map<String, Object> m = CloudSchema.habit(h);
            putDays(m, CloudSchema.HABIT_DONE, done);
            out.add(new Write(CloudSchema.HABITS, CloudSchema.docId(h.id), m, true));
            result.habits++;
        }

        // ---- Água: fica o maior total do dia (não troca o que já foi registrado na nuvem)
        for (JSONObject r : rows(t, "water_logs")) {
            String day = CloudSchema.day(r.getLong("epochDay"));
            Integer cloud = existing.waterByDay.get(day);
            if (cloud != null && cloud >= r.optInt("ml")) continue;
            Map<String, Object> m = new HashMap<>();
            m.put(CloudSchema.WATER_ML, r.optInt("ml"));
            out.add(new Write(CloudSchema.WATER, CloudSchema.day(r.getLong("epochDay")), m, false));
            result.waterDays++;
        }

        // ---- Estudos
        for (JSONObject r : rows(t, "study_topics")) {
            StudyTopic topic = new StudyTopic();
            topic.id = stableId(scope, "study_topics", r.getLong("id"));
            topic.name = r.optString("name");
            topic.position = r.optInt("position");
            topic.colorIndex = r.optInt("colorIndex");
            out.add(new Write(CloudSchema.TOPICS, CloudSchema.docId(topic.id), CloudSchema.topic(topic), false));
        }
        for (JSONObject r : rows(t, "study_modules")) {
            StudyModule mod = new StudyModule();
            mod.id = stableId(scope, "study_modules", r.getLong("id"));
            mod.topicId = stableId(scope, "study_topics", r.getLong("topicId"));
            mod.name = r.optString("name");
            mod.position = r.optInt("position");
            out.add(new Write(CloudSchema.MODULES, CloudSchema.docId(mod.id), CloudSchema.module(mod), false));
        }
        Map<Long, Map<String, Object>> timeOf = new HashMap<>();
        for (JSONObject r : rows(t, "study_logs")) {
            long seconds = r.optLong("seconds");
            if (seconds <= 0) continue;
            Map<String, Object> time = timeOf.get(r.getLong("subjectId"));
            if (time == null) {
                time = new HashMap<>();
                timeOf.put(r.getLong("subjectId"), time);
            }
            time.put(CloudSchema.day(r.getLong("epochDay")), seconds);
        }
        for (JSONObject r : rows(t, "study_subjects")) {
            long old = r.getLong("id");
            StudySubject s = new StudySubject();
            s.id = stableId(scope, "study_subjects", old);
            s.topicId = stableId(scope, "study_topics", r.getLong("topicId"));
            s.moduleId = r.isNull("moduleId") ? null : stableId(scope, "study_modules", r.getLong("moduleId"));
            s.name = r.optString("name");
            s.position = r.optInt("position");
            s.completed = r.optInt("completed") != 0;
            s.runningSince = 0; // cronômetro esquecido ligado na conta antiga não vira "rodando" na nuvem
            Map<String, Object> m = CloudSchema.subject(s);
            m.put(CloudSchema.SUBJECT_TIME, timeOf.containsKey(old) ? timeOf.get(old) : new HashMap<>());
            out.add(new Write(CloudSchema.SUBJECTS, CloudSchema.docId(s.id), m, true));
            result.subjects++;
        }
        return out;
    }

    /** Nome de hábito comparável ("Beber 2 litros de água" == "beber 2 litros de água "). */
    static String normalize(String name) {
        return name.trim().toLowerCase(Locale.ROOT);
    }

    private static List<JSONObject> rows(JSONObject tables, String name) throws JSONException {
        List<JSONObject> out = new ArrayList<>();
        JSONArray a = tables.optJSONArray(name);
        if (a == null) return out;
        for (int i = 0; i < a.length(); i++) out.add(a.getJSONObject(i));
        return out;
    }

    private static Map<Long, List<String>> days(List<JSONObject> logs, String parentKey) throws JSONException {
        Map<Long, List<String>> out = new HashMap<>();
        for (JSONObject r : logs) listOf(out, r.getLong(parentKey)).add(CloudSchema.day(r.getLong("epochDay")));
        return out;
    }

    private static <T> List<T> listOf(Map<Long, List<T>> map, long key) {
        List<T> list = map.get(key);
        if (list == null) {
            list = new ArrayList<>();
            map.put(key, list);
        }
        return list;
    }

    private static List<String> orEmpty(@Nullable List<String> list) {
        return list == null ? new ArrayList<>() : list;
    }

    /** Dias de "feito" somados aos da nuvem (arrayUnion): repetir não apaga marcações novas. */
    private static void putDays(Map<String, Object> m, String field, @Nullable List<String> days) {
        if (days != null && !days.isEmpty()) m.put(field, FieldValue.arrayUnion(days.toArray()));
    }
}
