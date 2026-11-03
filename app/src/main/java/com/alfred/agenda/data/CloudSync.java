package com.alfred.agenda.data;

import android.content.Context;

import androidx.annotation.Nullable;

import com.alfred.agenda.account.AccountStore;
import com.alfred.agenda.account.UserProfile;
import com.alfred.agenda.cloud.CloudConfig;
import com.alfred.agenda.cloud.CloudSchema;
import com.alfred.agenda.data.dao.SyncDao;
import com.alfred.agenda.data.entity.Habit;
import com.alfred.agenda.data.entity.HabitLog;
import com.alfred.agenda.data.entity.Meal;
import com.alfred.agenda.data.entity.MealItem;
import com.alfred.agenda.data.entity.MealLog;
import com.alfred.agenda.data.entity.StudyLog;
import com.alfred.agenda.data.entity.StudyModule;
import com.alfred.agenda.data.entity.StudySubject;
import com.alfred.agenda.data.entity.StudyTopic;
import com.alfred.agenda.data.entity.Tag;
import com.alfred.agenda.data.entity.Task;
import com.alfred.agenda.data.entity.TaskTag;
import com.alfred.agenda.data.entity.WaterLog;
import com.alfred.agenda.notifications.AlarmScheduler;
import com.alfred.agenda.util.AppLog;
import com.alfred.agenda.util.Honorific;
import com.google.firebase.firestore.CollectionReference;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.MetadataChanges;
import com.google.firebase.firestore.SetOptions;
import com.google.firebase.firestore.WriteBatch;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executor;

/**
 * Sincronização com o Firestore para contas da nuvem. O Firestore é a fonte da verdade;
 * o banco local (Room) é um espelho que as telas, os lembretes e o modo offline usam.
 *
 *  - Ida: cada gravação do AlfredRepository também é enviada (pushX). Sem internet, o
 *    Firestore guarda na fila e envia depois. Marcações de "feito" usam arrayUnion/arrayRemove
 *    e a água e o tempo de estudo usam increment: celular e web podem mexer ao mesmo tempo
 *    sem perder nada.
 *  - Volta: listeners em tempo real em cada coleção. A cada mudança a coleção inteira é
 *    reconciliada no banco local (o que sumiu da nuvem é apagado aqui), e os lembretes são
 *    reagendados. Tudo roda na thread de banco do repositório, na ordem.
 *
 * Os ids são os mesmos nos dois lados (CloudSchema.newId). Itens de refeição, registros
 * por dia e ligações tarefa-tag não têm documento próprio: vêm embutidos no documento pai.
 */
public final class CloudSync {

    private final Context app;
    private final AppDatabase db;
    private final SyncDao dao;
    private final AlarmScheduler scheduler;
    private final Executor io;
    private final Runnable onStudyChanged;
    private final DocumentReference user;
    private final List<ListenerRegistration> listeners = new ArrayList<>();

    /** Últimos estados de coleções que dependem umas das outras (chaves estrangeiras). */
    private List<DocumentSnapshot> lastTasks;
    private List<DocumentSnapshot> lastTopics;
    private List<DocumentSnapshot> lastModules;
    private List<DocumentSnapshot> lastSubjects;
    /** Último plano aplicado (eventos só de metadados não regravam o arquivo). */
    @Nullable
    private Map<String, Object> lastPlan;

    /**
     * @param io             thread de banco do repositório (listeners e gravações em ordem)
     * @param onStudyChanged roda depois de aplicar estudos (atualiza a notificação do cronômetro)
     */
    CloudSync(Context app, AppDatabase db, String uid, AlarmScheduler scheduler, Executor io,
              Runnable onStudyChanged) {
        this.app = app;
        this.db = db;
        this.dao = db.syncDao();
        this.scheduler = scheduler;
        this.io = io;
        this.onStudyChanged = onStudyChanged;
        this.user = userDoc(uid);
    }

    private static DocumentReference userDoc(String uid) {
        return FirebaseFirestore.getInstance().collection(CloudSchema.USERS).document(uid);
    }

    private CollectionReference col(String name) {
        return user.collection(name);
    }

    private DocumentReference doc(String collection, long id) {
        return col(collection).document(CloudSchema.docId(id));
    }

    // ===================== Ciclo de vida =====================

