package com.alfred.agenda.ui.tasks;

import android.content.Context;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.View;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.alfred.agenda.R;
import com.alfred.agenda.util.DateUtils;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.HashSet;
import java.util.Set;
import java.util.function.LongConsumer;

/**
 * Calendário mensal feito à mão (o CalendarView do Android não sabe destacar intervalos).
 *
 *  - O intervalo De..Até aparece como uma faixa cinza; o início e o fim ficam em amarelo.
 *  - Dias com tarefas ganham um pontinho embaixo do número.
 *  - Hoje tem um contorno.
 *  - As setas trocam o mês; tocar num dia chama o OnDayClick (quem decide o que fazer é o Fragment).
 *
 * A semana começa na segunda-feira, igual à aba Hábitos.
 */
public class RangeCalendarView extends LinearLayout {

    private static final int CELLS = 42; // 6 semanas x 7 dias
    private static final DateTimeFormatter MONTH_TITLE = DateTimeFormatter.ofPattern("MMMM 'de' yyyy", DateUtils.PT_BR);

    private YearMonth month = YearMonth.now();
    private long rangeFrom = Long.MIN_VALUE;
    private long rangeTo = Long.MIN_VALUE;
    private Set<Long> markedDays = new HashSet<>();
    @Nullable
    private LongConsumer onDayClick;
    @Nullable
    private Runnable onMonthChanged;

    private TextView title;
    private final LinearLayout[] cells = new LinearLayout[CELLS];
    private final TextView[] numbers = new TextView[CELLS];
    private final View[] dots = new View[CELLS];
    private final long[] cellDays = new long[CELLS];

