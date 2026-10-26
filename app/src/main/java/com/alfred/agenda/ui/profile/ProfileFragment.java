package com.alfred.agenda.ui.profile;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.GridLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.alfred.agenda.MainActivity;
import com.alfred.agenda.R;
import com.alfred.agenda.account.AccountStore;
import com.alfred.agenda.account.UserProfile;
import com.alfred.agenda.util.Avatars;
import com.alfred.agenda.util.DateUtils;
import com.alfred.agenda.util.Honorific;
import com.alfred.agenda.util.UiUtils;
import com.alfred.agenda.util.WaterGoal;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.imageview.ShapeableImageView;
import com.google.android.material.textfield.TextInputLayout;

import java.text.NumberFormat;
import java.util.function.IntConsumer;

/**
 * Perfil do usuário (aberto pelo avatar no canto superior esquerdo):
 *  - avatar atual em destaque, nome e forma de tratamento;
 *  - grade com os ícones de perfil;
 *  - edição de nome, tratamento e peso (a meta de água é recalculada na hora);
 *  - acesso às Configurações e botão "Sair".
 * As mudanças valem para o app inteiro assim que são salvas.
 */
public class ProfileFragment extends Fragment {

    private static final String STATE_DRAFT_FORM = "draft_form";
    private static final String STATE_DRAFT_AVATAR = "draft_avatar";

    /** O que está salvo e o que está sendo editado. */
    private UserProfile saved;
    private UserProfile draft;

    private ShapeableImageView heroAvatar;
    private TextView heroName;
    private TextView heroForm;
    private TextInputLayout nameLayout;
    private TextInputLayout weightLayout;
    private EditText name;
    private EditText weight;
    private TextView waterGoal;
    private MaterialButton save;
    private IntConsumer highlightAvatar;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_profile, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        heroAvatar = view.findViewById(R.id.profile_avatar);
        heroName = view.findViewById(R.id.profile_name);
        heroForm = view.findViewById(R.id.profile_form);
        nameLayout = view.findViewById(R.id.layout_name);
        weightLayout = view.findViewById(R.id.layout_weight);
        name = view.findViewById(R.id.input_name);
        weight = view.findViewById(R.id.input_weight);
        waterGoal = view.findViewById(R.id.water_goal);
        save = view.findViewById(R.id.button_save);
        ((TextView) view.findViewById(R.id.profile_username)).setText(
                getString(R.string.profile_username, AccountStore.currentUsername(requireContext())));

        saved = UserProfile.current(requireContext());
        draft = saved.copy();
        if (savedInstanceState != null) {
            // Os campos de texto se restauram sozinhos; tratamento e avatar vêm daqui
            draft.form = savedInstanceState.getInt(STATE_DRAFT_FORM, saved.form);
            draft.avatar = savedInstanceState.getInt(STATE_DRAFT_AVATAR, saved.avatar);
        } else {
            name.setText(saved.name);
            if (saved.weightKg > 0) weight.setText(formatWeight(saved.weightKg));
        }

        ProfileWidgets.formChips(view.findViewById(R.id.chips_form), draft.form, form -> {
            draft.form = form;
            onDraftChanged();
        });
        GridLayout grid = view.findViewById(R.id.avatar_grid);
        highlightAvatar = ProfileWidgets.avatarGrid(grid, draft.avatar, index -> {
            draft.avatar = index;
            highlightAvatar.accept(index);
            onDraftChanged();
        });