    void start() {
        if (AccountStore.takeSeedFlag(app)) seedSuggestedHabits();
        listen(CloudSchema.TAGS, this::applyTags);
        listen(CloudSchema.TASKS, docs -> {
            lastTasks = docs;
            applyTasks();
        });
        listen(CloudSchema.MEALS, this::applyMeals);
        listen(CloudSchema.HABITS, this::applyHabits);
        listen(CloudSchema.WATER, this::applyWater);
        listen(CloudSchema.TOPICS, docs -> {
            lastTopics = docs;
            applyStudy();
        });
        listen(CloudSchema.MODULES, docs -> {
            lastModules = docs;
            applyStudy();
        });
        listen(CloudSchema.SUBJECTS, docs -> {
            lastSubjects = docs;
            applyStudy();
        });
        // MetadataChanges.INCLUDE: o primeiro evento vem do cache; a confirmação do servidor
        // ("não existe mesmo") só muda os metadados e, sem isso, não chegaria
        listeners.add(user.addSnapshotListener(io, MetadataChanges.INCLUDE, (snap, error) -> {
            if (error != null) AppLog.e("Falha ao ouvir o perfil", error);
            else if (snap != null) guard(() -> applyProfile(snap));
        }));
        listeners.add(col(CloudSchema.PLAN).document(CloudSchema.PLAN_DOC).addSnapshotListener(io,
                MetadataChanges.INCLUDE, (snap, error) -> {
            if (error != null) AppLog.e("Falha ao ouvir o plano", error);
            else if (snap != null) guard(() -> applyPlan(snap));
        }));
    }

    void stop() {
        for (ListenerRegistration r : listeners) r.remove();
        listeners.clear();
    }

    private interface Apply {
        void apply(List<DocumentSnapshot> docs);
    }

    private void listen(String collection, Apply apply) {
        listeners.add(col(collection).addSnapshotListener(io, (snap, error) -> {
            if (error != null) {
                AppLog.e("Falha ao ouvir " + collection, error);
                return;
            }
            if (snap != null) guard(() -> apply.apply(snap.getDocuments()));
        }));
    }

    /** Um documento estranho ou um erro do banco não pode derrubar o app. */
    private static void guard(Runnable r) {
        try {
            r.run();
        } catch (RuntimeException e) {
            AppLog.e("Falha ao aplicar dados da nuvem", e);
        }
    }

    // ===================== Volta: nuvem -> banco local =====================

    private static Map<String, Object> data(DocumentSnapshot d) {
        Map<String, Object> m = d.getData();
        return m == null ? new HashMap<>() : m;
    }

    /** Apaga do banco local os ids que não estão mais na nuvem. */
    private static List<Long> missing(List<Long> local, Set<Long> remote) {
        List<Long> out = new ArrayList<>();
        for (long id : local) if (!remote.contains(id)) out.add(id);
        return out;
    }

    private void applyTags(List<DocumentSnapshot> docs) {
        List<Tag> tags = new ArrayList<>();
        Set<Long> ids = new HashSet<>();
        for (DocumentSnapshot d : docs) {
            Tag t = CloudSchema.tag(CloudSchema.parseId(d.getId()), data(d));
            if (t != null && ids.add(t.id)) tags.add(t);
        }
        db.runInTransaction(() -> {
            dao.upsertTags(tags);
            dao.deleteTags(missing(dao.tagIds(), ids));
        });
        applyTasks(); // ligações tarefa-tag que dependiam de tags que ainda não tinham chegado
    }

    private void applyTasks() {
        if (lastTasks == null) return;
        List<Task> tasks = new ArrayList<>();
        List<TaskTag> links = new ArrayList<>();
        Set<Long> ids = new HashSet<>();
        Set<Long> tags = new HashSet<>(dao.tagIds());
        for (DocumentSnapshot d : lastTasks) {
            Map<String, Object> m = data(d);
            Task t = CloudSchema.task(CloudSchema.parseId(d.getId()), m);
            if (t == null || !ids.add(t.id)) continue;
            tasks.add(t);
            for (long tagId : CloudSchema.taskTags(m)) {
                if (!tags.contains(tagId)) continue;
                TaskTag link = new TaskTag();
                link.taskId = t.id;
                link.tagId = tagId;
                links.add(link);
            }
        }
        List<Long> removed = new ArrayList<>();
        db.runInTransaction(() -> {
            dao.upsertTasks(tasks);
            removed.addAll(missing(dao.taskIds(), ids));
            dao.deleteTasks(removed);
            dao.clearTaskTags();
            dao.insertTaskTags(links);
        });
        for (Task t : tasks) scheduler.scheduleTask(t);
        for (long id : removed) scheduler.cancel(AlarmScheduler.TYPE_TASK, id);
    }

