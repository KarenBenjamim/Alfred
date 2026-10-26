package com.alfred.agenda.data;

import android.content.ContentValues;
import android.content.Context;
import android.database.sqlite.SQLiteDatabase;

import androidx.annotation.NonNull;
import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import androidx.room.migration.Migration;
import androidx.sqlite.db.SupportSQLiteDatabase;

import com.alfred.agenda.data.dao.HabitDao;
import com.alfred.agenda.data.dao.StudyDao;
import com.alfred.agenda.data.dao.TagDao;
import com.alfred.agenda.data.dao.MealDao;
import com.alfred.agenda.data.dao.TaskDao;
import com.alfred.agenda.data.dao.WaterDao;
import com.alfred.agenda.data.entity.Habit;
import com.alfred.agenda.data.entity.HabitLog;
import com.alfred.agenda.data.entity.StudyLog;
import com.alfred.agenda.data.entity.StudySubject;
import com.alfred.agenda.data.entity.StudyTopic;
import com.alfred.agenda.data.entity.Tag;
import com.alfred.agenda.data.entity.TaskTag;
import com.alfred.agenda.data.entity.Meal;
import com.alfred.agenda.data.entity.MealItem;
import com.alfred.agenda.data.entity.MealLog;
import com.alfred.agenda.data.entity.Task;
import com.alfred.agenda.data.entity.WaterLog;
import com.alfred.agenda.account.AccountStore;

/**
 * Banco SQLite local. Cada conta tem o seu arquivo (AccountStore.databaseName): a 1ª conta
 * usa "alfred.db", o banco que já existia antes do login; as outras, "alfred_u<id>.db".
 *
 * IMPORTANTE para evoluir o projeto: ao mudar qualquer entidade (adicionar campo, tabela...),
 * aumente o "version" e crie uma Migration (veja as existentes como exemplo); caso contrário
 * o app fecha ao abrir em celulares que já têm a versão anterior instalada.
 */
@Database(
        entities = {Meal.class, MealItem.class, MealLog.class,
                Habit.class, HabitLog.class,
                Task.class, Tag.class, TaskTag.class,
                StudyTopic.class, StudySubject.class, StudyLog.class,
                WaterLog.class},
        version = 6,
        exportSchema = false)
public abstract class AppDatabase extends RoomDatabase {

    public abstract MealDao mealDao();

    public abstract HabitDao habitDao();

    public abstract TagDao tagDao();

    public abstract StudyDao studyDao();

    public abstract TaskDao taskDao();

    public abstract WaterDao waterDao();

