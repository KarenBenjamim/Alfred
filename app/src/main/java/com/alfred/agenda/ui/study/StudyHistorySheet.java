package com.alfred.agenda.ui.study;

import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.Observer;

import com.alfred.agenda.R;
import com.alfred.agenda.util.Honorific;
import com.alfred.agenda.data.AlfredRepository;
import com.alfred.agenda.data.entity.StudyLog;
import com.alfred.agenda.data.entity.StudySubject;
import com.alfred.agenda.util.DateUtils;
import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialog;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Histórico de um assunto (painel que sobe de baixo):
 *  - barras dos últimos 7 dias (hoje em amarelo);
 *  - uma linha por dia estudado, com o tempo daquele dia.
 */
public final class StudyHistorySheet {

    private static final int CHART_HEIGHT_DP = 90;

    private StudyHistorySheet() {
    }

    public static void show(Fragment host, StudySubject subject) {
        Context ctx = host.requireContext();
        BottomSheetDialog sheet = new BottomSheetDialog(ctx);
        View view = LayoutInflater.from(ctx).inflate(R.layout.sheet_study_history, null);
        TextView title = view.findViewById(R.id.title);
        TextView total = view.findViewById(R.id.total);
        LinearLayout chart = view.findViewById(R.id.chart);
        LinearLayout days = view.findViewById(R.id.days);
        title.setText(ctx.getString(R.string.study_history_title, subject.name));

        // Observa os registros enquanto o painel estiver aberto
        LiveData<List<StudyLog>> live = AlfredRepository.get(ctx).observeStudyLogsForSubject(subject.id);
        Observer<List<StudyLog>> observer = logs -> {
            Map<Long, Long> byDay = new HashMap<>();
            long sum = 0;
            for (StudyLog l : logs) {
                byDay.put(l.epochDay, l.seconds);
                sum += l.seconds;
            }
            // Soma o que está correndo agora (ainda não foi gravado)
            long today = DateUtils.today();
            long runningToday = StudyViewModel.seconds(subject, logs, today, today) - byDay.getOrDefault(today, 0L);
            if (runningToday > 0) {
                byDay.put(today, byDay.getOrDefault(today, 0L) + runningToday);
                sum += runningToday;
            }
            total.setText(ctx.getString(R.string.study_history_total, DateUtils.formatDuration(sum)));
            renderChart(chart, byDay, today);
            renderDays(days, byDay);
        };
        live.observe(host.getViewLifecycleOwner(), observer);
        sheet.setOnDismissListener(d -> live.removeObserver(observer));

        sheet.setContentView(view);
        sheet.getBehavior().setState(BottomSheetBehavior.STATE_EXPANDED);
        sheet.getBehavior().setSkipCollapsed(true);
        sheet.show();
    }

    /** 7 barras (de 6 dias atrás até hoje), com altura proporcional ao maior dia. */
    private static void renderChart(LinearLayout chart, Map<Long, Long> byDay, long today) {
        Context ctx = chart.getContext();
        chart.removeAllViews();
        float density = ctx.getResources().getDisplayMetrics().density;
        String[] weekdays = ctx.getResources().getStringArray(R.array.weekday_short);

        long max = 1;
        for (long d = today - 6; d <= today; d++) max = Math.max(max, byDay.getOrDefault(d, 0L));

        for (long d = today - 6; d <= today; d++) {
            long secs = byDay.getOrDefault(d, 0L);
            LinearLayout col = new LinearLayout(ctx);
            col.setOrientation(LinearLayout.VERTICAL);
            col.setGravity(Gravity.CENTER_HORIZONTAL | Gravity.BOTTOM);

            TextView value = smallText(ctx, secs > 0 ? DateUtils.formatDuration(secs) : "", R.color.text_secondary);
            col.addView(value);

            View bar = new View(ctx);
            GradientDrawable bg = new GradientDrawable();
            bg.setCornerRadius(4 * density);
            bg.setColor(ContextCompat.getColor(ctx, d == today ? R.color.highlight : R.color.accent));
            bar.setBackground(bg);
            int height = Math.max(Math.round(2 * density), Math.round(CHART_HEIGHT_DP * density * secs / max));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(Math.round(18 * density), height);
            lp.topMargin = Math.round(2 * density);
            col.addView(bar, lp);

            int dow = LocalDate.ofEpochDay(d).getDayOfWeek().getValue(); // 1 = segunda
            col.addView(smallText(ctx, weekdays[dow - 1], d == today ? R.color.text_primary : R.color.text_secondary));

            chart.addView(col, new LinearLayout.LayoutParams(0,
                    Math.round((CHART_HEIGHT_DP + 40) * density), 1f));
        }
    }

    /** Lista "Sexta-feira, 24 de outubro ........ 1h 20min", do mais recente ao mais antigo. */
    private static void renderDays(LinearLayout container, Map<Long, Long> byDay) {
        Context ctx = container.getContext();
        container.removeAllViews();
        if (byDay.isEmpty()) {
            container.addView(smallText(ctx, Honorific.str(ctx, R.string.study_history_empty), R.color.text_secondary));
            return;
        }
        float density = ctx.getResources().getDisplayMetrics().density;
        byDay.keySet().stream().sorted((a, b) -> Long.compare(b, a)).forEach(day -> {
            LinearLayout row = new LinearLayout(ctx);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setPadding(0, Math.round(6 * density), 0, Math.round(6 * density));

            TextView date = new TextView(ctx);
            date.setText(DateUtils.formatLongDay(LocalDate.ofEpochDay(day)));
            date.setTextSize(14);
            date.setTextColor(ContextCompat.getColor(ctx, R.color.text_primary));
            row.addView(date, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

            TextView time = new TextView(ctx);
            time.setText(DateUtils.formatDuration(byDay.get(day)));
            time.setTextSize(14);
            time.setTextColor(ContextCompat.getColor(ctx, R.color.accent));
            row.addView(time);
            container.addView(row);
        });
    }

    private static TextView smallText(Context ctx, String text, int colorRes) {
        TextView tv = new TextView(ctx);
        tv.setText(text);
        tv.setTextSize(10);
        tv.setGravity(Gravity.CENTER);
        tv.setTextColor(ContextCompat.getColor(ctx, colorRes));
        return tv;
    }
}
