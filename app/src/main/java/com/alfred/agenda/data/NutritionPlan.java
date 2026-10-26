package com.alfred.agenda.data;

import android.content.Context;

import androidx.annotation.Nullable;

import com.alfred.agenda.account.AccountStore;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Plano alimentar importado da planilha Excel da nutricionista (ver PlanImport).
 *
 * Fica num arquivo JSON privado do app, um por usuário ("plano_alimentar_u<id>.json").
 * Contém:
 *  - as refeições do plano (copiadas para a aba Dieta na importação, onde podem ser editadas);
 *  - a lista de substituição (grupos; só leitura, consultada pelos atalhos);
 *  - o ml de água por kg e as dicas da aba "Água", e as orientações.
 * Enquanto nada foi importado, isImported() é false e a aba Dieta pede a planilha.
 */
public final class NutritionPlan {

    private static volatile NutritionPlan instance;
    private static volatile int instanceUser;

    // ---------------- Modelos ----------------

    public static final class Food {
        public final String food;
        public final String measure;
        public final String qty;

        Food(String food, String measure, String qty) {
            this.food = food;
            this.measure = measure;
            this.qty = qty;
        }

        /** "2 unidades médias (100 g)" ou só a medida, se não houver quantidade. */
        public String amount() {
            if (qty.isEmpty()) return measure;
            if (measure.isEmpty()) return qty;
            return measure + " (" + qty + ")";
        }
    }

    public static final class Group {
        public final int number;
        public final String name;
        public final List<Food> foods;

        Group(int number, String name, List<Food> foods) {
            this.number = number;
            this.name = name;
            this.foods = foods;
        }
    }

    public static final class PlanItem {
        public final Food food;
        public final List<String> substitutions;
        public final int group;
        public final String portions;

        PlanItem(Food food, List<String> substitutions, int group, String portions) {
            this.food = food;
            this.substitutions = substitutions;
            this.group = group;
            this.portions = portions;
        }
    }

    public static final class PlanMeal {
        public final int timeMinutes;
        public final String name;
        public final String notes;
        public final List<PlanItem> items;

        PlanMeal(int timeMinutes, String name, String notes, List<PlanItem> items) {
            this.timeMinutes = timeMinutes;
            this.name = name;
            this.notes = notes;
            this.items = items;
        }

        /** "Lanche - Opção 2" é uma opção; só a 1ª opção de cada horário avisa. */
        public boolean isAlternativeOption() {
            return name.matches(".*Opção\\s*[2-9].*");
        }
    }

    public static final class Note {
        public final String topic;
        public final String text;

        Note(String topic, String text) {
            this.topic = topic;
            this.text = text;
        }
    }

    // ---------------- Dados ----------------

    /** Nome do arquivo importado (ex.: "Plano_Alimentar.xlsx"). */
    public final String source;
    /** Dia da importação (LocalDate.toEpochDay()); 0 se não houver. */
    public final long importedAt;
    public final List<PlanMeal> meals;
    public final List<Group> groups;
    public final int waterMlPerKg;
    public final String waterTips;
    public final List<Note> notes;
    private final boolean imported;
    private final boolean corrupt;

    NutritionPlan(String source, long importedAt, List<PlanMeal> meals, List<Group> groups,
                  int waterMlPerKg, String waterTips, List<Note> notes) {
        this(source, importedAt, meals, groups, waterMlPerKg, waterTips, notes, true, false);
    }

    private NutritionPlan(String source, long importedAt, List<PlanMeal> meals, List<Group> groups,
                          int waterMlPerKg, String waterTips, List<Note> notes,
                          boolean imported, boolean corrupt) {
        this.source = source;
        this.importedAt = importedAt;
        this.meals = meals;
        this.groups = groups;
        this.waterMlPerKg = waterMlPerKg;
        this.waterTips = waterTips;
        this.notes = notes;
        this.imported = imported;
        this.corrupt = corrupt;
    }

    private static NutritionPlan empty(boolean corrupt) {
        return new NutritionPlan("", 0, Collections.emptyList(), Collections.emptyList(), 0, "",
                Collections.emptyList(), false, corrupt);
    }

    /** true depois que uma planilha foi importada com sucesso. */
    public boolean isImported() {
        return imported;
    }

    /** true se o arquivo do plano existe mas não pôde ser lido (a tela mostra um ErrorState). */
    public boolean failedToLoad() {
        return corrupt;
    }

    @Nullable
    public Group group(int number) {
        for (Group g : groups) if (g.number == number) return g;
        return null;
    }

    /** Mesmo plano, com outra origem/data (usado ao confirmar a importação). */
    NutritionPlan withSource(String newSource, long newImportedAt) {
        return new NutritionPlan(newSource, newImportedAt, meals, groups, waterMlPerKg, waterTips, notes);
    }

    // ---------------- Acesso por usuário ----------------

    /** Plano do usuário logado, carregado uma vez e guardado em memória. */
    public static NutritionPlan get(Context context) {
        int user = AccountStore.currentUserId(context);
        NutritionPlan current = instance;
        if (current != null && instanceUser == user) return current;
        synchronized (NutritionPlan.class) {
            if (instance == null || instanceUser != user) {
                instance = user == 0 ? empty(false) : load(file(context, user));
                instanceUser = user;
            }
            return instance;
        }
    }

    /** Descarta o que está em memória e lê o arquivo de novo ("Tentar novamente"). */
    public static NutritionPlan reload(Context context) {
        synchronized (NutritionPlan.class) {
            instance = null;
        }
        return get(context);
    }

    /** Esquece o plano em memória (ao sair da conta). */
    public static void reset() {
        synchronized (NutritionPlan.class) {
            instance = null;
            instanceUser = 0;
        }
    }

