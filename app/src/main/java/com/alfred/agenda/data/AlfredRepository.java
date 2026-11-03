package com.alfred.agenda.data;

import android.content.Context;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;

import com.alfred.agenda.R;
import com.alfred.agenda.account.AccountStore;
import com.alfred.agenda.account.UserProfile;
import com.alfred.agenda.cloud.CloudConfig;
import com.alfred.agenda.cloud.CloudSchema;
import com.alfred.agenda.data.dao.MealDao;
import com.alfred.agenda.data.dao.TaskDao;
import com.alfred.agenda.data.dao.HabitDao;
import com.alfred.agenda.data.dao.StudyDao;
import com.alfred.agenda.data.dao.TagDao;
import com.alfred.agenda.data.dao.WaterDao;
import com.alfred.agenda.data.entity.Meal;
import com.alfred.agenda.data.entity.MealItem;
import com.alfred.agenda.data.entity.MealLog;
import com.alfred.agenda.data.entity.MealWithItems;
import com.alfred.agenda.data.entity.Task;
import com.alfred.agenda.data.entity.Habit;
import com.alfred.agenda.data.entity.HabitLog;
import com.alfred.agenda.data.entity.StudyLog;
import com.alfred.agenda.data.entity.StudyModule;
import com.alfred.agenda.data.entity.StudySubject;
import com.alfred.agenda.data.entity.StudyTopic;
import com.alfred.agenda.data.entity.Tag;
import com.alfred.agenda.data.entity.TaskTag;
import com.alfred.agenda.data.entity.WaterLog;
import com.alfred.agenda.notifications.AlarmScheduler;
import com.alfred.agenda.notifications.NotificationHelper;
import com.alfred.agenda.util.AppLog;
import com.alfred.agenda.util.DateUtils;
import com.alfred.agenda.util.DayProgress;
import com.alfred.agenda.util.MealGroups;
import com.alfred.agenda.util.StudyTime;
import com.alfred.agenda.util.WaterGoal;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
import java.util.function.IntConsumer;

/**
 * Ponto único de acesso aos dados. Os ViewModels falam só com o repositório,
 * que cuida de:
 *  - executar gravações fora da thread principal (Room não permite na thread de UI);
 *  - agendar/cancelar os alarmes sempre que algo é salvo ou excluído;
 *  - em contas da nuvem, enviar cada gravação ao Firestore e espelhar no banco local o que
 *    muda lá (CloudSync). As telas continuam lendo do banco local, que funciona offline.
 *
 * Há uma instância por usuário logado: ao sair da conta, logout() + reset() descartam a atual
 * e a próxima chamada a get() usa o banco do novo usuário.
 */
public class AlfredRepository {

    private static volatile AlfredRepository instance;

    private final AppDatabase db;
    private final MealDao mealDao;
    private final HabitDao habitDao;
    private final TaskDao taskDao;
    private final TagDao tagDao;
    private final StudyDao studyDao;
    private final WaterDao waterDao;
    private final AlarmScheduler scheduler;
    private final Context app;
    /** Sincronização com o Firestore; null em contas locais (sem nuvem). */
    @Nullable
    private final CloudSync sync;

    /** Uma única thread de fundo: as gravações acontecem na ordem em que foram pedidas. */
    private final ExecutorService io = Executors.newSingleThreadExecutor();

    /** Para devolver resultados à thread principal (a única que pode mexer na tela). */
    private final Handler main = new Handler(Looper.getMainLooper());

    public static AlfredRepository get(Context context) {
        if (instance == null) {
            synchronized (AlfredRepository.class) {
                if (instance == null) {
                    instance = new AlfredRepository(context.getApplicationContext());
                }
            }
        }
        return instance;
    }

    /**
     * Descarta a instância do usuário que saiu. As gravações já pedidas terminam no banco
     * antigo, que é fechado em seguida; nada novo entra nele.
     */
    public static void reset() {
        synchronized (AlfredRepository.class) {
            AlfredRepository old = instance;
            instance = null;
            AppDatabase oldDb = AppDatabase.forget();
            // Os listeners da nuvem param antes da thread de banco (que eles usam) ser encerrada
            if (old != null && old.sync != null) old.sync.stop();
            if (old != null) {
                if (oldDb != null) old.io.execute(oldDb::close);
                old.io.shutdown();
            } else if (oldDb != null) {
                oldDb.close();
            }
        }
    }

    private AlfredRepository(Context app) {
        this.app = app;
        db = AppDatabase.get(app);
        mealDao = db.mealDao();
        habitDao = db.habitDao();
        taskDao = db.taskDao();
        tagDao = db.tagDao();
        studyDao = db.studyDao();
        waterDao = db.waterDao();
        scheduler = new AlarmScheduler(app);
        String uid = AccountStore.currentCloudUid(app);
        if (uid != null && CloudConfig.isAvailable(app)) {
            sync = new CloudSync(app, db, uid, scheduler, io, this::updateStudyNotificationSync);
            sync.start();
        } else {
            sync = null;
        }
    }

