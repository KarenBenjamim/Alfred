package com.alfred.agenda.data;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;

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
import com.alfred.agenda.util.DateUtils;
import com.alfred.agenda.util.DayProgress;
import com.alfred.agenda.util.MealGroups;
import com.alfred.agenda.util.StudyTime;
import com.alfred.agenda.util.WaterGoal;

import java.io.IOException;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
import java.util.function.IntConsumer;

/**
 * Ponto único de acesso aos dados. Os ViewModels falam só com o repositório,
 * que cuida de:
 *  - executar gravações fora da thread principal (Room não permite na thread de UI);
 *  - agendar/cancelar os alarmes sempre que algo é salvo ou excluído.
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
        io.execute(() -> {
            db.runInTransaction(() -> {
                if (meal.id == 0) meal.id = mealDao.insert(meal);
                else mealDao.update(meal);
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
        });
    }

    public void deleteMeal(Meal meal) {
        io.execute(() -> {
            mealDao.delete(meal);
            scheduler.cancel(AlarmScheduler.TYPE_MEAL, meal.id);
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
        io.execute(() -> {
            try {
                NutritionPlan.save(app, plan);
                for (Meal old : mealDao.getAllSync()) scheduler.cancel(AlarmScheduler.TYPE_MEAL, old.id);
                List<Meal> saved = new ArrayList<>();
                db.runInTransaction(() -> {
                    mealDao.deleteAll();
                    for (NutritionPlan.PlanMeal pm : plan.meals) saved.add(insertPlanMeal(pm));
                });
                for (Meal m : saved) scheduler.scheduleMeal(m);
            } catch (IOException | RuntimeException e) {
                android.util.Log.e("Alfred", "Falha ao importar o plano", e);
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
        io.execute(() -> {
            if (habit.id == 0) {
                habit.position = habitDao.nextPosition();
                habit.id = habitDao.insert(habit);
            } else {
                habitDao.update(habit);
            }
        });
    }

    public void deleteHabit(Habit habit) {
        io.execute(() -> habitDao.delete(habit));
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
        }), onSuccess == null ? null : () -> onSuccess.accept(total[0]));
    }

    /** Corrige o total do dia ("Ajustar"). */
    public void setWater(long epochDay, int ml, @Nullable IntConsumer onSuccess) {
        int value = WaterGoal.add(0, ml);
        runWrite(() -> putWater(epochDay, value), onSuccess == null ? null : () -> onSuccess.accept(value));
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
        io.execute(() -> {
            if (task.id == 0) task.id = taskDao.insert(task);
            else taskDao.update(task);
            scheduler.scheduleTask(task);
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
        io.execute(() -> {
            db.runInTransaction(() -> {
                if (task.id == 0) task.id = taskDao.insert(task);
                else taskDao.update(task);
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
        });
    }

    public void deleteTask(Task task) {
        io.execute(() -> {
            taskDao.delete(task);
            scheduler.cancel(AlarmScheduler.TYPE_TASK, task.id);
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
        io.execute(() -> {
            if (tag.id == 0) tag.id = tagDao.insert(tag);
            else tagDao.update(tag);
            if (onSaved != null) main.post(() -> onSaved.accept(tag));
        });
    }

    /** Exclui a tag (ela some de todas as tarefas; as tarefas continuam). */
    public void deleteTag(Tag tag) {
        io.execute(() -> tagDao.delete(tag));
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

    /** Cria ou renomeia um tema. onSaved recebe o tema já com id (na thread principal). */
    public void saveStudyTopic(StudyTopic topic, @Nullable Consumer<StudyTopic> onSaved) {
        io.execute(() -> {
            if (topic.id == 0) {
                topic.position = studyDao.nextTopicPosition();
                topic.id = studyDao.insertTopic(topic);
            } else {
                studyDao.updateTopic(topic);
            }
            if (onSaved != null) main.post(() -> onSaved.accept(topic));
        });
    }

    /** Exclui o tema com todos os assuntos e o tempo registrado. */
    public void deleteStudyTopic(StudyTopic topic) {
        io.execute(() -> {
            studyDao.deleteTopic(topic);
            updateStudyNotificationSync();
        });
    }

    /** Cria ou renomeia um módulo. onSaved recebe o módulo já com id (na thread principal). */
    public void saveStudyModule(StudyModule module, @Nullable Consumer<StudyModule> onSaved) {
        io.execute(() -> {
            if (module.id == 0) {
                module.position = studyDao.nextModulePosition(module.topicId);
                module.id = studyDao.insertModule(module);
            } else {
                studyDao.updateModule(module);
            }
            if (onSaved != null) main.post(() -> onSaved.accept(module));
        });
    }

    /** Exclui o módulo com os assuntos e o tempo registrado neles. */
    public void deleteStudyModule(StudyModule module) {
        io.execute(() -> {
            studyDao.deleteModule(module);
            updateStudyNotificationSync();
        });
    }

    /** Marca/desmarca o assunto como concluído. @param onSuccess só se a gravação deu certo */
    public void setStudySubjectCompleted(long subjectId, boolean completed, @Nullable Runnable onSuccess) {
        runWrite(() -> studyDao.setCompleted(subjectId, completed), onSuccess);
    }

    /** Troca o módulo do assunto (dentro do mesmo tema), mantendo cronômetro e histórico. */
    public void moveStudySubject(long subjectId, long moduleId, @Nullable Runnable onSuccess) {
        runWrite(() -> studyDao.moveToModule(subjectId, moduleId), onSuccess);
    }

    public void saveStudySubject(StudySubject subject) {
        io.execute(() -> {
            if (subject.id == 0) {
                subject.position = subject.moduleId == null ? 0 : studyDao.nextSubjectPosition(subject.moduleId);
                subject.id = studyDao.insertSubject(subject);
            } else {
                // Renomear não pode apagar um cronômetro rodando nem desmarcar "concluído"
                StudySubject current = studyDao.getSubject(subject.id);
                if (current != null) {
                    subject.runningSince = current.runningSince;
                    subject.completed = current.completed;
                }
                studyDao.updateSubject(subject);
            }
            updateStudyNotificationSync();
        });
    }

    public void deleteStudySubject(StudySubject subject) {
        io.execute(() -> {
            studyDao.deleteSubject(subject);
            updateStudyNotificationSync();
        });
    }

    /**
     * Inicia o cronômetro do assunto. Só um assunto conta por vez: se outro estiver
     * rodando, ele é pausado (e o tempo dele é guardado) antes.
     */
    public void startStudyTimer(long subjectId) {
        io.execute(() -> {
            long now = System.currentTimeMillis();
            db.runInTransaction(() -> {
                for (StudySubject running : studyDao.getRunningSync()) {
                    if (running.id != subjectId) stopTimerSync(running, now);
                }
                StudySubject s = studyDao.getSubject(subjectId);
                if (s != null && !s.isRunning()) studyDao.setRunningSince(subjectId, now);
            });
            updateStudyNotificationSync();
        });
    }

    /** Pausa o cronômetro do assunto e guarda o tempo no dia (ou dias) correspondente. */
    public void stopStudyTimer(long subjectId) {
        io.execute(() -> {
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
        io.execute(this::updateStudyNotificationSync);
    }

    private void stopTimerSync(StudySubject s, long endMillis) {
        addStudyTimeSync(s.id, s.runningSince, endMillis);
        studyDao.setRunningSince(s.id, 0);
    }

    /**
     * Soma o intervalo [start, end) ao tempo do assunto, dividindo entre os dias
     * se passar da meia-noite (ex.: das 23h à 1h = 1h ontem + 1h hoje).
     */
    private void addStudyTimeSync(long subjectId, long startMillis, long endMillis) {
        Map<Long, Long> byDay = StudyTime.splitByDay(startMillis, endMillis, ZoneId.systemDefault());
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
        io.execute(() -> {
            List<Task> tasks = TestData.sampleTasks(DateUtils.today());
            List<Long> ids = taskDao.insertAll(tasks);
            for (int i = 0; i < tasks.size(); i++) {
                tasks.get(i).id = ids.get(i);
                scheduler.scheduleTask(tasks.get(i));
            }
            main.post(onDone);
        });
    }

    /** Remove todas as tarefas cujo título começa com "[Teste] ". Devolve quantas removeu. */
    public void removeTestTasks(IntConsumer onDone) {
        io.execute(() -> {
            List<Task> tasks = taskDao.getByTitlePrefix(TestData.TITLE_PREFIX);
            for (Task t : tasks) {
                taskDao.delete(t);
                scheduler.cancel(AlarmScheduler.TYPE_TASK, t.id);
            }
            main.post(() -> onDone.accept(tasks.size()));
        });
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
        io.execute(() -> {
            long limit = LocalDate.now().minusYears(PURGE_YEARS).toEpochDay();
            int[] removed = {0};
            db.runInTransaction(() -> {
                removed[0] += taskDao.deleteOlderThan(limit);
                removed[0] += mealDao.deleteLogsOlderThan(limit);
                removed[0] += habitDao.deleteLogsOlderThan(limit);
                removed[0] += studyDao.deleteLogsOlderThan(limit);
                removed[0] += waterDao.deleteOlderThan(limit);
            });
            main.post(() -> onDone.accept(removed[0]));
        });
    }

    // ===================== Progresso do dia =====================

    /**
     * Calcula o progresso de hoje direto do banco (mesmo critério da tela Início,
     * ver DayProgress). Resultado na thread principal.
     */
    public void computeTodayProgress(Consumer<DayProgress> onResult) {
        io.execute(() -> {
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
        io.execute(() -> {
            try {
                write.run();
            } catch (RuntimeException e) {
                android.util.Log.e("Alfred", "Falha ao gravar", e);
                return;
            }
            if (onSuccess != null) main.post(onSuccess);
        });
    }

    // ===================== Sair da conta =====================

    /**
     * Prepara a saída da conta: pausa o cronômetro de estudo (guardando o tempo), cancela
     * todos os lembretes deste usuário e tira a notificação fixa. onDone roda na thread
     * principal; quem chama então encerra a sessão e chama reset().
     */
    public void logout(Runnable onDone) {
        io.execute(() -> {
            try {
                stopAllStudyTimersSync();
                for (Meal m : mealDao.getAllSync()) scheduler.cancel(AlarmScheduler.TYPE_MEAL, m.id);
                for (Task t : taskDao.getAllSync()) scheduler.cancel(AlarmScheduler.TYPE_TASK, t.id);
            } catch (RuntimeException e) {
                android.util.Log.e("Alfred", "Falha ao limpar lembretes ao sair", e);
            }
            NotificationHelper.cancelStudyTimer(app);
            main.post(onDone);
        });
    }

    // ===================== Alarmes =====================

    /** Reagenda todos os lembretes em segundo plano (ao abrir o app). */
    public void rescheduleAll() {
        io.execute(this::rescheduleAllSync);
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
