package com.alfred.agenda.cloud;

import androidx.annotation.Nullable;

import com.alfred.agenda.data.entity.Habit;
import com.alfred.agenda.data.entity.Meal;
import com.alfred.agenda.data.entity.MealItem;
import com.alfred.agenda.data.entity.StudyModule;
import com.alfred.agenda.data.entity.StudySubject;
import com.alfred.agenda.data.entity.StudyTopic;
import com.alfred.agenda.data.entity.Tag;
import com.alfred.agenda.data.entity.Task;
import com.alfred.agenda.util.TagPalette;

import java.security.SecureRandom;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Formato dos documentos no Firestore (a estrutura aprovada) e a conversão de/para as
 * entidades do app. É a "fonte da verdade" do formato: a versão web lê e grava os
 * mesmos campos (ver web/js/schema.js).
 *
 *   usuarios/{uid}                     nome, tratamento, pesoKg, avatar, onboarding
 *   usuarios/{uid}/tarefas/{id}        titulo, descricao, dia, prioridade, status, categoria, periodo, tags,
 *                                      tipo, diaInteiro, inicio, fim, adiadaDe (Trabalho)
 *   usuarios/{uid}/tags/{id}           nome, cor, grupo (-1 = Pessoal; 0..4 = grupo do Trabalho)
 *   usuarios/{uid}/refeicoes/{id}      nome, horario, avisar, observacoes, itens, feitas, comido
 *   usuarios/{uid}/plano/atual         plano importado da planilha
 *   usuarios/{uid}/habitos/{id}        nome, posicao, feitos
 *   usuarios/{uid}/agua/{dia}          ml
 *   usuarios/{uid}/estudos_materias/{id}   nome, posicao, cor
 *   usuarios/{uid}/estudos_modulos/{id}    materiaId, nome, posicao
 *   usuarios/{uid}/estudos_assuntos/{id}   materiaId, moduloId, nome, posicao, concluido,
 *                                          rodandoDesde, tempoPorDia
 *
 * Datas são texto ISO ("2026-11-03"); horários "HH:MM". Os ids são números sorteados
 * (até 2^53, que o JavaScript representa sem perda) e o id do documento é esse número.
 * Sem Android aqui: testado na JVM (CloudSchemaTest).
 */
public final class CloudSchema {

    public static final String USERS = "usuarios";
    public static final String TASKS = "tarefas";
    public static final String TAGS = "tags";
    public static final String MEALS = "refeicoes";
    public static final String PLAN = "plano";
    public static final String PLAN_DOC = "atual";
    public static final String HABITS = "habitos";
    public static final String WATER = "agua";
    public static final String TOPICS = "estudos_materias";
    public static final String MODULES = "estudos_modulos";
    public static final String SUBJECTS = "estudos_assuntos";

    /** Campos que só mudam por operações atômicas (arrayUnion, increment): nunca vão no set(merge). */
    public static final String MEAL_DONE = "feitas";
    /** {"2026-11-04": "o que foi comido de verdade"} (muda por chave, com merge). */
    public static final String MEAL_EATEN = "comido";
    public static final String HABIT_DONE = "feitos";
    public static final String SUBJECT_TIME = "tempoPorDia";
    public static final String WATER_ML = "ml";
    public static final String SUBJECT_RUNNING = "rodandoDesde";

    /** Maior inteiro que o JavaScript representa exatamente (2^53 - 1). */
    static final long MAX_ID = (1L << 53) - 1;
    private static final SecureRandom RANDOM = new SecureRandom();

    private CloudSchema() {
    }

    // ===================== Ids e datas =====================

    /** Id novo, aleatório, positivo e seguro para o JavaScript. */
    public static long newId() {
        long id;
        do {
            id = RANDOM.nextLong() & MAX_ID;
        } while (id == 0);
        return id;
    }

    public static String docId(long id) {
        return Long.toString(id);
    }