    /** Id para um registro novo: aleatório nas contas da nuvem (o mesmo no Firestore), 0 = automático. */
    private long newId() {
        return sync != null ? CloudSchema.newId() : 0;
    }

    // ===================== Dieta =====================

    public LiveData<List<MealWithItems>> observeMeals() {
        return mealDao.observeAll();
    }

    public LiveData<List<MealLog>> observeMealLogs(long epochDay) {
        return mealDao.observeLogs(epochDay);
    }

    /** Cria ou atualiza a refeição e substitui todos os itens dela. */
    public void saveMeal(Meal meal, List<MealItem> items) {
        runIo(() -> {
            db.runInTransaction(() -> {
                if (meal.id == 0) {
                    meal.id = newId();
                    meal.id = mealDao.insert(meal);
                } else {
                    mealDao.update(meal);
                }
                mealDao.deleteItems(meal.id);
                for (int i = 0; i < items.size(); i++) {
                    MealItem item = items.get(i);
                    item.id = 0;
                    item.mealId = meal.id;
                    item.position = i;
                }
                mealDao.insertItems(items);
            });
            scheduler.scheduleMeal(meal);
            if (sync != null) sync.pushMeal(meal.id);
        });
    }

    public void deleteMeal(Meal meal) {
        runIo(() -> {
            mealDao.delete(meal);
            scheduler.cancel(AlarmScheduler.TYPE_MEAL, meal.id);
            if (sync != null) sync.deleteDoc(CloudSchema.MEALS, meal.id);
        });
    }

    /**
     * Salva o plano importado da planilha (já conferido na prévia):
     *  - grava o arquivo do plano (grupos de substituição, orientações, água);
     *  - substitui TODAS as refeições da aba Dieta pelas do plano (as marcações de
     *    "feito" das refeições antigas vão junto) e reagenda os avisos.
     *
     * @param onSuccess roda na thread principal se tudo deu certo
     * @param onError   roda na thread principal se algo falhou
     */
    public void importPlan(NutritionPlan plan, Runnable onSuccess, Runnable onError) {
        runIo(() -> {
            try {
                NutritionPlan.save(app, plan);
                List<Long> oldIds = new ArrayList<>();
                for (Meal old : mealDao.getAllSync()) {
                    scheduler.cancel(AlarmScheduler.TYPE_MEAL, old.id);
                    oldIds.add(old.id);
                }
                List<Meal> saved = new ArrayList<>();
                db.runInTransaction(() -> {
                    mealDao.deleteAll();
                    for (NutritionPlan.PlanMeal pm : plan.meals) saved.add(insertPlanMeal(pm));
                });
                for (Meal m : saved) scheduler.scheduleMeal(m);
                if (sync != null) {
                    sync.pushPlan(plan);
                    sync.deleteDocs(CloudSchema.MEALS, oldIds);
                    for (Meal m : saved) sync.pushMeal(m.id);
                }
            } catch (IOException | RuntimeException e) {
                AppLog.e("Falha ao importar o plano", e);
                main.post(onError);
                return;
            }
            main.post(onSuccess);
        });
    }

    private Meal insertPlanMeal(NutritionPlan.PlanMeal pm) {
        Meal meal = new Meal();
        meal.name = pm.name;
        meal.timeMinutes = pm.timeMinutes;
        meal.notes = pm.notes;
        // Várias opções no mesmo horário: só a 1ª avisa, para não tocar 3 vezes às 17h
        meal.notifyEnabled = !pm.isAlternativeOption();
        meal.id = newId();
        meal.id = mealDao.insert(meal);

        List<MealItem> items = new ArrayList<>();
        for (int i = 0; i < pm.items.size(); i++) {
            NutritionPlan.PlanItem pi = pm.items.get(i);
            MealItem item = new MealItem();
            item.mealId = meal.id;
            item.position = i;
            item.description = pi.food.food;
            item.quantity = pi.food.amount();
            item.substitutions = String.join("\n", pi.substitutions);
            item.groupNumber = pi.group;
            item.groupPortions = pi.portions;
            items.add(item);
        }
        mealDao.insertItems(items);
        return meal;
    }

    public void setMealDone(long mealId, long epochDay, boolean done) {
        setMealDone(mealId, epochDay, done, null);
    }

