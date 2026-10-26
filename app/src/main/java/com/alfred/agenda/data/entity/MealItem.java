package com.alfred.agenda.data.entity;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;
import androidx.room.PrimaryKey;

/** Um alimento dentro de uma refeição. Excluído automaticamente junto com a refeição (CASCADE). */
@Entity(tableName = "meal_items",
        foreignKeys = @ForeignKey(entity = Meal.class, parentColumns = "id",
                childColumns = "mealId", onDelete = ForeignKey.CASCADE),
        indices = @Index("mealId"))
public class MealItem {

    @PrimaryKey(autoGenerate = true)
    public long id;

    public long mealId;

    @NonNull
    public String description = "";

    /** Texto livre: "2 fatias", "150 g", "1 xícara"... */
    @NonNull
    public String quantity = "";

    /** Ordem de exibição dentro da refeição. */
    public int position;

    /** Substituições diretas sugeridas pela nutricionista, uma por linha. */
    @NonNull
    @ColumnInfo(defaultValue = "")
    public String substitutions = "";

    /** Grupo da lista de substituição (1 a 15). 0 = sem grupo. */
    @ColumnInfo(defaultValue = "0")
    public int groupNumber;

    /** Quantas porções do grupo este item vale (ex.: "2,5 porções"). */
    @NonNull
    @ColumnInfo(defaultValue = "")
    public String groupPortions = "";

    public boolean hasGroup() {
        return groupNumber > 0;
    }
}