    private void applyMeals(List<DocumentSnapshot> docs) {
        List<Meal> meals = new ArrayList<>();
        List<MealItem> items = new ArrayList<>();
        List<MealLog> logs = new ArrayList<>();
        Set<Long> ids = new HashSet<>();
        for (DocumentSnapshot d : docs) {
            Map<String, Object> m = data(d);
            Meal meal = CloudSchema.meal(CloudSchema.parseId(d.getId()), m);
            if (meal == null || !ids.add(meal.id)) continue;
            meals.add(meal);
            items.addAll(CloudSchema.mealItems(meal.id, m));
            for (long day : CloudSchema.days(m.get(CloudSchema.MEAL_DONE))) {
                MealLog log = new MealLog();
                log.mealId = meal.id;
                log.epochDay = day;
                logs.add(log);
            }
        }
        List<Long> removed = new ArrayList<>();
        db.runInTransaction(() -> {
            dao.upsertMeals(meals);
            removed.addAll(missing(dao.mealIds(), ids));
            dao.deleteMeals(removed);
            dao.clearMealItems();
            dao.insertMealItems(items);
            dao.clearMealLogs();
            dao.insertMealLogs(logs);
        });
        for (Meal m : meals) scheduler.scheduleMeal(m);
        for (long id : removed) scheduler.cancel(AlarmScheduler.TYPE_MEAL, id);
    }

    private void applyHabits(List<DocumentSnapshot> docs) {
        List<Habit> habits = new ArrayList<>();
        List<HabitLog> logs = new ArrayList<>();
        Set<Long> ids = new HashSet<>();
        for (DocumentSnapshot d : docs) {
            Map<String, Object> m = data(d);
            Habit h = CloudSchema.habit(CloudSchema.parseId(d.getId()), m);
            if (h == null || !ids.add(h.id)) continue;
            habits.add(h);
            for (long day : CloudSchema.days(m.get(CloudSchema.HABIT_DONE))) {
                HabitLog log = new HabitLog();
                log.habitId = h.id;
                log.epochDay = day;
                logs.add(log);
            }
        }
        db.runInTransaction(() -> {
            dao.upsertHabits(habits);
            dao.deleteHabits(missing(dao.habitIds(), ids));
            dao.clearHabitLogs();
            dao.insertHabitLogs(logs);
        });
    }

    private void applyWater(List<DocumentSnapshot> docs) {
        List<WaterLog> logs = new ArrayList<>();
        for (DocumentSnapshot d : docs) {
            Long day = CloudSchema.parseDay(d.getId());
            Object ml = d.get(CloudSchema.WATER_ML);
            if (day == null || !(ml instanceof Number)) continue;
            WaterLog log = new WaterLog();
            log.epochDay = day;
            log.ml = com.alfred.agenda.util.WaterGoal.add(0, ((Number) ml).intValue()); // 0 a 20 litros
            logs.add(log);
        }
        db.runInTransaction(() -> {
            dao.clearWater();
            dao.upsertWater(logs);
        });
    }

    /** Matérias -> módulos -> assuntos, nessa ordem (filhos sem pai são ignorados até ele chegar). */
    private void applyStudy() {
        if (lastTopics == null || lastModules == null || lastSubjects == null) return;
        List<StudyTopic> topics = new ArrayList<>();
        Set<Long> topicIds = new HashSet<>();
        for (DocumentSnapshot d : lastTopics) {
            StudyTopic t = CloudSchema.topic(CloudSchema.parseId(d.getId()), data(d));
            if (t != null && topicIds.add(t.id)) topics.add(t);
        }
        List<StudyModule> modules = new ArrayList<>();
        Set<Long> moduleIds = new HashSet<>();
        for (DocumentSnapshot d : lastModules) {
            StudyModule m = CloudSchema.module(CloudSchema.parseId(d.getId()), data(d));
            if (m != null && topicIds.contains(m.topicId) && moduleIds.add(m.id)) modules.add(m);
        }
        List<StudySubject> subjects = new ArrayList<>();
        List<StudyLog> logs = new ArrayList<>();
        Set<Long> subjectIds = new HashSet<>();
        for (DocumentSnapshot d : lastSubjects) {
            Map<String, Object> m = data(d);
            StudySubject s = CloudSchema.subject(CloudSchema.parseId(d.getId()), m);
            if (s == null || !topicIds.contains(s.topicId)) continue;
            if (s.moduleId != null && !moduleIds.contains(s.moduleId)) continue;
            if (!subjectIds.add(s.id)) continue;
            subjects.add(s);
            for (Map.Entry<Long, Long> e : CloudSchema.timeByDay(m).entrySet()) {
                StudyLog log = new StudyLog();
                log.subjectId = s.id;
                log.epochDay = e.getKey();
                log.seconds = e.getValue();
                logs.add(log);
            }
        }
        db.runInTransaction(() -> {
            dao.upsertTopics(topics);
            dao.deleteTopics(missing(dao.topicIds(), topicIds));
            dao.upsertModules(modules);
            dao.deleteModules(missing(dao.moduleIds(), moduleIds));
            dao.upsertSubjects(subjects);
            dao.deleteSubjects(missing(dao.subjectIds(), subjectIds));
            dao.clearStudyLogs();
            dao.insertStudyLogs(logs);
        });
        onStudyChanged.run();
    }