    /** @param onSuccess roda na thread principal SÓ se a gravação deu certo (pode ser null) */
    public void setMealDone(long mealId, long epochDay, boolean done, @Nullable Runnable onSuccess) {
        runWrite(() -> {
            if (done) {
                MealLog log = new MealLog();
                log.mealId = mealId;
                log.epochDay = epochDay;
                mealDao.insertLog(log);
            } else {
                mealDao.deleteLog(mealId, epochDay);
            }
            if (sync != null) sync.setMealDone(mealId, epochDay, done);
        }, onSuccess);
    }

    // ===================== Hábitos =====================

    public LiveData<List<Habit>> observeHabits() {
        return habitDao.observeAll();
    }

    /** Registros de hábitos concluídos entre dois dias (inclusive). */
    public LiveData<List<HabitLog>> observeHabitLogs(long fromDay, long toDay) {
        return habitDao.observeLogs(fromDay, toDay);
    }

    public void saveHabit(Habit habit) {
        runIo(() -> {
            if (habit.id == 0) {
                habit.position = habitDao.nextPosition();
                habit.id = newId();
                habit.id = habitDao.insert(habit);
            } else {
                habitDao.update(habit);
            }
            if (sync != null) sync.pushHabit(habit);
        });
    }

    public void deleteHabit(Habit habit) {
        runIo(() -> {
            habitDao.delete(habit);
            if (sync != null) sync.deleteDoc(CloudSchema.HABITS, habit.id);
        });
    }

    public void setHabitDone(long habitId, long epochDay, boolean done) {
        setHabitDone(habitId, epochDay, done, null);
    }

    /** @param onSuccess roda na thread principal SÓ se a gravação deu certo (pode ser null) */
    public void setHabitDone(long habitId, long epochDay, boolean done, @Nullable Runnable onSuccess) {
        runWrite(() -> {
            if (done) {
                HabitLog log = new HabitLog();
                log.habitId = habitId;
                log.epochDay = epochDay;
                habitDao.insertLog(log);
            } else {
                habitDao.deleteLog(habitId, epochDay);
            }
            if (sync != null) sync.setHabitDone(habitId, epochDay, done);
        }, onSuccess);
    }

    // ===================== Água =====================

    public LiveData<WaterLog> observeWater(long epochDay) {
        return waterDao.observe(epochDay);
    }

    /**
     * Soma (ou subtrai, com deltaMl negativo) ao total de água do dia.
     * @param onSuccess recebe o novo total, na thread principal, só se a gravação deu certo
     */
    public void addWater(long epochDay, int deltaMl, @Nullable IntConsumer onSuccess) {
        int[] total = {0};
        runWrite(() -> db.runInTransaction(() -> {
            WaterLog log = waterDao.getSync(epochDay);
            int current = log == null ? 0 : log.ml;
            total[0] = WaterGoal.add(current, deltaMl);
            putWater(epochDay, total[0]);
            // Na nuvem, a soma é atômica (soma lá exatamente o que mudou aqui, já limitado)
            if (sync != null) sync.addWater(epochDay, total[0] - current);
        }), onSuccess == null ? null : () -> onSuccess.accept(total[0]));
    }

    /** Corrige o total do dia ("Ajustar"). */
    public void setWater(long epochDay, int ml, @Nullable IntConsumer onSuccess) {
        int value = WaterGoal.add(0, ml);
        runWrite(() -> {
            putWater(epochDay, value);
            if (sync != null) sync.setWater(epochDay, value);
        }, onSuccess == null ? null : () -> onSuccess.accept(value));
    }

    private void putWater(long epochDay, int ml) {
        WaterLog log = new WaterLog();
        log.epochDay = epochDay;
        log.ml = ml;
        waterDao.put(log);
    }

    // ===================== Tarefas (Trabalho e Pessoal) =====================

    /** @param category Task.CATEGORY_WORK ou Task.CATEGORY_PERSONAL */
    public LiveData<List<Task>> observeTasks(int category) {
        return taskDao.observeByCategory(category);
    }

    public LiveData<List<Task>> observeTasksForToday(long epochDay) {
        return taskDao.observeForToday(epochDay);
    }

    /** A tarefa pendente de MAIOR prioridade da categoria (para a tela Início). */
    public LiveData<Task> observeTopPendingTask(int category) {
        return taskDao.observeTopPending(category);
    }

    public void saveTask(Task task) {
        runIo(() -> {
            if (task.id == 0) {
                task.id = newId();
                task.id = taskDao.insert(task);
            } else {
                taskDao.update(task);
            }
            scheduler.scheduleTask(task);
            if (sync != null) sync.pushTask(task.id);
        });
    }

    /** Marca/desmarca como concluída. Ao desmarcar, volta para "Pendente". */
    public void setTaskDone(Task original, boolean done) {
        setTaskDone(original, done, null);
    }