    /**
     * Versão 1 -> 2: a tabela "work_tasks" virou "tasks" e ganhou as colunas
     * "category" (trabalho/pessoal) e "period" (manhã/tarde/noite).
     * As tarefas antigas viram tarefas de Trabalho, no período da manhã.
     */
    static final Migration MIGRATION_1_2 = new Migration(1, 2) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase db) {
            db.execSQL("ALTER TABLE work_tasks RENAME TO tasks");
            db.execSQL("ALTER TABLE tasks ADD COLUMN category INTEGER NOT NULL DEFAULT 0");
            db.execSQL("ALTER TABLE tasks ADD COLUMN period INTEGER NOT NULL DEFAULT 0");
        }
    };

    /**
     * Versão 2 -> 3: a aba Treino foi substituída pela aba Hábitos.
     * Remove as tabelas de treino, cria as de hábitos e cadastra os hábitos sugeridos.
     * (O SQL das tabelas novas é o mesmo que o Room gera para Habit e HabitLog.)
     */
    static final Migration MIGRATION_2_3 = new Migration(2, 3) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase db) {
            db.execSQL("DROP TABLE IF EXISTS workout_logs");
            db.execSQL("DROP TABLE IF EXISTS exercises");
            db.execSQL("DROP TABLE IF EXISTS workouts");
            db.execSQL("CREATE TABLE IF NOT EXISTS `habits` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, "
                    + "`name` TEXT NOT NULL, `position` INTEGER NOT NULL)");
            db.execSQL("CREATE TABLE IF NOT EXISTS `habit_logs` (`habitId` INTEGER NOT NULL, "
                    + "`epochDay` INTEGER NOT NULL, PRIMARY KEY(`habitId`, `epochDay`), "
                    + "FOREIGN KEY(`habitId`) REFERENCES `habits`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )");
            insertSuggestedHabits(db);
        }
    };

    /**
     * Versão 3 -> 4: plano alimentar da nutricionista. A refeição ganhou "notes" (orientação)
     * e cada item ganhou as substituições diretas e o grupo da lista de substituição.
     */
    static final Migration MIGRATION_3_4 = new Migration(3, 4) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase db) {
            db.execSQL("ALTER TABLE meals ADD COLUMN notes TEXT NOT NULL DEFAULT ''");
            db.execSQL("ALTER TABLE meal_items ADD COLUMN substitutions TEXT NOT NULL DEFAULT ''");
            db.execSQL("ALTER TABLE meal_items ADD COLUMN groupNumber INTEGER NOT NULL DEFAULT 0");
            db.execSQL("ALTER TABLE meal_items ADD COLUMN groupPortions TEXT NOT NULL DEFAULT ''");
        }
    };

    /**
     * Versão 4 -> 5: a aba Datas foi removida; entraram as tags das tarefas pessoais
     * e a aba Estudos (temas, assuntos e tempo por dia).
     * (O SQL das tabelas novas é o mesmo que o Room gera para as entidades.)
     */
    static final Migration MIGRATION_4_5 = new Migration(4, 5) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase db) {
            db.execSQL("DROP TABLE IF EXISTS important_dates");
            db.execSQL("CREATE TABLE IF NOT EXISTS `tags` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, "
                    + "`name` TEXT NOT NULL, `colorIndex` INTEGER NOT NULL)");
            db.execSQL("CREATE TABLE IF NOT EXISTS `task_tags` (`taskId` INTEGER NOT NULL, `tagId` INTEGER NOT NULL, "
                    + "PRIMARY KEY(`taskId`, `tagId`), "
                    + "FOREIGN KEY(`taskId`) REFERENCES `tasks`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE , "
                    + "FOREIGN KEY(`tagId`) REFERENCES `tags`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )");
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_task_tags_tagId` ON `task_tags` (`tagId`)");
            db.execSQL("CREATE TABLE IF NOT EXISTS `study_topics` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, "
                    + "`name` TEXT NOT NULL, `position` INTEGER NOT NULL)");
            db.execSQL("CREATE TABLE IF NOT EXISTS `study_subjects` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, "
                    + "`topicId` INTEGER NOT NULL, `name` TEXT NOT NULL, `position` INTEGER NOT NULL, "
                    + "`runningSince` INTEGER NOT NULL, "
                    + "FOREIGN KEY(`topicId`) REFERENCES `study_topics`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )");
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_study_subjects_topicId` ON `study_subjects` (`topicId`)");
            db.execSQL("CREATE TABLE IF NOT EXISTS `study_logs` (`subjectId` INTEGER NOT NULL, "
                    + "`epochDay` INTEGER NOT NULL, `seconds` INTEGER NOT NULL, PRIMARY KEY(`subjectId`, `epochDay`), "
                    + "FOREIGN KEY(`subjectId`) REFERENCES `study_subjects`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )");
        }
    };

    /**
     * Versão 5 -> 6: registro diário de água (aba Hábitos e copo da tela Início).
     * (O SQL é o mesmo que o Room gera para WaterLog.)
     */
    static final Migration MIGRATION_5_6 = new Migration(5, 6) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase db) {
            db.execSQL("CREATE TABLE IF NOT EXISTS `water_logs` (`epochDay` INTEGER NOT NULL, "
                    + "`ml` INTEGER NOT NULL, PRIMARY KEY(`epochDay`))");
        }
    };

    /** Cadastra os hábitos sugeridos (Habit.SUGGESTIONS). */
    static void insertSuggestedHabits(SupportSQLiteDatabase db) {
        for (int i = 0; i < Habit.SUGGESTIONS.length; i++) {
            ContentValues values = new ContentValues();
            values.put("name", Habit.SUGGESTIONS[i]);
            values.put("position", i);
            db.insert("habits", SQLiteDatabase.CONFLICT_NONE, values);
        }
    }

    /** Executado só na primeira vez que o banco é criado (instalação nova). */
    private static final Callback SEED_CALLBACK = new Callback() {
        @Override
        public void onCreate(@NonNull SupportSQLiteDatabase db) {
            insertSuggestedHabits(db);
        }
    };

    private static volatile AppDatabase instance;
    private static volatile String instanceName;

    /**
     * Banco do usuário logado (um por conta). Só pode ser chamado com alguém logado.
     * Ao sair da conta, forget() descarta a instância; a próxima chamada abre o banco do novo usuário.
     */
    public static AppDatabase get(Context context) {
        String name = AccountStore.databaseName(context);
        AppDatabase db = instance;
        if (db != null && name.equals(instanceName)) return db;
        synchronized (AppDatabase.class) {
            if (instance == null || !name.equals(instanceName)) {
                instance = Room.databaseBuilder(context.getApplicationContext(), AppDatabase.class, name)
                        .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6)
                        .addCallback(SEED_CALLBACK)
                        .build();
                instanceName = name;
            }
            return instance;
        }
    }

    /**
     * Esquece a instância atual (ao sair da conta) e devolve a antiga, para quem chamou
     * fechá-la depois que as gravações pendentes terminarem.
     */
    static AppDatabase forget() {
        synchronized (AppDatabase.class) {
            AppDatabase old = instance;
            instance = null;
            instanceName = null;
            return old;
        }
    }
}
