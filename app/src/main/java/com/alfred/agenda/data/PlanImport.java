package com.alfred.agenda.data;

import androidx.annotation.Nullable;

import com.alfred.agenda.util.XlsxReader;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Converte a planilha da dieta (formato do template em assets/plano_alimentar_template.xlsx)
 * num NutritionPlan, validando tudo antes.
 *
 * Regras do formato:
 *  - abas lidas pelo NOME (não pela posição): "Plano Alimentar", "Água", "Lista de Substituição"
 *    e "Observações"; a aba "Instruções" (e qualquer outra) é ignorada;
 *  - cabeçalhos na linha 4 e dados a partir da linha 5;
 *  - em "Substituições diretas", as opções vêm separadas por " | ";
 *  - "Ou escolha da lista" no formato "2,5 porções do Grupo 4".
 *
 * Linhas iguais às linhas de exemplo do template são contadas à parte, para a prévia
 * oferecer importar sem elas (é fácil esquecer de apagá-las).
 * Não usa nada do Android: é testada na JVM (PlanImportTest).
 */
public final class PlanImport {

    public static final String SHEET_PLAN = "Plano Alimentar";
    public static final String SHEET_WATER = "Água";
    public static final String SHEET_GROUPS = "Lista de Substituição";
    public static final String SHEET_NOTES = "Observações";

    static final int HEADER_ROW = 4;
    static final int FIRST_DATA_ROW = 5;
    private static final int MAX_ERRORS = 8;

    private static final String[] PLAN_HEADERS = {"Horário", "Refeição", "Alimento (opção principal)",
            "Medida caseira", "Qtd", "Substituições diretas", "Ou escolha da lista"};
    private static final String[] PLAN_KEYS = {"horario", "refeicao", "alimento", "medida", "qtd",
            "substituic", "ou escolha"};
    private static final String[] GROUP_HEADERS = {"Grupo", "Alimento", "Medida caseira (1 porção)", "Qtd"};
    private static final String[] GROUP_KEYS = {"grupo", "alimento", "medida", "qtd"};
    private static final String[] NOTE_HEADERS = {"Tema", "Orientação"};
    private static final String[] NOTE_KEYS = {"tema", "orientac"};

    private static final Pattern TIME = Pattern.compile("^(\\d{1,2})\\s*[:hH]\\s*(\\d{2})?$");
    private static final Pattern GROUP_LABEL = Pattern.compile("^(\\d{1,3})\\s*[-–—]\\s*(.+)$");
    private static final Pattern GROUP_REF = Pattern.compile("^(.+?)\\s+do\\s+grupo\\s+(\\d{1,3})\\s*$",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);

    /** Resultado da leitura, mostrado na prévia antes de salvar. */
    public static final class Result {
        /** Problemas que impedem a importação (vazio = pode importar). */
        public final List<String> errors;
        /** Avisos que não impedem (ex.: grupo citado que não existe na lista). */
        public final List<String> warnings;
        /** O plano com todas as linhas (null se houver erros). */
        @Nullable
        public final NutritionPlan plan;
        /** Sem as linhas de exemplo do template (igual a plan se não há exemplos; null se só havia exemplos). */
        @Nullable
        public final NutritionPlan planWithoutExamples;
        /** Quantas linhas são iguais às de exemplo do template. */
        public final int exampleRows;

        Result(List<String> errors, List<String> warnings, @Nullable NutritionPlan plan,
               @Nullable NutritionPlan planWithoutExamples, int exampleRows) {
            this.errors = errors;
            this.warnings = warnings;
            this.plan = plan;
            this.planWithoutExamples = planWithoutExamples;
            this.exampleRows = exampleRows;
        }

        public boolean ok() {
            return errors.isEmpty() && plan != null;
        }
    }

    private PlanImport() {
    }

    /** Resultado com um único erro (ex.: arquivo que nem é uma planilha). */
    public static Result failure(String message) {
        List<String> errors = new ArrayList<>();
        errors.add(message);
        return new Result(errors, new ArrayList<>(), null, null, 0);
    }

    // ===================== Linhas de exemplo do template =====================

    /** "Assinaturas" das linhas de exemplo das abas Plano Alimentar e Observações do template. */
    public static Set<String> exampleSignatures(@Nullable XlsxReader.Workbook template) {
        Set<String> set = new HashSet<>();
        if (template == null) return set;
        XlsxReader.Sheet plan = find(template, SHEET_PLAN);
        if (plan != null) {
            for (int r = FIRST_DATA_ROW; r <= plan.lastRow(); r++) {
                if (!plan.isBlank(r)) set.add(signature("P", plan, r, PLAN_HEADERS.length));
            }
        }
        XlsxReader.Sheet notes = find(template, SHEET_NOTES);
        if (notes != null) {
            for (int r = FIRST_DATA_ROW; r <= notes.lastRow(); r++) {
                if (!notes.isBlank(r)) set.add(signature("O", notes, r, NOTE_HEADERS.length));
            }
        }
        return set;
    }