    /** @param onSuccess roda na thread principal SÓ se a gravação deu certo (pode ser null) */
    public void setTaskDone(Task original, boolean done, @Nullable Runnable onSuccess) {
        Task task = original.copy();
        task.status = done ? Task.STATUS_DONE : Task.STATUS_PENDING;
        runWrite(() -> {
            taskDao.update(task);
            scheduler.scheduleTask(task);
            if (sync != null) sync.pushTask(task.id);
        }, onSuccess);
    }

    /**
     * Salva a tarefa e troca as tags dela pelas de tagIds.
     * Com tagIds = null, as tags não são alteradas (ex.: tarefas de Trabalho).
     */
    public void saveTask(Task task, @Nullable Set<Long> tagIds) {
        if (tagIds == null) {
            saveTask(task);
            return;
        }
        runIo(() -> {
            db.runInTransaction(() -> {
                if (task.id == 0) {
                    task.id = newId();
                    task.id = taskDao.insert(task);
                } else {
                    taskDao.update(task);
                }
                tagDao.clearTaskTags(task.id);
                List<TaskTag> links = new ArrayList<>();
                for (long tagId : tagIds) {
                    TaskTag link = new TaskTag();
                    link.taskId = task.id;
                    link.tagId = tagId;
                    links.add(link);
                }
                tagDao.insertTaskTags(links);
            });
            scheduler.scheduleTask(task);
            if (sync != null) sync.pushTask(task.id);
        });
    }

    public void deleteTask(Task task) {
        runIo(() -> {
            taskDao.delete(task);
            scheduler.cancel(AlarmScheduler.TYPE_TASK, task.id);
            if (sync != null) sync.deleteDoc(CloudSchema.TASKS, task.id);
        });
    }

    // ===================== Tags (tarefas pessoais) =====================

    public LiveData<List<Tag>> observeTags() {
        return tagDao.observeAll();
    }

    public LiveData<List<TaskTag>> observeTaskTags() {
        return tagDao.observeTaskTags();
    }

    /** Cria ou atualiza a tag. onSaved recebe a tag já com id (na thread principal). */
    public void saveTag(Tag tag, @Nullable Consumer<Tag> onSaved) {
        runIo(() -> {
            if (tag.id == 0) {
                tag.id = newId();
                tag.id = tagDao.insert(tag);
            } else {
                tagDao.update(tag);
            }
            if (sync != null) sync.pushTag(tag);
            if (onSaved != null) main.post(() -> onSaved.accept(tag));
        });
    }

    /** Exclui a tag (ela some de todas as tarefas; as tarefas continuam). */
    public void deleteTag(Tag tag) {
        runIo(() -> {
            // Tarefas que usavam a tag: lidas antes, porque o CASCADE local apaga as ligações
            List<Long> tasks = sync != null ? db.syncDao().taskIdsWithTag(tag.id) : null;
            tagDao.delete(tag);
            if (sync != null) sync.deleteTag(tag.id, tasks);
        });
    }

    // ===================== Estudos =====================

    public LiveData<List<StudyTopic>> observeStudyTopics() {
        return studyDao.observeTopics();
    }

    public LiveData<List<StudyModule>> observeStudyModules() {
        return studyDao.observeModules();
    }

    public LiveData<List<StudySubject>> observeStudySubjects() {
        return studyDao.observeSubjects();
    }

    public LiveData<List<StudyLog>> observeStudyLogsSince(long fromDay) {
        return studyDao.observeLogsSince(fromDay);
    }

    public LiveData<List<StudyLog>> observeStudyLogsForSubject(long subjectId) {
        return studyDao.observeLogsForSubject(subjectId);
    }

    /** Cria um tema. onSaved recebe o tema já com id (na thread principal). */
    public void saveStudyTopic(StudyTopic topic, @Nullable Consumer<StudyTopic> onSaved) {
        runIo(() -> {
            topic.position = studyDao.nextTopicPosition();
            topic.id = newId();
            topic.id = studyDao.insertTopic(topic);
            if (sync != null) sync.pushTopic(topic.id);
            if (onSaved != null) main.post(() -> onSaved.accept(topic));
        });
    }

    /** Exclui o tema com todos os assuntos e o tempo registrado. */
    public void deleteStudyTopic(StudyTopic topic) {
        runIo(() -> {
            // Filhos lidos antes do CASCADE local: na nuvem não há cascata
            List<Long> modules = sync != null ? db.syncDao().moduleIdsOfTopic(topic.id) : null;
            List<Long> subjects = sync != null ? db.syncDao().subjectIdsOfTopic(topic.id) : null;
            studyDao.deleteTopic(topic);
            updateStudyNotificationSync();
            if (sync != null) sync.deleteTopic(topic.id, modules, subjects);
        });
    }

