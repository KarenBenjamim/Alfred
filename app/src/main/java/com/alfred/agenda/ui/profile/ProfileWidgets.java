package com.alfred.agenda.ui.profile;

import android.content.Context;
import android.content.res.ColorStateList;
import android.widget.GridLayout;
import android.widget.ImageView;

import androidx.core.content.ContextCompat;

import com.alfred.agenda.R;
import com.alfred.agenda.util.Avatars;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.imageview.ShapeableImageView;
import com.google.android.material.shape.ShapeAppearanceModel;

import java.util.function.IntConsumer;

/**
 * Peças usadas no onboarding e na tela de Perfil:
 *  - grade com os ícones de perfil (o escolhido ganha um anel dourado);
 *  - chips da forma de tratamento (Senhor, Senhora, Senhorita, Pelo nome).
 */
public final class ProfileWidgets {

    private static final int AVATAR_DP = 68;

    private ProfileWidgets() {
    }

    /** Preenche a grade com os avatares. Devolve uma função para trocar o destaque. */
    public static IntConsumer avatarGrid(GridLayout grid, int selected, IntConsumer onPick) {
        Context ctx = grid.getContext();
        grid.removeAllViews();
        String[] names = ctx.getResources().getStringArray(R.array.avatar_names);
        int size = dp(ctx, AVATAR_DP);
        ShapeableImageView[] views = new ShapeableImageView[Avatars.DRAWABLES.length];

        for (int i = 0; i < Avatars.DRAWABLES.length; i++) {
            ShapeableImageView v = new ShapeableImageView(ctx);
            v.setImageResource(Avatars.DRAWABLES[i]);
            v.setScaleType(ImageView.ScaleType.CENTER_CROP);
            v.setShapeAppearanceModel(ShapeAppearanceModel.builder().setAllCornerSizes(
                    new com.google.android.material.shape.RelativeCornerSize(0.5f)).build());
            int pad = dp(ctx, 4);
            v.setPadding(pad, pad, pad, pad);
            v.setContentDescription(names[Math.min(i, names.length - 1)]);
            int index = i;
            v.setOnClickListener(x -> onPick.accept(index));

            GridLayout.LayoutParams lp = new GridLayout.LayoutParams(
                    GridLayout.spec(GridLayout.UNDEFINED), GridLayout.spec(GridLayout.UNDEFINED, 1f));
            lp.width = size;
            lp.height = size;
            lp.setGravity(android.view.Gravity.CENTER);
            lp.setMargins(0, dp(ctx, 6), 0, dp(ctx, 6));
            grid.addView(v, lp);
            views[i] = v;
        }

        IntConsumer highlight = sel -> {
            int gold = ContextCompat.getColor(ctx, R.color.highlight);
            int outline = ContextCompat.getColor(ctx, R.color.outline);
            for (int i = 0; i < views.length; i++) {
                boolean on = i == Avatars.safeIndex(sel);
                views[i].setStrokeColor(ColorStateList.valueOf(on ? gold : outline));
                views[i].setStrokeWidth(dp(ctx, on ? 3 : 1));
                views[i].setAlpha(on ? 1f : 0.75f);
                views[i].setSelected(on);
            }
        };
        highlight.accept(selected);
        return highlight;
    }

    /** Chips de escolha única com as formas de tratamento (posição = Honorific.FORM_*). */
    public static void formChips(ChipGroup group, int selected, IntConsumer onChange) {
        Context ctx = group.getContext();
        group.removeAllViews();
        group.setSingleSelection(true);
        group.setSelectionRequired(true);
        String[] options = ctx.getResources().getStringArray(R.array.honorific_options);
        int[][] states = {{android.R.attr.state_checked}, {}};
        int gold = ContextCompat.getColor(ctx, R.color.highlight);
        int goldBg = ContextCompat.getColor(ctx, R.color.gold_container);
        int text = ContextCompat.getColor(ctx, R.color.text_primary);
        int outline = ContextCompat.getColor(ctx, R.color.outline);
        int surface = ContextCompat.getColor(ctx, R.color.graphite_light);

        for (int i = 0; i < options.length; i++) {
            Chip chip = new Chip(ctx);
            chip.setId(android.view.View.generateViewId());
            chip.setText(options[i]);
            chip.setCheckable(true);
            chip.setCheckedIconVisible(true);
            chip.setCheckedIconTint(ColorStateList.valueOf(gold));
            chip.setChipBackgroundColor(new ColorStateList(states, new int[]{goldBg, surface}));
            chip.setChipStrokeColor(new ColorStateList(states, new int[]{gold, outline}));
            chip.setTextColor(new ColorStateList(states, new int[]{gold, text}));
            chip.setChecked(i == selected);
            int form = i;
            chip.setOnCheckedChangeListener((b, checked) -> {
                if (checked) onChange.accept(form);
            });
            group.addView(chip);
        }
    }

    static int dp(Context ctx, float value) {
        return Math.round(value * ctx.getResources().getDisplayMetrics().density);
    }
}
