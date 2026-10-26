package com.alfred.agenda.data.dao;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

import com.alfred.agenda.data.entity.Habit;
import com.alfred.agenda.data.entity.HabitLog;

import java.util.List;

@Dao
public interface HabitDao {

    @Query("SELECT * FROM habits ORDER BY position, id")
    LiveData<List<Habit>> observeAll();

    @Query("SELECT * FROM habits ORDER BY position, id")
    List<Habit> getAllSync();

    @Query("SELECT * FROM habit_logs WHERE epochDay = :epochDay")
    List<HabitLog> getLogsSync(long epochDay);

    /** Registros de um intervalo de dias (ex.: a semana atual, de segunda a domingo). */
    @Query("SELECT * FROM habit_logs WHERE epochDay BETWEEN :fromDay AND :toDay")
    LiveData<List<HabitLog>> observeLogs(long fromDay, long toDay);

    /** Posição para um hábito novo: depois do último. */
    @Query("SELECT COALESCE(MAX(position), -1) + 1 FROM habits")
    int nextPosition();

    @Insert
    long insert(Habit habit);

    @Update
    void update(Habit habit);

    @Delete
    void delete(Habit habit);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertLog(HabitLog log);

    @Query("DELETE FROM habit_logs WHERE habitId = :habitId AND epochDay = :epochDay")
    void deleteLog(long habitId, long epochDay);

    /** Expurgo: apaga registros de hábitos feitos antes de :limitDay. */
    @Query("DELETE FROM habit_logs WHERE epochDay < :limitDay")
    int deleteLogsOlderThan(long limitDay);
}
