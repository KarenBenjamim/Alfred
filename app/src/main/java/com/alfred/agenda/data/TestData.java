package com.alfred.agenda.data;

import com.alfred.agenda.data.entity.Task;

import java.util.ArrayList;
import java.util.List;

/**
 * Tarefas de exemplo para testar as abas Trabalho, Pessoal e Hoje.
 * Todas começam com TITLE_PREFIX, para poderem ser removidas de uma vez pelo menu "⋮".
 * As datas são relativas a hoje (ex.: +1 = amanhã, -2 = anteontem).
 */
public final class TestData {

    public static final String TITLE_PREFIX = "[Teste] ";

    private TestData() {
    }

    public static List<Task> sampleTasks(long today) {
        List<Task> list = new ArrayList<>();
        int W = Task.CATEGORY_WORK, P = Task.CATEGORY_PERSONAL;
        int M = Task.PERIOD_MORNING, T = Task.PERIOD_AFTERNOON, N = Task.PERIOD_NIGHT;
        int low = Task.PRIORITY_LOW, med = Task.PRIORITY_MEDIUM, high = Task.PRIORITY_HIGH;
        int pend = Task.STATUS_PENDING, prog = Task.STATUS_IN_PROGRESS, done = Task.STATUS_DONE;

        // ---- Trabalho ----
        list.add(task(W, today, M, high, pend, "Revisar relatório mensal", "Conferir números de vendas antes de enviar."));
        list.add(task(W, today, T, med, prog, "Reunião com a equipe", "Sala 3, às 14h."));
        list.add(task(W, today + 1, M, high, pend, "Enviar proposta ao cliente", ""));
        list.add(task(W, today + 3, T, low, pend, "Atualizar planilha de custos", ""));
        list.add(task(W, today + 7, M, high, pend, "Apresentação trimestral", "Preparar slides com os resultados."));
        list.add(task(W, today + 15, T, med, pend, "Treinamento do sistema novo", ""));
        list.add(task(W, today - 2, T, med, pend, "Responder e-mails pendentes", "Esta deve aparecer como atrasada na aba Hoje."));
        list.add(task(W, today - 5, M, low, done, "Fechar orçamento", ""));

        // ---- Pessoal ----
        list.add(task(P, today, M, high, pend, "Consulta no dentista", "Levar a carteirinha do convênio."));
        list.add(task(P, today, N, med, pend, "Ligar para a mamãe", ""));
        list.add(task(P, today + 1, T, med, pend, "Comprar presente da Ana", ""));
        list.add(task(P, today + 2, N, low, pend, "Cinema com as amigas", ""));
        list.add(task(P, today + 4, M, high, pend, "Pagar a conta de luz", "Vence às 18h."));
        list.add(task(P, today + 20, M, med, pend, "Renovar documento", ""));
        list.add(task(P, today - 1, N, low, pend, "Organizar o guarda-roupa", ""));
        list.add(task(P, today - 3, T, low, done, "Aula de dança", ""));

        // ---- Para testar o expurgo: 3 anos atrás. Some na próxima vez que o app abrir. ----
        list.add(task(W, today - 3 * 365, M, low, pend, "Tarefa de 3 anos atrás (será expurgada)", ""));
        return list;
    }

    private static Task task(int category, long day, int period, int priority, int status,
                             String title, String description) {
        Task t = new Task();
        t.category = category;
        t.dueEpochDay = day;
        t.period = period;
        t.priority = priority;
        t.status = status;
        t.title = TITLE_PREFIX + title;
        t.description = description;
        return t;
    }
}