    public RangeCalendarView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        setOrientation(VERTICAL);
        build();
        render();
    }

    // ---------------- API pública ----------------

    public void setMonth(YearMonth month) {
        this.month = month;
        render();
    }

    public YearMonth getMonth() {
        return month;
    }

    /** Intervalo destacado (epochDay, inclusive). */
    public void setRange(long from, long to) {
        this.rangeFrom = from;
        this.rangeTo = to;
        render();
    }

    /** Dias que ganham o pontinho (ex.: dias com tarefas). */
    public void setMarkedDays(Set<Long> days) {
        this.markedDays = days;
        render();
    }

    public void setOnDayClickListener(@Nullable LongConsumer listener) {
        this.onDayClick = listener;
    }

    /** Avisado quando o usuário troca de mês pelas setas. */
    public void setOnMonthChangedListener(@Nullable Runnable listener) {
        this.onMonthChanged = listener;
    }

    // ---------------- Montagem (uma vez só) ----------------

    private void build() {
        Context ctx = getContext();

        // Cabeçalho: ‹  Outubro de 2026  ›
        LinearLayout header = new LinearLayout(ctx);
        header.setOrientation(HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.addView(arrow(R.drawable.ic_chevron_left, -1));
        title = new TextView(ctx);
        title.setGravity(Gravity.CENTER);
        title.setTextSize(16);
        title.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        title.setTextColor(color(R.color.text_primary));
        header.addView(title, new LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f));
        header.addView(arrow(R.drawable.ic_chevron_right, +1));
        addView(header);

        // Nomes dos dias: Seg ... Dom
        LinearLayout weekdays = new LinearLayout(ctx);
        weekdays.setOrientation(HORIZONTAL);
        weekdays.setPadding(0, dp(4), 0, dp(4));
        for (String name : ctx.getResources().getStringArray(R.array.weekday_short)) {
            TextView tv = new TextView(ctx);
            tv.setText(name);
            tv.setTextSize(11);
            tv.setGravity(Gravity.CENTER);
            tv.setTextColor(color(R.color.text_secondary));
            weekdays.addView(tv, new LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f));
        }
        addView(weekdays);

        // Grade 6 x 7
        for (int row = 0; row < 6; row++) {
            LinearLayout line = new LinearLayout(ctx);
            line.setOrientation(HORIZONTAL);
            for (int col = 0; col < 7; col++) {
                int i = row * 7 + col;
                LinearLayout cell = new LinearLayout(ctx);
                cell.setOrientation(VERTICAL);
                cell.setGravity(Gravity.CENTER_HORIZONTAL);
                cell.setPadding(0, dp(3), 0, dp(3));

                TextView number = new TextView(ctx);
                number.setGravity(Gravity.CENTER);
                number.setTextSize(14);
                cell.addView(number, new LayoutParams(dp(34), dp(34)));

                View dot = new View(ctx);
                LayoutParams dotLp = new LayoutParams(dp(5), dp(5));
                dotLp.topMargin = dp(1);
                cell.addView(dot, dotLp);

                cell.setOnClickListener(v -> {
                    if (onDayClick != null) onDayClick.accept(cellDays[i]);
                });
                line.addView(cell, new LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f));
                cells[i] = cell;
                numbers[i] = number;
                dots[i] = dot;
            }
            addView(line);
        }
    }

    private ImageButton arrow(int icon, int delta) {
        ImageButton b = new ImageButton(getContext());
        b.setImageResource(icon);
        b.setColorFilter(color(R.color.text_secondary));
        b.setBackgroundResource(android.R.color.transparent);
        b.setPadding(dp(12), dp(8), dp(12), dp(8));
        b.setContentDescription(getContext().getString(delta < 0 ? R.string.month_previous : R.string.month_next));
        b.setOnClickListener(v -> {
            month = month.plusMonths(delta);
            render();
            if (onMonthChanged != null) onMonthChanged.run();
        });
        return b;
    }

    // ---------------- Desenho (a cada mudança) ----------------

    private void render() {
        if (title == null) return;
        title.setText(DateUtils.capitalize(month.format(MONTH_TITLE)));

        LocalDate first = month.atDay(1);
        LocalDate start = first.minusDays(first.getDayOfWeek().getValue() - 1); // segunda-feira
        long today = DateUtils.today();
        boolean singleDay = rangeFrom == rangeTo;

        for (int i = 0; i < CELLS; i++) {
            LocalDate date = start.plusDays(i);
            long day = date.toEpochDay();
            cellDays[i] = day;

            boolean inMonth = date.getMonthValue() == month.getMonthValue();
            boolean inRange = day >= rangeFrom && day <= rangeTo;
            boolean isEdge = day == rangeFrom || day == rangeTo;
            boolean isToday = day == today;

            // Faixa cinza do intervalo (arredondada nas pontas)
            if (inRange && !singleDay) {
                float r = dp(17);
                float left = day == rangeFrom ? r : 0;
                float right = day == rangeTo ? r : 0;
                GradientDrawable band = new GradientDrawable();
                band.setColor(color(R.color.gold_container));
                band.setCornerRadii(new float[]{left, left, right, right, right, right, left, left});
                cells[i].setBackground(insetVertical(band));
            } else {
                cells[i].setBackground(null);
            }

            // Número: início/fim em amarelo; hoje com contorno
            TextView number = numbers[i];
            number.setText(String.valueOf(date.getDayOfMonth()));
            number.setTypeface(null, isToday || isEdge ? Typeface.BOLD : Typeface.NORMAL);
            if (isEdge) {
                number.setBackground(circle(color(R.color.highlight), 0, 0));
                number.setTextColor(color(R.color.on_highlight));
            } else {
                number.setBackground(isToday ? circle(0x00000000, dp(1.5f), color(R.color.highlight)) : null);
                number.setTextColor(color(inMonth ? R.color.text_primary : R.color.outline));
            }

            // Pontinho de "tem tarefa"
            boolean marked = markedDays.contains(day);
            dots[i].setVisibility(marked ? VISIBLE : INVISIBLE);
            if (marked) dots[i].setBackground(circle(color(isEdge ? R.color.on_highlight : R.color.highlight), 0, 0));
        }
    }

    /** A faixa ocupa só a altura do círculo do número, não a célula inteira. */
    private android.graphics.drawable.Drawable insetVertical(GradientDrawable band) {
        return new android.graphics.drawable.InsetDrawable(band, 0, dp(3), 0, dp(9));
    }

    private static GradientDrawable circle(int fill, int strokeWidth, int strokeColor) {
        GradientDrawable d = new GradientDrawable();
        d.setShape(GradientDrawable.OVAL);
        d.setColor(fill);
        if (strokeWidth > 0) d.setStroke(strokeWidth, strokeColor);
        return d;
    }

    private int color(int res) {
        return ContextCompat.getColor(getContext(), res);
    }

    private int dp(float value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
