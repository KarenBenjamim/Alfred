package com.alfred.agenda.ui.common;

import android.content.Context;
import android.content.res.ColorStateList;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.alfred.agenda.R;

import java.util.ArrayList;
import java.util.List;

/**
 * Barra de navegação inferior do Alfred: Trabalho, Pessoal, Estudos, Hábitos e Dieta
 * (o Início fica na casinha do cabeçalho e o Perfil no avatar).
 *
 *  - Destino ativo: "pílula" dourada atrás do ícone e rótulo dourado.
 *  - Em telas estreitas (menos de 60dp por item), os rótulos dos destinos inativos somem
 *    para nada ficar espremido; o ativo continua com rótulo. Todos mantêm contentDescription.
 */
public class AlfredBottomNav extends LinearLayout {

    public interface OnSelectListener {
        void onSelect(int tab);
    }

    private static final int MIN_ITEM_WIDTH_WITH_LABEL_DP = 60;

    private final List<Item> items = new ArrayList<>();
    private int selectedTab = -1;
    private boolean compact = false;
    @Nullable
    private OnSelectListener listener;

    private static final class Item {
        final int tab;
        final View root;
        final FrameLayout pill;
        final ImageView icon;
        final TextView label;

        Item(int tab, View root, FrameLayout pill, ImageView icon, TextView label) {
            this.tab = tab;
            this.root = root;
            this.pill = pill;
            this.icon = icon;
            this.label = label;
        }
    }

    public AlfredBottomNav(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        setOrientation(HORIZONTAL);
        setGravity(Gravity.CENTER_VERTICAL);
    }

    public void setOnSelectListener(@Nullable OnSelectListener listener) {
        this.listener = listener;
    }

    /** Adiciona um destino. */
    public void addItem(int tab, int iconRes, int labelRes) {
        Context ctx = getContext();
        LinearLayout root = new LinearLayout(ctx);
        root.setOrientation(VERTICAL);
        root.setGravity(Gravity.CENTER);
        root.setBackgroundResource(resolveRipple());
        root.setContentDescription(ctx.getString(labelRes));

        FrameLayout pill = new FrameLayout(ctx);
        ImageView icon = new ImageView(ctx);
        icon.setImageResource(iconRes);
        FrameLayout.LayoutParams iconLp = new FrameLayout.LayoutParams(dp(22), dp(22), Gravity.CENTER);
        pill.addView(icon, iconLp);
        root.addView(pill, new LayoutParams(dp(52), dp(30)));

        TextView label = new TextView(ctx);
        label.setText(labelRes);
        label.setTextSize(11);
        label.setMaxLines(1);
        label.setEllipsize(android.text.TextUtils.TruncateAt.END);
        label.setGravity(Gravity.CENTER);
        LayoutParams labelLp = new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
        labelLp.topMargin = dp(3);
        root.addView(label, labelLp);

        root.setOnClickListener(v -> {
            if (listener != null) listener.onSelect(tab);
        });
        addView(root, new LayoutParams(0, LayoutParams.MATCH_PARENT, 1f));
        items.add(new Item(tab, root, pill, icon, label));
        refresh();
    }

    /** Marca o destino ativo (-1 = nenhum, ex.: na tela de configurações). */
    public void setSelectedTab(int tab) {
        selectedTab = tab;
        refresh();
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        if (items.isEmpty()) return;
        boolean newCompact = w / items.size() < dp(MIN_ITEM_WIDTH_WITH_LABEL_DP);
        if (newCompact != compact) {
            compact = newCompact;
            post(this::refresh);
        }
    }

    private void refresh() {
        int gold = ContextCompat.getColor(getContext(), R.color.highlight);
        int dark = ContextCompat.getColor(getContext(), R.color.on_highlight);
        int muted = ContextCompat.getColor(getContext(), R.color.text_secondary);
        for (Item item : items) {
            boolean active = item.tab == selectedTab;
            item.pill.setBackgroundResource(active ? R.drawable.bg_nav_active : 0);
            item.icon.setImageTintList(ColorStateList.valueOf(active ? dark : muted));
            item.label.setTextColor(active ? gold : muted);
            item.label.setVisibility(active || !compact ? VISIBLE : GONE);
            item.root.setSelected(active);
        }
    }

    private int resolveRipple() {
        android.util.TypedValue tv = new android.util.TypedValue();
        getContext().getTheme().resolveAttribute(android.R.attr.selectableItemBackgroundBorderless, tv, true);
        return tv.resourceId;
    }

    private int dp(float v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }
}