    /** Cria um módulo. onSaved recebe o módulo já com id (na thread principal). */
    public void saveStudyModule(StudyModule module, @Nullable Consumer<StudyModule> onSaved) {
        runIo(() -> {
            module.position = studyDao.nextModulePosition(module.topicId);
            module.id = newId();
            module.id = studyDao.insertModule(module);
            if (sync != null) sync.pushModule(module.id);
            if (onSaved != null) main.post(() -> onSaved.accept(module));
        });
    }

    /** Exclui o módulo com os assuntos e o tempo registrado neles. */
    public void deleteStudyModule(StudyModule module) {
        runIo(() -> {
            List<Long> subjects = sync != null ? db.syncDao().subjectIdsOfModule(module.id) : null;
            studyDao.deleteModule(module);
            updateStudyNotificationSync();
            if (sync != null) {
                sync.deleteDoc(CloudSchema.MODULES, module.id);
                sync.deleteDocs(CloudSchema.SUBJECTS, subjects);
            }
        });
    }

    /** Marca/desmarca o assunto como concluído. @param onSuccess só se a gravação deu certo */
    public void setStudySubjectCompleted(long subjectId, boolean completed, @Nullable Runnable onSuccess) {
        runWrite(() -> {
            studyDao.setCompleted(subjectId, completed);
            if (sync != null) sync.pushSubject(subjectId);
        }, onSuccess);
    }

    /** Muda nome e cor da matéria (só esses campos). */
    public void editStudyTopic(long topicId, String name, int colorIndex) {
        runWrite(() -> {
            studyDao.editTopic(topicId, name, colorIndex);
            if (sync != null) sync.pushTopic(topicId);
        }, null);
    }

    public void renameStudyModule(long moduleId, String name) {
        runWrite(() -> {
            studyDao.renameModule(moduleId, name);
            if (sync != null) sync.pushModule(moduleId);
        }, null);
    }

    /** Renomeia o assunto; a notificação do cronômetro (se for ele que está rodando) acompanha. */
    public void renameStudySubject(long subjectId, String name) {
        runWrite(() -> {
            studyDao.renameSubject(subjectId, name);
            updateStudyNotificationSync();
            if (sync != null) sync.pushSubject(subjectId);
        }, null);
    }

    /** Troca o módulo do assunto (dentro do mesmo tema), mantendo cronômetro e histórico. */
    public void moveStudySubject(long subjectId, long moduleId, @Nullable Runnable onSuccess) {
        runWrite(() -> {
            studyDao.moveToModule(subjectId, moduleId);
            if (sync != null) sync.pushSubject(subjectId);
        }, onSuccess);
    }

    /** Cria um assunto no fim do módulo. */
    public void saveStudySubject(StudySubject subject) {
        runIo(() -> {
            subject.position = subject.moduleId == null ? 0 : studyDao.nextSubjectPosition(subject.moduleId);
            subject.id = newId();
            subject.id = studyDao.insertSubject(subject);
            if (sync != null) sync.pushSubject(subject.id);
        });
    }

    public void deleteStudySubject(StudySubject subject) {
        runIo(() -> {
            studyDao.deleteSubject(subject);
            updateStudyNotificationSync();
            if (sync != null) sync.deleteDoc(CloudSchema.SUBJECTS, subject.id);
        });
    }

    /**
     * Inicia o cronômetro do assunto. Só um assunto conta por vez: se outro estiver
     * rodando, ele é pausado (e o tempo dele é guardado) antes.
     */
    public void startStudyTimer(long subjectId) {
        runIo(() -> {
            long now = System.currentTimeMillis();
            db.runInTransaction(() -> {
                for (StudySubject running : studyDao.getRunningSync()) {
                    if (running.id != subjectId) stopTimerSync(running, now);
                }
                StudySubject s = studyDao.getSubject(subjectId);
                if (s != null && !s.isRunning()) {
                    studyDao.setRunningSince(subjectId, now);
                    if (sync != null) sync.pushSubject(subjectId); // a web vê o cronômetro rodando
                }
            });
            updateStudyNotificationSync();
        });
    }

    /** Pausa o cronômetro do assunto e guarda o tempo no dia (ou dias) correspondente. */
    public void stopStudyTimer(long subjectId) {
        runIo(() -> {
            long now = System.currentTimeMillis();
            db.runInTransaction(() -> {
                StudySubject s = studyDao.getSubject(subjectId);
                if (s != null && s.isRunning()) stopTimerSync(s, now);
            });
            updateStudyNotificationSync();
        });
    }

    /** Pausa qualquer cronômetro rodando (usado pelo botão "Pausar" da notificação). */
    public void stopAllStudyTimersSync() {
        long now = System.currentTimeMillis();
        db.runInTransaction(() -> {
            for (StudySubject s : studyDao.getRunningSync()) stopTimerSync(s, now);
        });
        updateStudyNotificationSync();
    }

