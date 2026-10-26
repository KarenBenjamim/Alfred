package com.alfred.agenda.data.entity;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

/** Uma refeição do plano alimentar (ex.: "Café da manhã" às 07:30). */
@Entity(tableName = "meals")
public class Meal {

    @PrimaryKey(autoGenerate = true)
    public long id;

    @NonNull
    public String name = "";

    /** Horário em minutos desde a meia-noite (ex.: 7h30 = 450). */
    public int timeMinutes = 8 * 60;

    /** Se true, o Alfred envia uma notificação no horário da refeição. */
    public boolean notifyEnabled = true;

    /** Orientação da nutricionista para esta refeição (ex.: "Consumir primeiro os vegetais crus."). */
    @NonNull
    @ColumnInfo(defaultValue = "")
    public String notes = "";
}
