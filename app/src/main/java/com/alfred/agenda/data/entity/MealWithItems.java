package com.alfred.agenda.data.entity;

import androidx.room.Embedded;
import androidx.room.Relation;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Refeição + seus itens, carregados juntos pelo Room (@Relation). */
public class MealWithItems {

    @Embedded
    public Meal meal;

    @Relation(parentColumn = "id", entityColumn = "mealId")
    public List<MealItem> items;

    /** Itens na ordem em que foram cadastrados (o @Relation não garante ordem). */
    public List<MealItem> sortedItems() {
        List<MealItem> list = new ArrayList<>(items != null ? items : new ArrayList<>());
        list.sort(Comparator.comparingInt(i -> i.position));
        return list;
    }
}