    /** Mostra (ou tira) a notificação fixa do cronômetro conforme o que está rodando. */
    public void refreshStudyNotification() {
        runIo(this::updateStudyNotificationSync);
    }

    private void stopTimerSync(StudySubject s, long endMillis) {
        addStudyTimeSync(s.id, s.runningSince, endMillis);
        studyDao.setRunningSince(s.id, 0);
        if (sync != null) sync.pushSubject(s.id);
    }

    /**
     * Soma o intervalo [start, end) ao tempo do assunto, dividindo entre os dias
     * se passar da meia-noite (ex.: das 23h à 1h = 1h ontem + 1h hoje).
     */
    private void addStudyTimeSync(long subjectId, long startMillis, long endMillis) {
        Map<Long, Long> byDay = StudyTime.splitByDay(startMillis, endMillis, ZoneId.systemDefault());
        if (sync != null) sync.addStudyTime(subjectId, byDay); // soma atômica na nuvem
        for (Map.Entry<Long, Long> e : byDay.entrySet()) {
            StudyLog log = studyDao.getLog(subjectId, e.getKey());
            if (log == null) {
                log = new StudyLog();
                log.subjectId = subjectId;
                log.epochDay = e.getKey();
            }
            log.seconds += e.getValue();
            studyDao.putLog(log);
        }
    }

    private void updateStudyNotificationSync() {
        List<StudySubject> running = studyDao.getRunningSync();
        if (running.isEmpty()) NotificationHelper.cancelStudyTimer(app);
        else NotificationHelper.showStudyTimer(app, running.get(0).name, running.get(0).runningSince);
    }

    // ===================== Tarefas de teste =====================

    /** Cadastra as tarefas de exemplo (TestData). onDone roda na thread principal. */
    public void insertTestTasks(Runnable onDone) {
        runIo(() -> {
            List<Task> tasks = TestData.sampleTasks(DateUtils.today());
            for (Task t : tasks) t.id = newId();
            List<Long> ids = taskDao.insertAll(tasks);
            for (int i = 0; i < tasks.size(); i++) {
                tasks.get(i).id = ids.get(i);
                scheduler.scheduleTask(tasks.get(i));
                if (sync != null) sync.pushTask(tasks.get(i).id);
            }
            main.post(onDone);
        });
    }

    /** Remove todas as tarefas cujo título começa com "[Teste] ". Devolve quantas removeu. */
    public void removeTestTasks(IntConsumer onDone) {
        runIo(() -> {
            List<Task> tasks = taskDao.getByTitlePrefix(TestData.TITLE_PREFIX);
            for (Task t : tasks) {
                taskDao.delete(t);
                scheduler.cancel(AlarmScheduler.TYPE_TASK, t.id);
                if (sync != null) sync.deleteDoc(CloudSchema.TASKS, t.id);
            }
            main.post(() -> onDone.accept(tasks.size()));
        });
    }

    // ===================== Backup local =====================

    /**
     * Grava o backup completo da conta (LocalBackup) no arquivo escolhido pela usuária.
     * onSuccess/onError rodam na thread principal.
     */
    public void exportBackup(Uri target, @Nullable String appVersion, Runnable onSuccess, Runnable onError) {
        runIo(() -> {
            try (OutputStream out = app.getContentResolver().openOutputStream(target, "wt")) {
                if (out == null) throw new IOException("Destino indisponível");
                byte[] json = LocalBackup.build(app, db, appVersion).toString(2).getBytes(StandardCharsets.UTF_8);
                out.write(json);
            } catch (IOException | JSONException | RuntimeException e) {
                AppLog.e("Falha ao exportar o backup", e);
                main.post(onError);
                return;
            }
            main.post(onSuccess);
        });
    }

    /** Backup de uma conta local antiga deste aparelho (antes de migrá-la para a nuvem). */
    public void exportLegacyBackup(Uri target, int legacyId, String username, @Nullable String appVersion,
                                   Runnable onSuccess, Runnable onError) {
        runIo(() -> {
            AppDatabase legacyDb = AppDatabase.openForAccount(app, legacyId);
            try (OutputStream out = app.getContentResolver().openOutputStream(target, "wt")) {
                if (out == null) throw new IOException("Destino indisponível");
                JSONObject backup = LocalBackup.build(legacyDb, legacyId, username, UserProfile.forAccount(app, legacyId),
                        NutritionPlan.forAccount(app, legacyId), appVersion);
                out.write(backup.toString(2).getBytes(StandardCharsets.UTF_8));
            } catch (IOException | JSONException | RuntimeException e) {
                AppLog.e("Falha ao exportar o backup da conta antiga", e);
                main.post(onError);
                return;
            } finally {
                legacyDb.close();
            }
            main.post(onSuccess);
        });
    }

