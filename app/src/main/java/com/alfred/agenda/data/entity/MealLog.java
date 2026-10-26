package com.alfred.agenda.data.entity;

import androidx.room.Entity;
import androidx.room.ForeignKey;

/**
 * Registro de "refeição feita" num dia. Se existe uma linha (mealId, epochDay),
 * a refeição foi marcada como feita naquele dia.
 */
@Entity(tableName = "meal_logs",
        primaryKeys = {"mealId", "epochDay"},
        foreignKeys = @ForeignKey(entity = Meal.class, parentColumns = "id",
                childColumns = "mealId", onDelete = ForeignKey.CASCADE))
public class MealLog {

    public long mealId;

    /** Dia no formato LocalDate.toEpochDay() (dias desde 01/01/1970). */
    public long epochDay;
}