    private static String signature(String prefix, XlsxReader.Sheet sheet, int row, int columns) {
        StringBuilder sb = new StringBuilder(prefix);
        for (int c = 0; c < columns; c++) sb.append('|').append(norm(sheet.cell(row, c)));
        return sb.toString();
    }

    // ===================== Leitura =====================

    /**
     * @param examples   assinaturas das linhas de exemplo (exampleSignatures do template)
     * @param source     nome do arquivo, exibido na Dieta
     * @param importedAt dia da importação (epochDay)
     */
    public static Result parse(XlsxReader.Workbook wb, Set<String> examples, String source, long importedAt) {
        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();

        // 1. Abas obrigatórias, pelo nome
        String[] required = {SHEET_PLAN, SHEET_WATER, SHEET_GROUPS, SHEET_NOTES};
        List<String> missing = new ArrayList<>();
        for (String name : required) if (find(wb, name) == null) missing.add("\"" + name + "\"");
        if (!missing.isEmpty()) {
            errors.add((missing.size() == 1 ? "Não encontrei a aba " : "Não encontrei as abas ")
                    + String.join(", ", missing) + ". A planilha precisa ter as abas Plano Alimentar, "
                    + "Água, Lista de Substituição e Observações, com esses nomes. Abas no arquivo: "
                    + (wb.sheets.isEmpty() ? "nenhuma" : String.join(", ", wb.sheetNames())) + ".");
            return new Result(errors, warnings, null, null, 0);
        }
        XlsxReader.Sheet planSheet = find(wb, SHEET_PLAN);
        XlsxReader.Sheet groupSheet = find(wb, SHEET_GROUPS);
        XlsxReader.Sheet noteSheet = find(wb, SHEET_NOTES);
        XlsxReader.Sheet waterSheet = find(wb, SHEET_WATER);

        // 2. Cabeçalhos na linha 4
        checkHeaders(planSheet, PLAN_HEADERS, PLAN_KEYS, errors);
        checkHeaders(groupSheet, GROUP_HEADERS, GROUP_KEYS, errors);
        checkHeaders(noteSheet, NOTE_HEADERS, NOTE_KEYS, errors);
        if (!errors.isEmpty()) return new Result(errors, warnings, null, null, 0);

        // 3. Dados (uma vez com tudo e outra sem as linhas de exemplo)
        int[] exampleCount = {0};
        NutritionPlan all = build(planSheet, groupSheet, noteSheet, waterSheet, Collections.emptySet(),
                source, importedAt, errors, warnings, null);
        if (!errors.isEmpty()) return new Result(cap(errors), warnings, null, null, 0);
        if (all.meals.isEmpty()) {
            errors.add("Nenhuma refeição encontrada na aba \"" + SHEET_PLAN + "\". Os dados começam na linha "
                    + FIRST_DATA_ROW + ", com Horário, Refeição e Alimento preenchidos.");
            return new Result(errors, warnings, null, null, 0);
        }
        NutritionPlan clean = null;
        if (!examples.isEmpty()) {
            clean = build(planSheet, groupSheet, noteSheet, waterSheet, examples, source, importedAt,
                    new ArrayList<>(), new ArrayList<>(), exampleCount);
            if (clean.meals.isEmpty()) clean = null;
        }
        return new Result(errors, warnings, all, exampleCount[0] > 0 ? clean : all, exampleCount[0]);
    }

