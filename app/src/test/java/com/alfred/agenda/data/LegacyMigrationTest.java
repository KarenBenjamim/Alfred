package com.alfred.agenda.data;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.alfred.agenda.cloud.CloudSchema;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/** Migração de conta antiga: o backup vira os documentos da estrutura aprovada, sem duplicar. */
public class LegacyMigrationTest {

    private static final long DAY = LocalDate.of(2026, 11, 3).toEpochDay();

    private static JSONObject row(Object... kv) throws Exception {
        JSONObject o = new JSONObject();
        for (int i = 0; i < kv.length; i += 2) o.put((String) kv[i], kv[i + 1]);
        return o;
    }

    /** Backup no formato do LocalBackup com um pouco de cada tabela. */
    private static JSONObject backup() throws Exception {
        JSONObject t = new JSONObject();
        t.put("tags", new JSONArray().put(row("id", 1, "name", "Casa", "colorIndex", 2)));
        t.put("tasks", new JSONArray().put(row("id", 5, "title", "Pagar luz", "description", "",
                "dueEpochDay", DAY, "priority", 2, "status", 0, "category", 1, "period", 0)));
        t.put("task_tags", new JSONArray().put(row("taskId", 5, "tagId", 1)));
        t.put("meals", new JSONArray().put(row("id", 3, "name", "Almoço", "timeMinutes", 720,
                "notifyEnabled", 1, "notes", "")));
        t.put("meal_items", new JSONArray()
                .put(row("id", 9, "mealId", 3, "position", 1, "description", "Feijão", "quantity", "1 concha",
                        "substitutions", "", "groupNumber", 4, "groupPortions", "1"))
                .put(row("id", 8, "mealId", 3, "position", 0, "description", "Arroz", "quantity", "2 col.",
                        "substitutions", "", "groupNumber", 1, "groupPortions", "1")));
        t.put("meal_logs", new JSONArray().put(row("mealId", 3, "epochDay", DAY)));
        t.put("habits", new JSONArray()
                .put(row("id", 1, "name", "Beber 2 litros de água", "position", 0))
                .put(row("id", 2, "name", "Ler", "position", 1)));
        t.put("habit_logs", new JSONArray()
                .put(row("habitId", 1, "epochDay", DAY))
                .put(row("habitId", 2, "epochDay", DAY - 1)));
        t.put("water_logs", new JSONArray().put(row("epochDay", DAY, "ml", 1800)));
        t.put("study_topics", new JSONArray().put(row("id", 1, "name", "Inglês", "position", 0, "colorIndex", 3)));
        t.put("study_modules", new JSONArray().put(row("id", 1, "topicId", 1, "name", "Geral", "position", 0)));
        t.put("study_subjects", new JSONArray().put(row("id", 4, "topicId", 1, "moduleId", 1, "name", "Verbos",
                "position", 0, "runningSince", 0, "completed", 1)));
        t.put("study_logs", new JSONArray().put(row("subjectId", 4, "epochDay", DAY, "seconds", 86)));
        JSONObject root = new JSONObject();
        root.put("tabelas", t);
        root.put("perfil", row("nome", "Milainy", "tratamento", 0, "pesoKg", 70.0, "avatar", 1, "onboarding", true));
        root.put("planoAlimentar", JSONObject.NULL);
        return root;
    }

    private static LegacyMigration.Write find(List<LegacyMigration.Write> ws, String col) {
        for (LegacyMigration.Write w : ws) if (w.collection.equals(col)) return w;
        return null;
    }

    @Test
    public void everyTableBecomesItsDocumentsWithReferencesRemapped() throws Exception {
        LegacyMigration.Result r = new LegacyMigration.Result();
        List<LegacyMigration.Write> ws = LegacyMigration.plan(backup(), "u1", new LegacyMigration.Existing(), r);
        assertEquals(1, r.tasks);
        assertEquals(1, r.meals);
        assertEquals(2, r.habits);
        assertEquals(1, r.subjects);
        assertEquals(1, r.waterDays);

        long tagId = LegacyMigration.stableId("u1", "tags", 1);
        LegacyMigration.Write task = find(ws, CloudSchema.TASKS);
        assertEquals(CloudSchema.docId(LegacyMigration.stableId("u1", "tasks", 5)), task.docId);
        assertEquals(Collections.singletonList(tagId), task.data.get("tags")); // tag com o id novo
        assertEquals("pessoal", task.data.get("categoria"));

        LegacyMigration.Write meal = find(ws, CloudSchema.MEALS);
        List<?> itens = (List<?>) meal.data.get("itens");
        assertEquals("Arroz", ((Map<?, ?>) itens.get(0)).get("alimento")); // ordem pela posição
        assertTrue(meal.merge); // merge + arrayUnion: repetir não apaga marcações feitas depois
        assertNotNull(meal.data.get(CloudSchema.MEAL_DONE));

        LegacyMigration.Write subject = find(ws, CloudSchema.SUBJECTS);
        assertEquals(LegacyMigration.stableId("u1", "study_topics", 1), subject.data.get("materiaId"));
        assertEquals(LegacyMigration.stableId("u1", "study_modules", 1), subject.data.get("moduloId"));
        assertEquals(86L, ((Map<?, ?>) subject.data.get(CloudSchema.SUBJECT_TIME)).get("2026-11-03"));

        LegacyMigration.Write water = find(ws, CloudSchema.WATER);
        assertEquals("2026-11-03", water.docId);
        assertEquals(1800, water.data.get(CloudSchema.WATER_ML));

        LegacyMigration.Write profile = find(ws, "");
        assertTrue(profile.merge);
        assertEquals("Milainy", profile.data.get("nome"));
        assertNull(find(ws, CloudSchema.PLAN)); // sem plano importado, nada é enviado
    }