    /** Id do documento -> número; 0 se não for um id válido (documento estranho é ignorado). */
    public static long parseId(@Nullable String docId) {
        if (docId == null) return 0;
        try {
            long id = Long.parseLong(docId);
            return id > 0 && id <= MAX_ID ? id : 0;
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    public static String day(long epochDay) {
        return LocalDate.ofEpochDay(epochDay).toString();
    }

    /** "2026-11-03" -> epochDay; null se o texto não for uma data. */
    @Nullable
    public static Long parseDay(@Nullable Object value) {
        if (!(value instanceof String)) return null;
        try {
            return LocalDate.parse((String) value).toEpochDay();
        } catch (RuntimeException e) {
            return null;
        }
    }

    static String time(int minutes) {
        int m = Math.max(0, Math.min(24 * 60 - 1, minutes));
        return String.format(Locale.ROOT, "%02d:%02d", m / 60, m % 60);
    }

    static int parseTime(@Nullable Object value, int fallback) {
        if (!(value instanceof String)) return fallback;
        String[] p = ((String) value).split(":");
        try {
            int h = Integer.parseInt(p[0].trim());
            int m = Integer.parseInt(p[1].trim());
            return h >= 0 && h < 24 && m >= 0 && m < 60 ? h * 60 + m : fallback;
        } catch (RuntimeException e) {
            return fallback;
        }
    }

    // ===================== Tarefas e tags =====================

    public static Map<String, Object> task(Task t, Collection<Long> tagIds) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("titulo", t.title);
        m.put("descricao", t.description);
        m.put("dia", day(t.dueEpochDay));
        m.put("prioridade", t.priority);
        m.put("status", t.status);
        m.put("categoria", t.category == Task.CATEGORY_PERSONAL ? "pessoal" : "trabalho");
        m.put("periodo", t.period);
        m.put("tags", new ArrayList<>(tagIds));
        m.put("tipo", t.demandType);
        m.put("diaInteiro", t.allDay);
        m.put("inicio", t.startMinutes >= 0 ? time(t.startMinutes) : null);
        m.put("fim", t.endMinutes >= 0 ? time(t.endMinutes) : null);
        m.put("adiadaDe", t.postponedFrom > 0 ? day(t.postponedFrom) : null);
        return m;
    }

    @Nullable
    public static Task task(long id, Map<String, Object> m) {
        Long dia = parseDay(m.get("dia"));
        if (id == 0 || dia == null) return null;
        Task t = new Task();
        t.id = id;
        t.title = str(m, "titulo", "");
        t.description = str(m, "descricao", "");
        t.dueEpochDay = dia;
        t.priority = clamp(integer(m, "prioridade", Task.PRIORITY_MEDIUM), Task.PRIORITY_LOW, Task.PRIORITY_HIGH);
        t.status = clamp(integer(m, "status", Task.STATUS_PENDING), Task.STATUS_PENDING, Task.STATUS_CANCELED);
        t.category = "pessoal".equals(m.get("categoria")) ? Task.CATEGORY_PERSONAL : Task.CATEGORY_WORK;
        t.period = clamp(integer(m, "periodo", Task.PERIOD_MORNING), Task.PERIOD_MORNING, Task.PERIOD_NIGHT);
        t.demandType = clamp(integer(m, "tipo", Task.TYPE_OTHER), 0, Task.TYPE_COUNT - 1);
        t.allDay = bool(m, "diaInteiro", false);
        t.startMinutes = parseTime(m.get("inicio"), -1);
        t.endMinutes = parseTime(m.get("fim"), -1);
        Long from = parseDay(m.get("adiadaDe"));
        t.postponedFrom = from == null ? 0 : from;
        return t;
    }

    public static List<Long> taskTags(Map<String, Object> m) {
        return ids(m.get("tags"));
    }

    public static Map<String, Object> tag(Tag t) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("nome", t.name);
        m.put("cor", t.colorIndex);
        m.put("grupo", t.tagGroup);
        return m;
    }

