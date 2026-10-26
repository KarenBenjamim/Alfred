package com.alfred.agenda.ui.auth;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.GridLayout;
import android.widget.TextView;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.alfred.agenda.MainActivity;
import com.alfred.agenda.R;
import com.alfred.agenda.account.UserProfile;
import com.alfred.agenda.ui.profile.ProfileWidgets;
import com.alfred.agenda.util.Honorific;
import com.alfred.agenda.util.UiUtils;
import com.alfred.agenda.util.WaterGoal;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.progressindicator.LinearProgressIndicator;
import com.google.android.material.textfield.TextInputLayout;

import java.text.NumberFormat;
import java.util.function.IntConsumer;

/**
 * Primeiro acesso de cada conta: pergunta nome, forma de tratamento, peso e ícone de perfil,
 * um passo por vez. Ao concluir, grava no perfil (UserProfile) e não pergunta de novo.
 */
public class OnboardingFragment extends Fragment {

    private static final int STEPS = 4;
    private static final String STATE_STEP = "step";
    private static final String STATE_FORM = "form";
    private static final String STATE_AVATAR = "avatar";

    private int step;
    private int form = Honorific.FORM_NAME;
    private int avatar;

    private TextView stepLabel;
    private TextView stepTitle;
    private TextView stepText;
    private LinearProgressIndicator stepProgress;
    private TextInputLayout nameLayout;
    private EditText name;
    private View formStep;
    private TextView formPreview;
    private View weightStep;
    private TextInputLayout weightLayout;
    private EditText weight;
    private TextView weightPreview;
    private GridLayout avatarStep;
    private MaterialButton back;
    private MaterialButton next;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_onboarding, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        stepLabel = view.findViewById(R.id.step_label);
        stepTitle = view.findViewById(R.id.step_title);
        stepText = view.findViewById(R.id.step_text);
        stepProgress = view.findViewById(R.id.step_progress);
        nameLayout = view.findViewById(R.id.step_name);
        name = view.findViewById(R.id.input_name);
        formStep = view.findViewById(R.id.step_form);
        formPreview = view.findViewById(R.id.form_preview);
        weightStep = view.findViewById(R.id.step_weight);
        weightLayout = view.findViewById(R.id.layout_weight);
        weight = view.findViewById(R.id.input_weight);
        weightPreview = view.findViewById(R.id.weight_preview);
        avatarStep = view.findViewById(R.id.step_avatar);
        back = view.findViewById(R.id.button_back);
        next = view.findViewById(R.id.button_next);

        if (savedInstanceState != null) {
            step = savedInstanceState.getInt(STATE_STEP);
            form = savedInstanceState.getInt(STATE_FORM, Honorific.FORM_NAME);
            avatar = savedInstanceState.getInt(STATE_AVATAR);
        } else {
            // Quem atualizou o app já tinha informado o peso na calculadora de água antiga
            float legacy = UserProfile.legacyWaterWeight(requireContext());
            if (WaterGoal.isValidWeight(legacy)) weight.setText(formatWeight(legacy));
        }

        ProfileWidgets.formChips(view.findViewById(R.id.chips_form), form, f -> {
            form = f;
            updatePreviews();
        });
        IntConsumer[] highlight = new IntConsumer[1];
        highlight[0] = ProfileWidgets.avatarGrid(avatarStep, avatar, index -> {
            avatar = index;
            highlight[0].accept(index);
        });