    @Test
    public void runningTwiceProducesTheSameDocuments() throws Exception {
        List<LegacyMigration.Write> a = LegacyMigration.plan(backup(), "u1", new LegacyMigration.Existing(), new LegacyMigration.Result());
        List<LegacyMigration.Write> b = LegacyMigration.plan(backup(), "u1", new LegacyMigration.Existing(), new LegacyMigration.Result());
        assertEquals(a.size(), b.size());
        for (int i = 0; i < a.size(); i++) {
            assertEquals(a.get(i).collection, b.get(i).collection);
            assertEquals(a.get(i).docId, b.get(i).docId); // repetir regrava, não duplica
        }
    }

    @Test
    public void differentAccountsNeverShareIds() {
        assertNotEquals(LegacyMigration.stableId("u1", "tasks", 5), LegacyMigration.stableId("u2", "tasks", 5));
        assertNotEquals(LegacyMigration.stableId("u1", "tasks", 5), LegacyMigration.stableId("u1", "meals", 5));
        long id = LegacyMigration.stableId("u1", "tasks", 5);
        assertTrue(id > 0 && id <= (1L << 53) - 1); // seguro para o JavaScript
    }

    @Test
    public void habitWithSameNameInTheCloudGetsTheOldDaysInsteadOfADuplicate() throws Exception {
        LegacyMigration.Existing existing = new LegacyMigration.Existing();
        existing.habits.put(LegacyMigration.normalize("  beber 2 LITROS de água"), 777L);
        LegacyMigration.Result r = new LegacyMigration.Result();
        List<LegacyMigration.Write> ws = LegacyMigration.plan(backup(), "u1", existing, r);
        assertEquals(1, r.habits); // só o "Ler" é novo
        LegacyMigration.Write merged = null;
        for (LegacyMigration.Write w : ws) if (w.docId.equals("777")) merged = w;
        assertNotNull(merged);
        assertTrue(merged.merge); // arrayUnion nas marcações do hábito que já existia
        assertTrue(merged.data.containsKey(CloudSchema.HABIT_DONE));
    }

    @Test
    public void whatTheCloudAlreadyHasIsNotOverwritten() throws Exception {
        JSONObject b = backup();
        b.put("planoAlimentar", new JSONObject().put("source", "plano.xlsx"));
        LegacyMigration.Existing existing = new LegacyMigration.Existing();
        existing.profile = true;          // a conta da nuvem já tem perfil
        existing.plan = true;             // e plano importado
        existing.waterByDay.put("2026-11-03", 2500); // e mais água hoje do que a conta antiga
        LegacyMigration.Result r = new LegacyMigration.Result();
        List<LegacyMigration.Write> ws = LegacyMigration.plan(b, "u1", existing, r);
        assertNull(find(ws, ""));
        assertNull(find(ws, CloudSchema.PLAN));
        assertNull(find(ws, CloudSchema.WATER));
        assertEquals(0, r.waterDays);

        existing.waterByDay.put("2026-11-03", 500); // menos água na nuvem: o total antigo (maior) vale
        assertEquals(1800, find(LegacyMigration.plan(b, "u1", existing, new LegacyMigration.Result()),
                CloudSchema.WATER).data.get(CloudSchema.WATER_ML));
    }

    @Test
    public void forgottenRunningTimerArrivesPaused() throws Exception {
        JSONObject b = backup();
        b.getJSONObject("tabelas").getJSONArray("study_subjects").getJSONObject(0).put("runningSince", 1_700_000_000_000L);
        LegacyMigration.Write s = find(LegacyMigration.plan(b, "u1", new LegacyMigration.Existing(),
                new LegacyMigration.Result()), CloudSchema.SUBJECTS);
        assertEquals(0L, s.data.get(CloudSchema.SUBJECT_RUNNING));
    }

    @Test
    public void profileWithoutOnboardingIsNotSent() throws Exception {
        JSONObject b = backup();
        b.getJSONObject("perfil").put("onboarding", false);
        assertNull(find(LegacyMigration.plan(b, "u1", new LegacyMigration.Existing(), new LegacyMigration.Result()), ""));
        assertFalse(LegacyMigration.normalize(" A ").isEmpty());
    }
}
