package com.alfred.agenda.data.entity;

import androidx.room.Entity;
import androidx.room.ForeignKey;

/**
 * Registro de "hábito concluído" num dia. Se existe a linha (habitId, epochDay),
 * o hábito foi feito naquele dia (e a bolinha correspondente fica amarela).
 */
@Entity(tableName = "habit_logs",
        primaryKeys = {"habitId", "epochDay"},
        foreignKeys = @ForeignKey(entity = Habit.class, parentColumns = "id",
                childColumns = "habitId", onDelete = ForeignKey.CASCADE))
public class HabitLog {

    public long habitId;

    /** Dia no formato LocalDate.toEpochDay(). */
    public long epochDay;
}