    // ===================== Migração de conta antiga para a nuvem =====================

    /** Resultado da migração: result != null deu certo; senão errorRes diz o motivo. */
    public interface MigrationCallback {
        void done(@Nullable LegacyMigration.Result result, int errorRes);
    }

    /**
     * Confere a senha da conta local antiga e envia os dados dela para a conta da nuvem logada.
     * Roda numa thread própria: espera a confirmação do servidor (até 1 min por lote) sem
     * travar a fila de gravações nem a sincronização.
     */
    public void migrateLegacyAccount(int legacyId, String password, @Nullable String appVersion,
                                     MigrationCallback cb) {
        String uid = AccountStore.currentCloudUid(app);
        if (uid == null || sync == null) {
            cb.done(null, R.string.migration_error_not_cloud);
            return;
        }
        new Thread(() -> {
            LegacyMigration.Result result = null;
            int error = 0;
            try {
                if (!AccountStore.checkLegacyPassword(app, legacyId, password)) {
                    error = R.string.auth_error_wrong;
                } else {
                    Map<String, Long> habits = new HashMap<>();
                    for (Habit h : habitDao.getAllSync()) habits.put(LegacyMigration.normalize(h.name), h.id);
                    result = LegacyMigration.run(app, legacyId, uid, habits, appVersion);
                }
            } catch (java.util.concurrent.TimeoutException e) {
                error = R.string.migration_error_timeout;
            } catch (Exception e) {
                AppLog.e("Falha na migração", e);
                error = R.string.migration_error;
            }
            LegacyMigration.Result r = result;
            int err = error;
            main.post(() -> cb.done(r, err));
        }, "alfred-migracao").start();
    }

    /** Arquivos de backup maiores que isto são recusados (protege a memória do celular). */
    private static final long MAX_BACKUP_BYTES = 20L * 1024 * 1024;

    /**
     * Importa um arquivo de backup (.json exportado pelo app) para a conta da nuvem logada.
     * Roda numa thread própria e espera a confirmação do servidor, como a migração.
     */
    public void importBackupFile(Uri source, MigrationCallback cb) {
        String uid = AccountStore.currentCloudUid(app);
        if (uid == null || sync == null) {
            cb.done(null, R.string.migration_error_not_cloud);
            return;
        }
        new Thread(() -> {
            LegacyMigration.Result result = null;
            int error = 0;
            try {
                JSONObject backup = new JSONObject(readText(source));
                LegacyMigration.validate(backup);
                Map<String, Long> habits = new HashMap<>();
                for (Habit h : habitDao.getAllSync()) habits.put(LegacyMigration.normalize(h.name), h.id);
                result = LegacyMigration.importBackup(backup, uid, habits);
            } catch (LegacyMigration.InvalidBackup | JSONException | IOException e) {
                AppLog.e("Backup inválido", e);
                error = R.string.backup_import_invalid;
            } catch (java.util.concurrent.TimeoutException e) {
                error = R.string.migration_error_timeout;
            } catch (Exception e) {
                AppLog.e("Falha ao importar o backup", e);
                error = R.string.migration_error;
            }
            LegacyMigration.Result r = result;
            int err = error;
            main.post(() -> cb.done(r, err));
        }, "alfred-importar-backup").start();
    }

    /** Lê o arquivo escolhido como texto UTF-8, com limite de tamanho. */
    private String readText(Uri source) throws IOException {
        try (java.io.InputStream in = app.getContentResolver().openInputStream(source)) {
            if (in == null) throw new IOException("Arquivo indisponível");
            java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            long total = 0;
            int n;
            while ((n = in.read(buf)) > 0) {
                total += n;
                if (total > MAX_BACKUP_BYTES) throw new IOException("Arquivo grande demais");
                out.write(buf, 0, n);
            }
            return new String(out.toByteArray(), StandardCharsets.UTF_8);
        }
    }

    // ===================== Expurgo =====================

    /** Tudo o que for mais antigo que isto é apagado. */
    public static final int PURGE_YEARS = 2;