        TextWatcher watcher = new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                nameLayout.setError(null);
                weightLayout.setError(null);
                updatePreviews();
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        };
        name.addTextChangedListener(watcher);
        weight.addTextChangedListener(watcher);

        back.setOnClickListener(v -> goTo(step - 1));
        next.setOnClickListener(v -> onNext());

        // "Voltar" do sistema volta um passo (no 1º passo, deixa o app fechar)
        requireActivity().getOnBackPressedDispatcher().addCallback(getViewLifecycleOwner(),
                new OnBackPressedCallback(true) {
                    @Override
                    public void handleOnBackPressed() {
                        if (step > 0) {
                            goTo(step - 1);
                        } else {
                            setEnabled(false);
                            requireActivity().getOnBackPressedDispatcher().onBackPressed();
                        }
                    }
                });

        goTo(step);
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putInt(STATE_STEP, step);
        outState.putInt(STATE_FORM, form);
        outState.putInt(STATE_AVATAR, avatar);
    }

    private void onNext() {
        if (step == 0 && UiUtils.text(name).isEmpty()) {
            nameLayout.setError(getString(R.string.onboarding_name_required));
            return;
        }
        if (step == 2 && !WaterGoal.isValidWeight(WaterGoal.parseWeight(UiUtils.text(weight)))) {
            weightLayout.setError(getString(R.string.profile_weight_invalid,
                    Math.round(WaterGoal.MIN_WEIGHT_KG), Math.round(WaterGoal.MAX_WEIGHT_KG)));
            return;
        }
        if (step < STEPS - 1) {
            goTo(step + 1);
        } else {
            finish();
        }
    }

    private void goTo(int target) {
        step = Math.max(0, Math.min(STEPS - 1, target));
        nameLayout.setVisibility(step == 0 ? View.VISIBLE : View.GONE);
        formStep.setVisibility(step == 1 ? View.VISIBLE : View.GONE);
        weightStep.setVisibility(step == 2 ? View.VISIBLE : View.GONE);
        avatarStep.setVisibility(step == 3 ? View.VISIBLE : View.GONE);

        int[] titles = {R.string.onboarding_name_title, R.string.onboarding_form_title,
                R.string.onboarding_weight_title, R.string.onboarding_avatar_title};
        int[] texts = {R.string.onboarding_name_text, R.string.onboarding_form_text,
                R.string.onboarding_weight_text, R.string.onboarding_avatar_text};
        stepLabel.setText(getString(R.string.onboarding_step, step + 1, STEPS));
        stepProgress.setProgressCompat(step + 1, true);
        stepTitle.setText(titles[step]);
        stepText.setText(texts[step]);
        back.setVisibility(step == 0 ? View.INVISIBLE : View.VISIBLE);
        next.setText(step == STEPS - 1 ? R.string.onboarding_finish : R.string.onboarding_next);
        updatePreviews();
    }

    /** Mostra, ao vivo, como o Alfred vai cumprimentar e qual será a meta de água. */
    private void updatePreviews() {
        String typed = Honorific.normalizeName(UiUtils.text(name));
        formPreview.setText(getString(R.string.greeting_morning, Honorific.fullName(form, typed)));

        float kg = WaterGoal.parseWeight(UiUtils.text(weight));
        if (WaterGoal.isValidWeight(kg)) {
            int ml = WaterGoal.goalMl(kg, WaterGoal.DEFAULT_ML_PER_KG);
            weightPreview.setText(getString(R.string.profile_water_goal,
                    NumberFormat.getIntegerInstance(com.alfred.agenda.util.DateUtils.PT_BR).format(ml),
                    WaterGoal.DEFAULT_ML_PER_KG));
        } else {
            weightPreview.setText(R.string.onboarding_weight_hint);
        }
    }

    private void finish() {
        UserProfile profile = UserProfile.current(requireContext());
        profile.name = UiUtils.text(name);
        profile.form = form;
        profile.weightKg = WaterGoal.parseWeight(UiUtils.text(weight));
        profile.avatar = avatar;
        profile.onboarded = true;
        if (!profile.save(requireContext())) {
            UiUtils.toast(requireContext(), getString(R.string.state_error_title));
            return;
        }
        ((MainActivity) requireActivity()).onOnboardingDone();
    }

    static String formatWeight(float kg) {
        NumberFormat f = NumberFormat.getNumberInstance(com.alfred.agenda.util.DateUtils.PT_BR);
        f.setMaximumFractionDigits(1);
        f.setGroupingUsed(false);
        return f.format(kg);
    }
}