    /**
     * Grava o plano do usuário logado (escreve num arquivo temporário e troca, para nunca
     * deixar um arquivo pela metade). Chame fora da thread principal.
     */
    static void save(Context context, NutritionPlan plan) throws IOException {
        int user = AccountStore.currentUserId(context);
        if (user == 0) throw new IOException("Nenhum usuário logado");
        File target = file(context, user);
        File tmp = new File(target.getParentFile(), target.getName() + ".tmp");
        try (FileOutputStream out = new FileOutputStream(tmp)) {
            out.write(plan.toJson().toString().getBytes(StandardCharsets.UTF_8));
            out.getFD().sync();
        } catch (JSONException e) {
            throw new IOException("Falha ao montar o plano", e);
        }
        if (!tmp.renameTo(target)) {
            tmp.delete();
            throw new IOException("Falha ao gravar o plano");
        }
        synchronized (NutritionPlan.class) {
            instance = plan;
            instanceUser = user;
        }
    }

    private static File file(Context context, int user) {
        return new File(context.getApplicationContext().getFilesDir(), "plano_alimentar_u" + user + ".json");
    }

    // ---------------- JSON ----------------

    private JSONObject toJson() throws JSONException {
        JSONObject root = new JSONObject();
        root.put("source", source);
        root.put("importedAt", importedAt);

        JSONArray jm = new JSONArray();
        for (PlanMeal m : meals) {
            JSONArray items = new JSONArray();
            for (PlanItem it : m.items) {
                JSONObject o = food(it.food);
                o.put("substitutions", new JSONArray(it.substitutions));
                o.put("group", it.group);
                o.put("portions", it.portions);
                items.put(o);
            }
            jm.put(new JSONObject()
                    .put("time", String.format(java.util.Locale.ROOT, "%02d:%02d", m.timeMinutes / 60, m.timeMinutes % 60))
                    .put("name", m.name)
                    .put("notes", m.notes)
                    .put("items", items));
        }
        root.put("meals", jm);

        JSONArray jg = new JSONArray();
        for (Group g : groups) {
            JSONArray foods = new JSONArray();
            for (Food f : g.foods) foods.put(food(f));
            jg.put(new JSONObject().put("number", g.number).put("name", g.name).put("items", foods));
        }
        root.put("groups", jg);

        JSONArray jn = new JSONArray();
        for (Note n : notes) jn.put(new JSONObject().put("topic", n.topic).put("text", n.text));
        root.put("notes", jn);

        root.put("water", new JSONObject().put("mlPerKg", waterMlPerKg).put("tips", waterTips));
        return root;
    }

    private static JSONObject food(Food f) throws JSONException {
        return new JSONObject().put("food", f.food).put("measure", f.measure).put("qty", f.qty);
    }

    private static NutritionPlan load(File file) {
        if (!file.exists()) return empty(false);
        try (InputStream in = new FileInputStream(file)) {
            JSONObject root = new JSONObject(new String(readAll(in), StandardCharsets.UTF_8));

            List<PlanMeal> meals = new ArrayList<>();
            JSONArray jm = root.getJSONArray("meals");
            for (int i = 0; i < jm.length(); i++) {
                JSONObject m = jm.getJSONObject(i);
                List<PlanItem> items = new ArrayList<>();
                JSONArray ji = m.getJSONArray("items");
                for (int j = 0; j < ji.length(); j++) {
                    JSONObject it = ji.getJSONObject(j);
                    List<String> subs = new ArrayList<>();
                    JSONArray js = it.getJSONArray("substitutions");
                    for (int k = 0; k < js.length(); k++) subs.add(js.getString(k));
                    items.add(new PlanItem(readFood(it), subs, it.optInt("group"), it.optString("portions")));
                }
                meals.add(new PlanMeal(parseTime(m.getString("time")), m.getString("name"),
                        m.optString("notes"), items));
            }

            List<Group> groups = new ArrayList<>();
            JSONArray jg = root.getJSONArray("groups");
            for (int i = 0; i < jg.length(); i++) {
                JSONObject g = jg.getJSONObject(i);
                List<Food> foods = new ArrayList<>();
                JSONArray jf = g.getJSONArray("items");
                for (int j = 0; j < jf.length(); j++) foods.add(readFood(jf.getJSONObject(j)));
                groups.add(new Group(g.getInt("number"), g.getString("name"), foods));
            }

            List<Note> notes = new ArrayList<>();
            JSONArray jn = root.getJSONArray("notes");
            for (int i = 0; i < jn.length(); i++) {
                JSONObject n = jn.getJSONObject(i);
                notes.add(new Note(n.getString("topic"), n.getString("text")));
            }

            JSONObject water = root.getJSONObject("water");
            return new NutritionPlan(root.optString("source"), root.optLong("importedAt"), meals, groups,
                    water.optInt("mlPerKg", 0), water.optString("tips"), notes);
        } catch (IOException | JSONException | RuntimeException e) {
            // Arquivo danificado: o app continua funcionando e a Dieta oferece importar de novo
            return empty(true);
        }
    }

    private static Food readFood(JSONObject o) {
        return new Food(o.optString("food"), o.optString("measure"), o.optString("qty"));
    }

    /** "08:00" -> 480 minutos. */
    private static int parseTime(String hhmm) {
        String[] parts = hhmm.split(":");
        return Integer.parseInt(parts[0].trim()) * 60 + Integer.parseInt(parts[1].trim());
    }

    private static byte[] readAll(InputStream in) throws IOException {
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int n;
        while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
        return out.toByteArray();
    }
}