    /**
     * "Teoria do expurgo": apaga o que tem mais de 2 anos:
     *  - tarefas (Trabalho e Pessoal) com entrega antiga;
     *  - registros de refeições e hábitos concluídos;
     *  - tempo de estudo registrado por dia e água bebida por dia;
     * Refeições, hábitos e assuntos de estudo cadastrados não têm "idade", então ficam.
     *
     * @param onDone recebe o total de registros apagados (na thread principal)
     */
    public void purgeOld(IntConsumer onDone) {
        runIo(() -> {
            long limit = LocalDate.now().minusYears(PURGE_YEARS).toEpochDay();
            // O que vai sumir também precisa sumir na nuvem (lido antes de apagar aqui)
            List<Long> oldTasks = sync != null ? db.syncDao().taskIdsOlderThan(limit) : null;
            List<Long> oldWater = sync != null ? db.syncDao().waterDaysOlderThan(limit) : null;
            int[] removed = {0};
            db.runInTransaction(() -> {
                removed[0] += taskDao.deleteOlderThan(limit);
                removed[0] += mealDao.deleteLogsOlderThan(limit);
                removed[0] += habitDao.deleteLogsOlderThan(limit);
                removed[0] += studyDao.deleteLogsOlderThan(limit);
                removed[0] += waterDao.deleteOlderThan(limit);
            });
            if (sync != null && removed[0] > 0) sync.purgeRemote(oldTasks, oldWater);
            main.post(() -> onDone.accept(removed[0]));
        });
    }

    // ===================== Progresso do dia =====================

    /**
     * Calcula o progresso de hoje direto do banco (mesmo critério da tela Início,
     * ver DayProgress). Resultado na thread principal.
     */
    public void computeTodayProgress(Consumer<DayProgress> onResult) {
        runIo(() -> {
            long today = DateUtils.today();
            Set<Long> doneMeals = new HashSet<>();
            for (MealLog l : mealDao.getLogsSync(today)) doneMeals.add(l.mealId);
            Set<Long> doneHabits = new HashSet<>();
            for (HabitLog l : habitDao.getLogsSync(today)) doneHabits.add(l.habitId);
            Set<Long> studied = new HashSet<>();
            for (StudyLog l : studyDao.getLogsForDaySync(today)) if (l.seconds > 0) studied.add(l.subjectId);
            for (StudySubject s : studyDao.getRunningSync()) studied.add(s.id);

            DayProgress progress = DayProgress.compute(today, taskDao.getForDaySync(today),
                    habitDao.getAllSync(), doneHabits,
                    MealGroups.group(mealDao.getAllWithItemsSync()), doneMeals,
                    studyDao.getSubjectsSync(), studied);
            main.post(() -> onResult.accept(progress));
        });
    }

    /**
     * Executa uma gravação em segundo plano e, só se ela der certo, chama onSuccess
     * na thread principal. Se der erro, nada de mensagem de sucesso.
     */
    private void runWrite(Runnable write, @Nullable Runnable onSuccess) {
        runIo(() -> {
            write.run();
            if (onSuccess != null) main.post(onSuccess);
        });
    }

    /**
     * Roda o trabalho na thread de fundo sem nunca derrubar o app: um erro do banco (disco
     * cheio, assunto excluído no meio do caminho...) é registrado e a operação é abandonada,
     * sem chamar os callbacks de sucesso. Depois de sair da conta (reset), a thread já foi
     * encerrada e pedidos atrasados de telas antigas são simplesmente ignorados.
     */
    private void runIo(Runnable work) {
        try {
            io.execute(() -> {
                try {
                    work.run();
                } catch (RuntimeException e) {
                    AppLog.e("Falha ao gravar", e);
                }
            });
        } catch (RejectedExecutionException e) {
            AppLog.e("Gravação pedida depois de sair da conta", e);
        }
    }

    // ===================== Sair da conta =====================

    /**
     * Prepara a saída da conta: pausa o cronômetro de estudo (guardando o tempo), cancela
     * todos os lembretes deste usuário e tira a notificação fixa. onDone roda na thread
     * principal; quem chama então encerra a sessão e chama reset().
     */
    public void logout(Runnable onDone) {
        runIo(() -> {
            try {
                stopAllStudyTimersSync();
                for (Meal m : mealDao.getAllSync()) scheduler.cancel(AlarmScheduler.TYPE_MEAL, m.id);
                for (Task t : taskDao.getAllSync()) scheduler.cancel(AlarmScheduler.TYPE_TASK, t.id);
            } catch (RuntimeException e) {
                AppLog.e("Falha ao limpar lembretes ao sair", e);
            }
            NotificationHelper.cancelStudyTimer(app);
            main.post(onDone);
        });
    }

    // ===================== Alarmes =====================

    /** Reagenda todos os lembretes em segundo plano (ao abrir o app). */
    public void rescheduleAll() {
        runIo(this::rescheduleAllSync);
    }

    /**
     * Reagenda todos os lembretes na thread atual. Usado após reiniciar o celular,
     * pois o Android apaga todos os alarmes quando desliga.
     */
    public void rescheduleAllSync() {
        for (Meal m : mealDao.getAllSync()) scheduler.scheduleMeal(m);
        for (Task t : taskDao.getAllSync()) scheduler.scheduleTask(t);
    }
}