    private void applyProfile(DocumentSnapshot snap) {
        UserProfile local = UserProfile.current(app);
        if (!snap.exists()) {
            // Conta nova na nuvem (ou primeiro aparelho): o perfil deste aparelho sobe
            if (!snap.getMetadata().isFromCache() && local.onboarded) pushProfile(app, local);
            return;
        }
        applyProfileData(app, data(snap));
    }

    /** Grava neste aparelho o perfil que veio da nuvem (só se algo mudou). */
    private static void applyProfileData(Context app, Map<String, Object> m) {
        UserProfile local = UserProfile.current(app);
        UserProfile p = local.copy();
        Object name = m.get("nome");
        if (name instanceof String) p.name = (String) name;
        if (m.get("tratamento") instanceof Number) p.form = Honorific.safeForm(((Number) m.get("tratamento")).intValue());
        if (m.get("pesoKg") instanceof Number) p.weightKg = ((Number) m.get("pesoKg")).floatValue();
        if (m.get("avatar") instanceof Number) p.avatar = ((Number) m.get("avatar")).intValue();
        if (m.get("onboarding") instanceof Boolean) p.onboarded = (Boolean) m.get("onboarding");
        boolean changed = !p.name.equals(local.name) || p.form != local.form || p.weightKg != local.weightKg
                || p.avatar != local.avatar || p.onboarded != local.onboarded;
        if (changed) p.saveLocal(app);
    }

    private void applyPlan(DocumentSnapshot snap) {
        if (!snap.exists()) {
            // Plano importado neste aparelho antes de existir na nuvem: sobe
            NutritionPlan local = NutritionPlan.get(app);
            if (!snap.getMetadata().isFromCache() && local.isImported()) pushPlan(local);
            return;
        }
        Map<String, Object> m = data(snap);
        if (m.equals(lastPlan)) return;
        try {
            NutritionPlan plan = NutritionPlan.fromJson(new JSONObject(m));
            NutritionPlan.save(app, plan);
            lastPlan = m;
        } catch (JSONException | IOException e) {
            AppLog.e("Plano da nuvem inválido", e);
        }
    }

    // ===================== Ida: banco local -> nuvem =====================
    // Chamados pelo AlfredRepository na thread de banco, depois de gravar no Room.

    void pushTask(long id) {
        Task t = dao.getTask(id);
        if (t != null) doc(CloudSchema.TASKS, id).set(CloudSchema.task(t, dao.tagIdsOf(id)));
    }

    void pushTag(Tag t) {
        doc(CloudSchema.TAGS, t.id).set(CloudSchema.tag(t));
    }

    /** Tag excluída: sai das tarefas que a usavam (taskIds lidos antes do CASCADE local). */
    void deleteTag(long tagId, List<Long> taskIds) {
        WriteBatch batch = user.getFirestore().batch();
        batch.delete(doc(CloudSchema.TAGS, tagId));
        for (long taskId : taskIds) {
            batch.set(doc(CloudSchema.TASKS, taskId),
                    Collections.singletonMap("tags", FieldValue.arrayRemove(tagId)), SetOptions.merge());
        }
        batch.commit();
    }

    /** Refeição com itens (merge: as marcações de "feita" ficam como estão). */
    void pushMeal(long id) {
        Meal meal = dao.getMeal(id);
        if (meal != null) {
            doc(CloudSchema.MEALS, id).set(CloudSchema.meal(meal, dao.itemsOf(id)), SetOptions.merge());
        }
    }

