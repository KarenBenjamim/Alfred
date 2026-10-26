package com.alfred.agenda.data.entity;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

/** Um hábito diário (ex.: "Beber 2 litros de água"). */
@Entity(tableName = "habits")
public class Habit {

    /**
     * Hábitos sugeridos pelo Alfred, cadastrados na primeira vez que o app abre
     * (ou na atualização que trouxe a aba Hábitos). Podem ser editados ou excluídos.
     */
    public static final String[] SUGGESTIONS = {
            "Beber 2 litros de água",
            "Comer 3 porções de frutas ou verduras",
            "Mexer o corpo por 30 minutos",
            "Dormir de 7 a 8 horas",
            "15 minutos sem telas antes de dormir"
    };

    @PrimaryKey(autoGenerate = true)
    public long id;

    @NonNull
    public String name = "";

    /** Ordem de exibição. */
    public int position;
}