    private static NutritionPlan build(XlsxReader.Sheet planSheet, XlsxReader.Sheet groupSheet,
                                       XlsxReader.Sheet noteSheet, XlsxReader.Sheet waterSheet,
                                       Set<String> skip, String source, long importedAt,
                                       List<String> errors, List<String> warnings, @Nullable int[] skipped) {
        // ---- Observações (as que têm o nome de uma refeição viram a orientação dela) ----
        List<NutritionPlan.Note> notes = new ArrayList<>();
        Map<String, String> notesByTopic = new LinkedHashMap<>();
        for (int r = FIRST_DATA_ROW; r <= noteSheet.lastRow(); r++) {
            if (noteSheet.isBlank(r)) continue;
            if (skip.contains(signature("O", noteSheet, r, NOTE_HEADERS.length))) {
                if (skipped != null) skipped[0]++;
                continue;
            }
            String topic = noteSheet.cell(r, 0);
            String text = noteSheet.cell(r, 1);
            if (topic.isEmpty() || text.isEmpty()) {
                warnings.add(where(SHEET_NOTES, r) + "preencha Tema e Orientação (linha ignorada).");
                continue;
            }
            notes.add(new NutritionPlan.Note(topic, text));
            notesByTopic.putIfAbsent(norm(topic), text);
        }

        // ---- Lista de substituição ----
        Map<Integer, String> groupNames = new TreeMap<>();
        Map<Integer, List<NutritionPlan.Food>> groupFoods = new TreeMap<>();
        for (int r = FIRST_DATA_ROW; r <= groupSheet.lastRow(); r++) {
            if (groupSheet.isBlank(r)) continue;
            String label = groupSheet.cell(r, 0);
            String food = groupSheet.cell(r, 1);
            Matcher m = GROUP_LABEL.matcher(label);
            if (!m.matches()) {
                errors.add(where(SHEET_GROUPS, r) + "o grupo \"" + label + "\" deve estar no formato "
                        + "\"N - Nome\" (ex.: 4 - Carnes e Proteínas).");
                continue;
            }
            if (food.isEmpty()) {
                errors.add(where(SHEET_GROUPS, r) + "falta o alimento (coluna B).");
                continue;
            }
            int number = Integer.parseInt(m.group(1));
            groupNames.putIfAbsent(number, m.group(2).trim());
            groupFoods.computeIfAbsent(number, k -> new ArrayList<>())
                    .add(new NutritionPlan.Food(food, groupSheet.cell(r, 2), cleanQty(groupSheet.cell(r, 3))));
        }
        List<NutritionPlan.Group> groups = new ArrayList<>();
        for (Map.Entry<Integer, String> e : groupNames.entrySet()) {
            groups.add(new NutritionPlan.Group(e.getKey(), e.getValue(), groupFoods.get(e.getKey())));
        }

        // ---- Água ----
        int mlPerKg = 0;
        String tips = "";
        for (int r = 1; r <= waterSheet.lastRow(); r++) {
            String label = waterSheet.cell(r, 0);
            String n = norm(label);
            if (n.startsWith("ml por kg")) {
                Integer value = parseInt(waterSheet.cell(r, 1));
                if (value != null && value >= 10 && value <= 100) {
                    mlPerKg = value;
                } else if (!waterSheet.cell(r, 1).isEmpty()) {
                    warnings.add(where(SHEET_WATER, r) + "\"ml por kg\" deve ser um número entre 10 e 100; "
                            + "usarei 35 ml por kg.");
                }
            } else if (n.startsWith("dicas")) {
                int colon = label.indexOf(':');
                tips = colon >= 0 ? label.substring(colon + 1).trim() : label;
            }
        }

        // ---- Plano: agrupa as linhas por (horário, refeição), mantendo a ordem ----
        Map<String, MealBuilder> meals = new LinkedHashMap<>();
        Set<Integer> referenced = new HashSet<>();
        for (int r = FIRST_DATA_ROW; r <= planSheet.lastRow(); r++) {
            if (planSheet.isBlank(r)) continue;
            if (skip.contains(signature("P", planSheet, r, PLAN_HEADERS.length))) {
                if (skipped != null) skipped[0]++;
                continue;
            }
            String timeText = planSheet.cell(r, 0);
            String name = planSheet.cell(r, 1);
            String food = planSheet.cell(r, 2);
            int before = errors.size();
            int time = parseTime(timeText);
            if (timeText.isEmpty()) {
                errors.add(where(SHEET_PLAN, r) + "falta o horário (coluna A, formato HH:MM).");
            } else if (time < 0) {
                errors.add(where(SHEET_PLAN, r) + "horário \"" + timeText + "\" inválido. Use HH:MM (ex.: 08:00).");
            }
            if (name.isEmpty()) errors.add(where(SHEET_PLAN, r) + "falta o nome da refeição (coluna B).");
            if (food.isEmpty()) errors.add(where(SHEET_PLAN, r) + "falta o alimento (coluna C).");
            if (errors.size() > before) continue;

            MealBuilder meal = meals.computeIfAbsent(time + "|" + norm(name), k -> new MealBuilder(time, name));
            List<String> subs = new ArrayList<>();
            for (String s : planSheet.cell(r, 5).split("\\|")) if (!s.trim().isEmpty()) subs.add(s.trim());

            String ref = planSheet.cell(r, 6);
            int group = 0;
            String portions = "";
            Matcher m = GROUP_REF.matcher(ref);
            if (m.matches()) {
                group = Integer.parseInt(m.group(2));
                portions = m.group(1).trim();
                referenced.add(group);
            } else if (!ref.isEmpty()) {
                meal.extraNotes.add(food + ": " + ref);
            }
            meal.items.add(new NutritionPlan.PlanItem(
                    new NutritionPlan.Food(food, planSheet.cell(r, 3), cleanQty(planSheet.cell(r, 4))),
                    subs, group, portions));
        }
        for (int g : new TreeSet<>(referenced)) {
            if (!groupNames.containsKey(g)) {
                warnings.add("O Grupo " + g + " é citado no plano, mas não existe na aba \"" + SHEET_GROUPS + "\".");
            }
        }

        List<NutritionPlan.PlanMeal> planMeals = new ArrayList<>();
        for (MealBuilder b : meals.values()) {
            List<String> mealNotes = new ArrayList<>();
            String fromSheet = notesByTopic.get(norm(b.name));
            if (fromSheet != null) mealNotes.add(fromSheet);
            mealNotes.addAll(b.extraNotes);
            planMeals.add(new NutritionPlan.PlanMeal(b.time, b.name, String.join("\n", mealNotes), b.items));
        }
        // Mesma ordem da aba Dieta: por horário (a planilha pode vir fora de ordem)
        planMeals.sort((a, b) -> Integer.compare(a.timeMinutes, b.timeMinutes));
        return new NutritionPlan(source, importedAt, planMeals, groups, mlPerKg, tips, notes);
    }