    @Nullable
    public static Tag tag(long id, Map<String, Object> m) {
        if (id == 0) return null;
        Tag t = new Tag();
        t.id = id;
        t.name = str(m, "nome", "");
        // Sem cor válida: cinza, como a web e como o app já mostra (TagPalette.safeIndex)
        int group = integer(m, "grupo", Tag.NO_GROUP);
        t.tagGroup = group >= 0 && group < Tag.GROUP_COUNT ? group : Tag.NO_GROUP;
        // Pessoal: cor da paleta (sem cor válida, cinza); Trabalho: tom 0 a 3 dentro do grupo
        t.colorIndex = t.isWork() ? Math.max(0, Math.min(3, integer(m, "cor", 0)))
                : TagPalette.safeIndex(integer(m, "cor", TagPalette.DEFAULT_INDEX));
        return t;
    }

    // ===================== Dieta =====================

    /** Refeição com os itens embutidos (sem "feitas", que muda por arrayUnion/arrayRemove). */
    public static Map<String, Object> meal(Meal meal, List<MealItem> items) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("nome", meal.name);
        m.put("horario", time(meal.timeMinutes));
        m.put("avisar", meal.notifyEnabled);
        m.put("observacoes", meal.notes);
        List<Map<String, Object>> list = new ArrayList<>();
        for (MealItem it : items) {
            Map<String, Object> i = new LinkedHashMap<>();
            i.put("alimento", it.description);
            i.put("quantidade", it.quantity);
            i.put("substituicoes", it.substitutions);
            i.put("grupo", it.groupNumber);
            i.put("porcoes", it.groupPortions);
            list.add(i);
        }
        m.put("itens", list);
        return m;
    }

    @Nullable
    public static Meal meal(long id, Map<String, Object> m) {
        if (id == 0) return null;
        Meal meal = new Meal();
        meal.id = id;
        meal.name = str(m, "nome", "");
        meal.timeMinutes = parseTime(m.get("horario"), 8 * 60);
        meal.notifyEnabled = bool(m, "avisar", true);
        meal.notes = str(m, "observacoes", "");
        return meal;
    }

    /** Itens da refeição, na ordem; o id local é gerado pelo Room. */
    public static List<MealItem> mealItems(long mealId, Map<String, Object> m) {
        List<MealItem> items = new ArrayList<>();
        Object raw = m.get("itens");
        if (!(raw instanceof List)) return items;
        int pos = 0;
        for (Object o : (List<?>) raw) {
            if (!(o instanceof Map)) continue;
            @SuppressWarnings("unchecked") Map<String, Object> i = (Map<String, Object>) o;
            MealItem it = new MealItem();
            it.mealId = mealId;
            it.position = pos++;
            it.description = str(i, "alimento", "");
            it.quantity = str(i, "quantidade", "");
            it.substitutions = str(i, "substituicoes", "");
            it.groupNumber = integer(i, "grupo", 0);
            it.groupPortions = str(i, "porcoes", "");
            items.add(it);
        }
        return items;
    }

    /** "comido" -> {epochDay: texto}, só datas válidas e textos não vazios. */
    public static Map<Long, String> mealEaten(Map<String, Object> m) {
        Map<Long, String> out = new HashMap<>();
        Object raw = m.get(MEAL_EATEN);
        if (!(raw instanceof Map)) return out;
        for (Map.Entry<?, ?> e : ((Map<?, ?>) raw).entrySet()) {
            Long d = parseDay(e.getKey());
            if (d != null && e.getValue() instanceof String && !((String) e.getValue()).trim().isEmpty()) {
                out.put(d, (String) e.getValue());
            }
        }
        return out;
    }

    // ===================== Hábitos =====================

    public static Map<String, Object> habit(Habit h) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("nome", h.name);
        m.put("posicao", h.position);
        return m;
    }

    @Nullable
    public static Habit habit(long id, Map<String, Object> m) {
        if (id == 0) return null;
        Habit h = new Habit();
        h.id = id;
        h.name = str(m, "nome", "");
        h.position = integer(m, "posicao", 0);
        return h;
    }

    /** Lista de dias ("feitos"/"feitas") -> epochDays válidos, sem repetição. */
    public static List<Long> days(@Nullable Object raw) {
        List<Long> out = new ArrayList<>();
        if (!(raw instanceof List)) return out;
        for (Object o : (List<?>) raw) {
            Long d = parseDay(o);
            if (d != null && !out.contains(d)) out.add(d);
        }
        return out;
    }

    // ===================== Estudos =====================

    public static Map<String, Object> topic(StudyTopic t) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("nome", t.name);
        m.put("posicao", t.position);
        m.put("cor", t.colorIndex);
        return m;
    }

    @Nullable
    public static StudyTopic topic(long id, Map<String, Object> m) {
        if (id == 0) return null;
        StudyTopic t = new StudyTopic();
        t.id = id;
        t.name = str(m, "nome", "");
        t.position = integer(m, "posicao", 0);
        t.colorIndex = integer(m, "cor", 0);
        return t;
    }

    public static Map<String, Object> module(StudyModule mod) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("materiaId", mod.topicId);
        m.put("nome", mod.name);
        m.put("posicao", mod.position);
        return m;
    }

    @Nullable
    public static StudyModule module(long id, Map<String, Object> m) {
        long topic = lng(m, "materiaId", 0);
        if (id == 0 || topic <= 0) return null;
        StudyModule mod = new StudyModule();
        mod.id = id;
        mod.topicId = topic;
        mod.name = str(m, "nome", "");
        mod.position = integer(m, "posicao", 0);
        return mod;
    }

    /** Assunto sem "tempoPorDia" (que só muda por increment). */
    public static Map<String, Object> subject(StudySubject s) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("materiaId", s.topicId);
        m.put("moduloId", s.moduleId);
        m.put("nome", s.name);
        m.put("posicao", s.position);
        m.put("concluido", s.completed);
        m.put(SUBJECT_RUNNING, s.runningSince);
        return m;
    }

    @Nullable
    public static StudySubject subject(long id, Map<String, Object> m) {
        long topic = lng(m, "materiaId", 0);
        if (id == 0 || topic <= 0) return null;
        StudySubject s = new StudySubject();
        s.id = id;
        s.topicId = topic;
        long module = lng(m, "moduloId", 0);
        s.moduleId = module > 0 ? module : null;
        s.name = str(m, "nome", "");
        s.position = integer(m, "posicao", 0);
        s.completed = bool(m, "concluido", false);
        s.runningSince = Math.max(0, lng(m, SUBJECT_RUNNING, 0));
        return s;
    }

    /** "tempoPorDia" -> {epochDay: segundos}, só valores positivos e datas válidas. */
    public static Map<Long, Long> timeByDay(Map<String, Object> m) {
        Map<Long, Long> out = new HashMap<>();
        Object raw = m.get(SUBJECT_TIME);
        if (!(raw instanceof Map)) return out;
        for (Map.Entry<?, ?> e : ((Map<?, ?>) raw).entrySet()) {
            Long d = parseDay(e.getKey());
            if (d != null && e.getValue() instanceof Number) {
                long seconds = ((Number) e.getValue()).longValue();
                if (seconds > 0) out.put(d, seconds);
            }
        }
        return out;
    }

    // ===================== Leitura tolerante =====================
    // O Firestore devolve números como Long ou Double; documentos editados à mão (ou pela
    // web com bug) não podem derrubar o app: campo faltando ou do tipo errado vira o padrão.

    static String str(Map<String, Object> m, String key, String fallback) {
        Object v = m.get(key);
        return v instanceof String ? (String) v : fallback;
    }

    static long lng(Map<String, Object> m, String key, long fallback) {
        Object v = m.get(key);
        return v instanceof Number ? ((Number) v).longValue() : fallback;
    }

    static int integer(Map<String, Object> m, String key, int fallback) {
        Object v = m.get(key);
        return v instanceof Number ? ((Number) v).intValue() : fallback;
    }

    static boolean bool(Map<String, Object> m, String key, boolean fallback) {
        Object v = m.get(key);
        return v instanceof Boolean ? (Boolean) v : fallback;
    }

    private static List<Long> ids(@Nullable Object raw) {
        List<Long> out = new ArrayList<>();
        if (!(raw instanceof List)) return out;
        for (Object o : (List<?>) raw) {
            if (o instanceof Number) {
                long id = ((Number) o).longValue();
                if (id > 0 && !out.contains(id)) out.add(id);
            }
        }
        return out;
    }

    private static int clamp(int v, int min, int max) {
        return Math.max(min, Math.min(max, v));
    }
}
