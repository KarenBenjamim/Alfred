package com.alfred.agenda.data.entity;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

/**
 * Água bebida num dia (total em ml). Um registro por dia: no dia seguinte começa do zero
 * sozinho, porque o "hoje" é outro registro.
 */
@Entity(tableName = "water_logs")
public class WaterLog {

    /** Dia em LocalDate.toEpochDay(). */
    @PrimaryKey
    public long epochDay;

    public int ml;
}
