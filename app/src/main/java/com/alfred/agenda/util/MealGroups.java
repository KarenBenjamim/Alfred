package com.alfred.agenda.util;

import com.alfred.agenda.data.entity.MealWithItems;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Agrupa as refeições por horário: "Lanche - Opção 1/2/3" às 17:00 viram uma refeição só,
 * que conta como feita quando QUALQUER opção foi marcada.
 */
public final class MealGroups {

    private static final Pattern OPTION = Pattern.compile("Opção\\s*\\d+");

    private MealGroups() {
    }

    /** Uma refeição do dia (com suas opções). */
    public static final class Group {
        public final String name;
        public final int timeMinutes;
        public final List<MealWithItems> options = new ArrayList<>();

        Group(String name, int timeMinutes) {
            this.name = name;
            this.timeMinutes = timeMinutes;
        }

        /** A opção marcada como feita (ou null). */
        public MealWithItems doneOption(Set<Long> doneMealIds) {
            for (MealWithItems o : options) if (doneMealIds.contains(o.meal.id)) return o;
            return null;
        }

        public boolean isDone(Set<Long> doneMealIds) {
            return doneOption(doneMealIds) != null;
        }
    }

    /** Agrupa mantendo a ordem recebida (o banco já devolve por horário). */
    public static List<Group> group(List<MealWithItems> meals) {
        Map<String, Group> map = new LinkedHashMap<>();
        for (MealWithItems m : meals) {
            String base = baseName(m.meal.name);
            String key = m.meal.timeMinutes + "|" + base;
            Group g = map.get(key);
            if (g == null) {
                g = new Group(base, m.meal.timeMinutes);
                map.put(key, g);
            }
            g.options.add(m);
        }
        return new ArrayList<>(map.values());
    }

    /** "Lanche - Opção 2" -> "Lanche". */
    public static String baseName(String name) {
        return name.replaceAll("\\s*[-–]\\s*Opção\\s*\\d+\\s*$", "");
    }

    /** "Lanche - Opção 2" -> "Opção 2" (ou o nome inteiro, se não houver opção). */
    public static String optionLabel(String name) {
        Matcher m = OPTION.matcher(name);
        return m.find() ? m.group() : name;
    }
}
