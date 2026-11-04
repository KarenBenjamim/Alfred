package com.alfred.agenda.util;

import com.alfred.agenda.data.entity.MealWithItems;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Agrupa as opções de uma mesma refeição: "Lanche - Opção 1", "Lanche (opção 2)" e
 * "Lanche opcao 3" viram uma refeição só ("Lanche"), mesmo em horários diferentes, que conta
 * como feita quando QUALQUER opção foi marcada. Refeições sem "Opção N" ficam sozinhas.
 * Mesma regra de web/js/schema.js (groupMeals).
 */
public final class MealGroups {

    /** "Opção 2", "opcao 2", "Op. 2", "Opç 2", no fim do nome, com ou sem "-", "–", ":", "(". */
    private static final Pattern OPTION_SUFFIX = Pattern.compile(
            "\\s*[-–—:(]?\\s*(?<!\\p{L})(op(?:ç|c)?(?:ão|ao)?\\.?)\\s*(\\d+)\\s*\\)?\\s*$",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);

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

    /**
     * Agrupa mantendo a ordem recebida (o banco já devolve por horário); o grupo fica no
     * horário da primeira opção.
     */
    public static List<Group> group(List<MealWithItems> meals) {
        Map<String, Group> map = new LinkedHashMap<>();
        for (MealWithItems m : meals) {
            String base = baseName(m.meal.name);
            String key = isOption(m.meal.name) ? "o|" + base.toLowerCase(Locale.ROOT) : "m|" + m.meal.id;
            Group g = map.get(key);
            if (g == null) {
                g = new Group(base, m.meal.timeMinutes);
                map.put(key, g);
            }
            g.options.add(m);
        }
        return new ArrayList<>(map.values());
    }

    static boolean isOption(String name) {
        Matcher m = OPTION_SUFFIX.matcher(name);
        return m.find() && m.start() > 0;
    }

    /** "Lanche - Opção 2" -> "Lanche". */
    public static String baseName(String name) {
        Matcher m = OPTION_SUFFIX.matcher(name);
        return m.find() && m.start() > 0 ? name.substring(0, m.start()).trim() : name;
    }

    /** "Lanche - Opção 2" -> "Opção 2" (ou o nome inteiro, se não houver opção). */
    public static String optionLabel(String name) {
        Matcher m = OPTION_SUFFIX.matcher(name);
        return m.find() && m.start() > 0 ? "Opção " + m.group(2) : name;
    }
}
