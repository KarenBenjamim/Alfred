package com.alfred.agenda.data.dao;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Upsert;

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

import java.util.List;

/**
 * Operações do espelho Firestore -> banco local (CloudSync).
 *
 * Usa @Upsert (insere ou atualiza) e nunca REPLACE: o REPLACE apaga a linha antes de
 * inserir e, com as chaves estrangeiras em CASCADE, levaria junto o tempo de estudo,
 * os itens das refeições etc.
 */
@Dao
public interface SyncDao {

    /** Uma marcação por dia de algum pai (hábito, refeição ou assunto). */
    class ParentDay {
        public long parentId;
        public long epochDay;
    }

    // ---- Dias antigos (expurgo também na nuvem) ----

    @Query("SELECT habitId AS parentId, epochDay FROM habit_logs WHERE epochDay < :limitDay")
    List<ParentDay> habitDaysOlderThan(long limitDay);

    @Query("SELECT mealId AS parentId, epochDay FROM meal_logs WHERE epochDay < :limitDay")
    List<ParentDay> mealDaysOlderThan(long limitDay);

    @Query("SELECT subjectId AS parentId, epochDay FROM study_logs WHERE epochDay < :limitDay")
    List<ParentDay> studyDaysOlderThan(long limitDay);

    // ---- Tarefas e tags ----

    @Upsert
    void upsertTasks(List<Task> tasks);

    @Query("SELECT id FROM tasks")
    List<Long> taskIds();

    @Query("DELETE FROM tasks WHERE id IN (:ids)")
    void deleteTasks(List<Long> ids);

    @Query("SELECT id FROM tasks WHERE dueEpochDay < :limitDay")
    List<Long> taskIdsOlderThan(long limitDay);

    @Query("DELETE FROM task_tags")
    void clearTaskTags();

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    void insertTaskTags(List<TaskTag> links);

    @Query("SELECT tagId FROM task_tags WHERE taskId = :taskId")
    List<Long> tagIdsOf(long taskId);

    @Query("SELECT taskId FROM task_tags WHERE tagId = :tagId")
    List<Long> taskIdsWithTag(long tagId);

    @Query("SELECT * FROM tasks WHERE id = :id")
    Task getTask(long id);

    @Upsert
    void upsertTags(List<Tag> tags);

    @Query("SELECT id FROM tags")
    List<Long> tagIds();

    @Query("DELETE FROM tags WHERE id IN (:ids)")
    void deleteTags(List<Long> ids);

    // ---- Dieta ----

    @Upsert
    void upsertMeals(List<Meal> meals);

    @Query("SELECT id FROM meals")
    List<Long> mealIds();

    @Query("DELETE FROM meals WHERE id IN (:ids)")
    void deleteMeals(List<Long> ids);

    @Query("SELECT * FROM meals WHERE id = :id")
    Meal getMeal(long id);

    @Query("SELECT * FROM meal_items WHERE mealId = :mealId ORDER BY position")
    List<MealItem> itemsOf(long mealId);

    @Query("DELETE FROM meal_items")
    void clearMealItems();

    @Insert
    void insertMealItems(List<MealItem> items);

    @Query("DELETE FROM meal_logs")
    void clearMealLogs();

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    void insertMealLogs(List<MealLog> logs);


    // ---- Hábitos e água ----

    @Upsert
    void upsertHabits(List<Habit> habits);

    @Query("SELECT id FROM habits")
    List<Long> habitIds();

    @Query("DELETE FROM habits WHERE id IN (:ids)")
    void deleteHabits(List<Long> ids);

    @Query("SELECT * FROM habits WHERE id = :id")
    Habit getHabit(long id);

    @Query("DELETE FROM habit_logs")
    void clearHabitLogs();

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    void insertHabitLogs(List<HabitLog> logs);


    @Query("DELETE FROM water_logs")
    void clearWater();

    @Upsert
    void upsertWater(List<WaterLog> logs);

    @Query("SELECT * FROM water_logs")
    List<WaterLog> allWater();

    @Query("SELECT epochDay FROM water_logs WHERE epochDay < :limitDay")
    List<Long> waterDaysOlderThan(long limitDay);

    // ---- Estudos ----

    @Upsert
    void upsertTopics(List<StudyTopic> topics);

    @Query("SELECT id FROM study_topics")
    List<Long> topicIds();

    @Query("DELETE FROM study_topics WHERE id IN (:ids)")
    void deleteTopics(List<Long> ids);

    @Query("SELECT * FROM study_topics WHERE id = :id")
    StudyTopic getTopic(long id);

    @Upsert
    void upsertModules(List<StudyModule> modules);

    @Query("SELECT id FROM study_modules")
    List<Long> moduleIds();

    @Query("DELETE FROM study_modules WHERE id IN (:ids)")
    void deleteModules(List<Long> ids);

    @Query("SELECT * FROM study_modules WHERE id = :id")
    StudyModule getModule(long id);

    @Query("SELECT id FROM study_modules WHERE topicId = :topicId")
    List<Long> moduleIdsOfTopic(long topicId);

    @Upsert
    void upsertSubjects(List<StudySubject> subjects);

    @Query("SELECT id FROM study_subjects")
    List<Long> subjectIds();

    @Query("DELETE FROM study_subjects WHERE id IN (:ids)")
    void deleteSubjects(List<Long> ids);

    @Query("SELECT id FROM study_subjects WHERE topicId = :topicId")
    List<Long> subjectIdsOfTopic(long topicId);

    @Query("SELECT id FROM study_subjects WHERE moduleId = :moduleId")
    List<Long> subjectIdsOfModule(long moduleId);

    @Query("DELETE FROM study_logs")
    void clearStudyLogs();

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    void insertStudyLogs(List<StudyLog> logs);

}