    void setMealDone(long mealId, long epochDay, boolean done) {
        String day = CloudSchema.day(epochDay);
        doc(CloudSchema.MEALS, mealId).set(Collections.singletonMap(CloudSchema.MEAL_DONE,
                done ? FieldValue.arrayUnion(day) : FieldValue.arrayRemove(day)), SetOptions.merge());
    }

    void pushHabit(Habit h) {
        doc(CloudSchema.HABITS, h.id).set(CloudSchema.habit(h), SetOptions.merge());
    }

    void setHabitDone(long habitId, long epochDay, boolean done) {
        String day = CloudSchema.day(epochDay);
        doc(CloudSchema.HABITS, habitId).set(Collections.singletonMap(CloudSchema.HABIT_DONE,
                done ? FieldValue.arrayUnion(day) : FieldValue.arrayRemove(day)), SetOptions.merge());
    }

    /** +200 ml etc.: soma atômica, sem perder o que outro aparelho somou. */
    void addWater(long epochDay, int deltaMl) {
        col(CloudSchema.WATER).document(CloudSchema.day(epochDay)).set(
                Collections.singletonMap(CloudSchema.WATER_ML, FieldValue.increment(deltaMl)), SetOptions.merge());
    }

    /** "Ajustar": o total passa a ser exatamente este. */
    void setWater(long epochDay, int ml) {
        col(CloudSchema.WATER).document(CloudSchema.day(epochDay))
                .set(Collections.singletonMap(CloudSchema.WATER_ML, ml));
    }

    void pushTopic(long id) {
        StudyTopic t = dao.getTopic(id);
        if (t != null) doc(CloudSchema.TOPICS, id).set(CloudSchema.topic(t), SetOptions.merge());
    }

    void pushModule(long id) {
        StudyModule m = dao.getModule(id);
        if (m != null) doc(CloudSchema.MODULES, id).set(CloudSchema.module(m), SetOptions.merge());
    }

    /** Assunto sem o tempo (merge: "tempoPorDia" só muda por increment). */
    void pushSubject(long id) {
        StudySubject s = db.studyDao().getSubject(id);
        if (s != null) doc(CloudSchema.SUBJECTS, id).set(CloudSchema.subject(s), SetOptions.merge());
    }

    void addStudyTime(long subjectId, Map<Long, Long> secondsByDay) {
        Map<String, Object> time = new HashMap<>();
        for (Map.Entry<Long, Long> e : secondsByDay.entrySet()) {
            if (e.getValue() > 0) time.put(CloudSchema.day(e.getKey()), FieldValue.increment(e.getValue()));
        }
        if (time.isEmpty()) return;
        doc(CloudSchema.SUBJECTS, subjectId).set(
                Collections.singletonMap(CloudSchema.SUBJECT_TIME, time), SetOptions.merge());
    }

    /** Exclui vários documentos de uma vez (ex.: matéria com módulos e assuntos). */
    void deleteDocs(String collection, Collection<Long> ids) {
        if (ids.isEmpty()) return;
        WriteBatch batch = user.getFirestore().batch();
        for (long id : ids) batch.delete(doc(collection, id));
        batch.commit();
    }

    void deleteDoc(String collection, long id) {
        doc(collection, id).delete();
    }

    /** Matéria excluída: módulos e assuntos dela também (o Firestore não apaga em cascata). */
    void deleteTopic(long topicId, List<Long> moduleIds, List<Long> subjectIds) {
        WriteBatch batch = user.getFirestore().batch();
        batch.delete(doc(CloudSchema.TOPICS, topicId));
        for (long id : moduleIds) batch.delete(doc(CloudSchema.MODULES, id));
        for (long id : subjectIds) batch.delete(doc(CloudSchema.SUBJECTS, id));
        batch.commit();
    }

    void pushPlan(NutritionPlan plan) {
        try {
            col(CloudSchema.PLAN).document(CloudSchema.PLAN_DOC).set(toMap(plan.toJson()));
        } catch (JSONException e) {
            AppLog.e("Falha ao montar o plano para a nuvem", e);
        }
    }

