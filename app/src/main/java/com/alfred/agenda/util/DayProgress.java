package com.alfred.agenda.util;

import com.alfred.agenda.data.entity.Habit;
import com.alfred.agenda.data.entity.StudySubject;
import com.alfred.agenda.data.entity.Task;

import java.util.Collection;
import java.util.List;
import java.util.Set;

/**
 * Progresso do dia, usado no anel da tela Início e para saber quando "todas as metas
 * de hoje foram concluídas".
 *
 * CRITÉRIO (cada categoria conta só coisas que têm um "feito" claro no app):
 *  - Trabalho e Pessoal: tarefas com entrega HOJE; feita = status "Concluída".
 *    (Tarefas atrasadas aparecem na lista de pendências, mas não entram no percentual do dia.)
 *  - Hábitos: todos os hábitos cadastrados; feito = marcado hoje.
 *  - Dieta: refeições do plano agrupadas por horário (as opções de um horário contam como 1);
 *    feita = qualquer opção marcada hoje.
 *  - Estudos: assuntos cadastrados; "feito" = assunto com tempo registrado hoje
 *    (inclui o cronômetro rodando).
 *  O total geral é a SOMA das categorias (feitos / metas), então o anel e as barras batem.
 */
public final class DayProgress {

    /** Feitos e total de uma categoria. */
    public static final class Count {
        public final int done;
        public final int total;

        public Count(int done, int total) {
            this.done = done;
            this.total = total;
        }

        public float fraction() {
            return total == 0 ? 0f : (float) done / total;
        }
    }

    public final Count work;
    public final Count personal;
    public final Count habits;
    public final Count diet;
    public final Count study;

    public DayProgress(Count work, Count personal, Count habits, Count diet, Count study) {
        this.work = work;
        this.personal = personal;
        this.habits = habits;
        this.diet = diet;
        this.study = study;
    }

    public int done() {
        return work.done + personal.done + habits.done + diet.done + study.done;
    }

    public int total() {
        return work.total + personal.total + habits.total + diet.total + study.total;
    }

    public float fraction() {
        return total() == 0 ? 0f : (float) done() / total();
    }

    /** Todas as metas de hoje concluídas (e existe pelo menos uma meta). */
    public boolean allDone() {
        return total() > 0 && done() == total();
    }

    /**
     * @param tasksToday       tarefas (trabalho e pessoais) — só as com entrega em "today" são contadas
     * @param doneHabitIds     hábitos marcados hoje
     * @param mealGroups       refeições do dia já agrupadas
     * @param doneMealIds      refeições marcadas hoje
     * @param studiedSubjectIds assuntos com tempo registrado hoje
     */
    public static DayProgress compute(long today, Collection<Task> tasksToday,
                                      List<Habit> habits, Set<Long> doneHabitIds,
                                      List<MealGroups.Group> mealGroups, Set<Long> doneMealIds,
                                      List<StudySubject> subjects, Set<Long> studiedSubjectIds) {
        int wDone = 0, wTotal = 0, pDone = 0, pTotal = 0;
        for (Task t : tasksToday) {
            if (t.dueEpochDay != today) continue;
            if (t.isPersonal()) {
                pTotal++;
                if (t.isDone()) pDone++;
            } else {
                wTotal++;
                if (t.isDone()) wDone++;
            }
        }
        int hDone = 0;
        for (Habit h : habits) if (doneHabitIds.contains(h.id)) hDone++;
        int dDone = 0;
        for (MealGroups.Group g : mealGroups) if (g.isDone(doneMealIds)) dDone++;
        int sDone = 0;
        for (StudySubject s : subjects) if (studiedSubjectIds.contains(s.id)) sDone++;

        return new DayProgress(new Count(wDone, wTotal), new Count(pDone, pTotal),
                new Count(hDone, habits.size()), new Count(dDone, mealGroups.size()),
                new Count(sDone, subjects.size()));
    }
}