        TextWatcher watcher = new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                nameLayout.setError(null);
                weightLayout.setError(null);
                onDraftChanged();
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        };
        name.addTextChangedListener(watcher);
        weight.addTextChangedListener(watcher);

        save.setOnClickListener(v -> save());
        MainActivity activity = (MainActivity) requireActivity();
        addRow(view.findViewById(R.id.group_app), R.drawable.ic_settings, getString(R.string.settings_title),
                getString(R.string.profile_settings_desc), () -> activity.showTab(MainActivity.TAB_SETTINGS));
        view.findViewById(R.id.button_logout).setOnClickListener(v -> confirmLogout());

        onDraftChanged();
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        if (draft != null) {
            outState.putInt(STATE_DRAFT_FORM, draft.form);
            outState.putInt(STATE_DRAFT_AVATAR, draft.avatar);
        }
    }

    /** Atualiza o destaque (prévia do que será salvo), a meta de água e o botão Salvar. */
    private void onDraftChanged() {
        draft.name = UiUtils.text(name);
        draft.weightKg = WaterGoal.parseWeight(UiUtils.text(weight));

        heroAvatar.setImageResource(Avatars.drawable(draft.avatar));
        String shownName = Honorific.normalizeName(draft.name);
        heroName.setText(shownName.isEmpty() ? getString(R.string.profile_name) : shownName);
        String[] forms = getResources().getStringArray(R.array.honorific_options);
        heroForm.setText(getString(R.string.profile_form_value, forms[Honorific.safeForm(draft.form)]));

        if (WaterGoal.isValidWeight(draft.weightKg)) {
            int perKg = WaterGoal.mlPerKg(requireContext());
            waterGoal.setText(getString(R.string.profile_water_goal,
                    NumberFormat.getIntegerInstance(DateUtils.PT_BR).format(WaterGoal.goalMl(draft.weightKg, perKg)),
                    perKg));
        } else {
            waterGoal.setText(R.string.onboarding_weight_hint);
        }

        boolean changed = !Honorific.normalizeName(draft.name).equals(saved.name)
                || draft.form != saved.form
                || draft.avatar != saved.avatar
                || Math.abs(draft.weightKg - saved.weightKg) > 0.001f;
        save.setEnabled(changed);
    }

    private void save() {
        if (draft.name.trim().isEmpty()) {
            nameLayout.setError(getString(R.string.onboarding_name_required));
            return;
        }
        if (!WaterGoal.isValidWeight(draft.weightKg)) {
            weightLayout.setError(getString(R.string.profile_weight_invalid,
                    Math.round(WaterGoal.MIN_WEIGHT_KG), Math.round(WaterGoal.MAX_WEIGHT_KG)));
            return;
        }
        UserProfile toSave = draft.copy();
        toSave.onboarded = true;
        if (!toSave.save(requireContext())) {
            UiUtils.toast(requireContext(), getString(R.string.state_error_title));
            return;
        }
        saved = UserProfile.current(requireContext());
        draft = saved.copy();
        onDraftChanged();
        MainActivity activity = (MainActivity) requireActivity();
        activity.refreshProfile();
        // A mensagem já sai com o tratamento novo
        activity.showMessage(Honorific.str(requireContext(), R.string.profile_saved));
    }

    private void confirmLogout() {
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.profile_logout_title)
                .setMessage(Honorific.str(requireContext(), R.string.profile_logout_msg))
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.profile_logout, (d, w) -> ((MainActivity) requireActivity()).logout())
                .show();
    }

    private void addRow(LinearLayout group, int icon, String title, String value, Runnable action) {
        View row = LayoutInflater.from(requireContext()).inflate(R.layout.item_settings_row, group, false);
        ((ImageView) row.findViewById(R.id.icon)).setImageResource(icon);
        ((TextView) row.findViewById(R.id.title)).setText(title);
        ((TextView) row.findViewById(R.id.value)).setText(value);
        android.util.TypedValue tv = new android.util.TypedValue();
        requireContext().getTheme().resolveAttribute(android.R.attr.selectableItemBackground, tv, true);
        row.setBackgroundResource(tv.resourceId);
        row.setOnClickListener(v -> action.run());
        group.addView(row);
    }

    private static String formatWeight(float kg) {
        NumberFormat f = NumberFormat.getNumberInstance(DateUtils.PT_BR);
        f.setMaximumFractionDigits(1);
        f.setGroupingUsed(false);
        return f.format(kg);
    }
}