    /**
     * Depois do expurgo local: apaga na nuvem as tarefas e os dias de água antigos e
     * regrava as marcações por dia (já sem os dias antigos) de hábitos, refeições e estudos.
     */
    void purgeRemote(List<Long> oldTaskIds, List<Long> oldWaterDays) {
        deleteDocs(CloudSchema.TASKS, oldTaskIds);
        for (long day : oldWaterDays) col(CloudSchema.WATER).document(CloudSchema.day(day)).delete();
        for (long id : dao.habitIds()) {
            doc(CloudSchema.HABITS, id).update(CloudSchema.HABIT_DONE, isoDays(dao.habitDoneDays(id)));
        }
        for (long id : dao.mealIds()) {
            doc(CloudSchema.MEALS, id).update(CloudSchema.MEAL_DONE, isoDays(dao.mealDoneDays(id)));
        }
        for (long id : dao.subjectIds()) {
            Map<String, Object> time = new HashMap<>();
            for (StudyLog l : dao.logsOf(id)) time.put(CloudSchema.day(l.epochDay), l.seconds);
            doc(CloudSchema.SUBJECTS, id).update(CloudSchema.SUBJECT_TIME, time);
        }
    }

    private static List<String> isoDays(List<Long> days) {
        List<String> out = new ArrayList<>();
        for (long d : days) out.add(CloudSchema.day(d));
        return out;
    }

    /** Conta recém-criada: os hábitos sugeridos vão direto para a nuvem (e voltam pelo listener). */
    private void seedSuggestedHabits() {
        WriteBatch batch = user.getFirestore().batch();
        for (int i = 0; i < Habit.SUGGESTIONS.length; i++) {
            Habit h = new Habit();
            h.id = CloudSchema.newId();
            h.name = Habit.SUGGESTIONS[i];
            h.position = i;
            batch.set(doc(CloudSchema.HABITS, h.id), CloudSchema.habit(h));
        }
        batch.commit();
    }

    // ===================== Perfil =====================

    /**
     * Logo depois do login: traz o perfil da nuvem ANTES de decidir entre onboarding e app.
     * Num aparelho novo, quem já tem conta não precisa responder o onboarding de novo.
     * Sem internet (ou demorando mais de alguns segundos), segue com o que houver aqui.
     *
     * @param done roda na thread principal
     */
    public static void fetchProfile(Context ctx, Runnable done) {
        String uid = AccountStore.currentCloudUid(ctx);
        if (uid == null || !CloudConfig.isAvailable(ctx)) {
            done.run();
            return;
        }
        Context app = ctx.getApplicationContext();
        android.os.Handler main = new android.os.Handler(android.os.Looper.getMainLooper());
        boolean[] finished = {false};
        Runnable finish = () -> {
            if (finished[0]) return;
            finished[0] = true;
            done.run();
        };
        main.postDelayed(finish, PROFILE_TIMEOUT_MS);
        userDoc(uid).get().addOnCompleteListener(t -> {
            if (t.isSuccessful() && t.getResult() != null && t.getResult().exists()) {
                try {
                    applyProfileData(app, data(t.getResult()));
                } catch (RuntimeException e) {
                    AppLog.e("Perfil da nuvem inválido", e);
                }
            }
            main.post(finish);
        });
    }

    private static final long PROFILE_TIMEOUT_MS = 8_000;

    /** Envia o perfil para usuarios/{uid} se a sessão for da nuvem; senão não faz nada. */
    public static void pushProfile(Context ctx, UserProfile p) {
        String uid = AccountStore.currentCloudUid(ctx);
        if (uid == null || !CloudConfig.isAvailable(ctx)) return;
        try {
            Map<String, Object> m = new HashMap<>();
            m.put("nome", p.name);
            m.put("tratamento", p.form);
            m.put("pesoKg", (double) p.weightKg);
            m.put("avatar", p.avatar);
            m.put("onboarding", p.onboarded);
            userDoc(uid).set(m, SetOptions.merge());
        } catch (RuntimeException e) {
            AppLog.e("Falha ao enviar o perfil", e);
        }
    }

    // ===================== JSON -> Map (para o plano) =====================

    static Map<String, Object> toMap(JSONObject o) throws JSONException {
        Map<String, Object> m = new HashMap<>();
        Iterator<String> keys = o.keys();
        while (keys.hasNext()) {
            String k = keys.next();
            m.put(k, toValue(o.get(k)));
        }
        return m;
    }

    @Nullable
    private static Object toValue(Object v) throws JSONException {
        if (v == JSONObject.NULL) return null;
        if (v instanceof JSONObject) return toMap((JSONObject) v);
        if (v instanceof JSONArray) {
            JSONArray a = (JSONArray) v;
            List<Object> list = new ArrayList<>();
            for (int i = 0; i < a.length(); i++) list.add(toValue(a.get(i)));
            return list;
        }
        return v;
    }
}
