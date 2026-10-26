package com.alfred.agenda.data.dao;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Transaction;
import androidx.room.Update;

import com.alfred.agenda.data.entity.Meal;
import com.alfred.agenda.data.entity.MealItem;
import com.alfred.agenda.data.entity.MealLog;
import com.alfred.agenda.data.entity.MealWithItems;

import java.util.List;

/**
 * Consultas da dieta. Métodos que retornam LiveData rodam sozinhos em segundo plano
 * e se atualizam quando o banco muda; os demais precisam ser chamados fora da thread principal.
 */
@Dao
public interface MealDao {

    @Transaction
    @Query("SELECT * FROM meals ORDER BY timeMinutes, name")
    LiveData<List<MealWithItems>> observeAll();

    @Query("SELECT * FROM meals")
    List<Meal> getAllSync();

    @Transaction
    @Query("SELECT * FROM meals ORDER BY timeMinutes, name")
    List<MealWithItems> getAllWithItemsSync();

    @Query("SELECT * FROM meal_logs WHERE epochDay = :epochDay")
    List<MealLog> getLogsSync(long epochDay);

    @Query("SELECT * FROM meals WHERE id = :id")
    Meal getById(long id);

    @Insert
    long insert(Meal meal);

    @Update
    void update(Meal meal);

    @Delete
    void delete(Meal meal);

    @Insert
    void insertItems(List<MealItem> items);

    @Query("DELETE FROM meal_items WHERE mealId = :mealId")
    void deleteItems(long mealId);

    /** Apaga todas as refeições (os itens e registros vão junto, por CASCADE). */
    @Query("DELETE FROM meals")
    void deleteAll();

    @Query("SELECT COUNT(*) FROM meals")
    int count();

    // ---- Registro de refeições feitas ----

    @Query("SELECT * FROM meal_logs WHERE epochDay = :epochDay")
    LiveData<List<MealLog>> observeLogs(long epochDay);

    @Query("SELECT COUNT(*) > 0 FROM meal_logs WHERE mealId = :mealId AND epochDay = :epochDay")
    boolean isDone(long mealId, long epochDay);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertLog(MealLog log);

    @Query("DELETE FROM meal_logs WHERE mealId = :mealId AND epochDay = :epochDay")
    void deleteLog(long mealId, long epochDay);

    /** Expurgo: apaga registros de refeições feitas antes de :limitDay. */
    @Query("DELETE FROM meal_logs WHERE epochDay < :limitDay")
    int deleteLogsOlderThan(long limitDay);
}