    private static final class MealBuilder {
        final int time;
        final String name;
        final List<NutritionPlan.PlanItem> items = new ArrayList<>();
        final List<String> extraNotes = new ArrayList<>();

        MealBuilder(int time, String name) {
            this.time = time;
            this.name = name;
        }
    }

    // ===================== Validação =====================

    private static void checkHeaders(XlsxReader.Sheet sheet, String[] headers, String[] keys, List<String> errors) {
        for (int c = 0; c < headers.length; c++) {
            String actual = sheet.cell(HEADER_ROW, c);
            if (!norm(actual).startsWith(keys[c])) {
                errors.add("Aba \"" + sheet.name + "\": a coluna " + (char) ('A' + c) + " da linha " + HEADER_ROW
                        + " deveria ser \"" + headers[c] + "\", mas "
                        + (actual.isEmpty() ? "está vazia" : "está \"" + actual + "\"")
                        + ". Mantenha os cabeçalhos do template na linha " + HEADER_ROW + ".");
            }
        }
    }

    private static List<String> cap(List<String> errors) {
        if (errors.size() <= MAX_ERRORS) return errors;
        List<String> capped = new ArrayList<>(errors.subList(0, MAX_ERRORS));
        capped.add("… e mais " + (errors.size() - MAX_ERRORS) + " problema(s).");
        return capped;
    }

    private static String where(String sheet, int row) {
        return "Aba \"" + sheet + "\", linha " + row + ": ";
    }

    // ===================== Utilidades =====================

    /** Procura a aba pelo nome, sem ligar para acento, maiúsculas ou espaços sobrando. */
    @Nullable
    static XlsxReader.Sheet find(XlsxReader.Workbook wb, String name) {
        String wanted = norm(name);
        for (Map.Entry<String, XlsxReader.Sheet> e : wb.sheets.entrySet()) {
            if (norm(e.getKey()).equals(wanted)) return e.getValue();
        }
        return null;
    }

    /**
     * Horário em minutos: "08:00", "8:00", "8h", "08h30" ou a fração de dia que o Excel grava
     * quando a célula é do tipo hora (0,3125 = 07:30). -1 se inválido.
     */
    static int parseTime(String text) {
        String t = text.trim();
        if (t.isEmpty()) return -1;
        Matcher m = TIME.matcher(t);
        if (m.matches()) {
            int h = Integer.parseInt(m.group(1));
            int min = m.group(2) == null ? 0 : Integer.parseInt(m.group(2));
            return h <= 23 && min <= 59 ? h * 60 + min : -1;
        }
        try {
            double fraction = Double.parseDouble(t.replace(',', '.'));
            if (fraction >= 0 && fraction < 1) return (int) Math.round(fraction * 24 * 60) % (24 * 60);
        } catch (NumberFormatException ignored) {
            // cai no -1
        }
        return -1;
    }

    @Nullable
    private static Integer parseInt(String text) {
        try {
            return (int) Math.round(Double.parseDouble(text.trim().replace(',', '.')));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** "—" e "-" na coluna Qtd significam "sem quantidade". */
    private static String cleanQty(String qty) {
        String q = qty.trim();
        return q.equals("—") || q.equals("-") || q.equals("–") ? "" : q;
    }

    /** "  Lista de  Substituição " -> "lista de substituicao". */
    static String norm(String s) {
        String n = Normalizer.normalize(s, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        return n.toLowerCase(Locale.ROOT).replaceAll("\\s+", " ").trim();
    }
}
