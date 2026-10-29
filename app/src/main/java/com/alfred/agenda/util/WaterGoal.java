package com.alfred.agenda.util;

import android.content.Context;

import com.alfred.agenda.account.UserProfile;
import com.alfred.agenda.data.NutritionPlan;

/**
 * Meta diária de água: peso do perfil × ml por kg.
 * O padrão é 35 ml/kg; se a planilha da dieta importada trouxer outro valor na aba "Água"
 * (a nutricionista pode indicar outro), vale o da planilha.
 */
public final class WaterGoal {

    public static final int DEFAULT_ML_PER_KG = 35;

    /** Faixa aceita para o peso (kg), no onboarding e no perfil. */
    public static final float MIN_WEIGHT_KG = 20f;
    public static final float MAX_WEIGHT_KG = 400f;

    /** Botões de registro rápido na aba Hábitos. */
    public static final int[] QUICK_ADD_ML = {200, 300, 500};

    /** Limite para o total de um dia (evita digitação errada no "Ajustar"). */
    public static final int MAX_DAY_ML = 20_000;

    private WaterGoal() {
    }

    /** Meta em ml (0 se o peso não foi informado ou é inválido). */
    public static int goalMl(float weightKg, int mlPerKg) {
        if (!isValidWeight(weightKg)) return 0;
        int perKg = mlPerKg > 0 ? mlPerKg : DEFAULT_ML_PER_KG;
        return Math.round(weightKg * perKg);
    }

    /** Fração do copo cheio (0 a 1). Com a meta atingida (ou passada) o copo fica cheio. */
    public static float fraction(int consumedMl, int goalMl) {
        if (goalMl <= 0 || consumedMl <= 0) return 0f;
        return Math.min(1f, consumedMl / (float) goalMl);
    }

    /**
     * O copo transborda quando o usuário registra mais água e o total passa da meta
     * (inclusive quando o mesmo registro cruza a meta). Diminuir ou só abrir a tela não transborda.
     */
    public static boolean overflows(int previousMl, int currentMl, int goalMl) {
        return goalMl > 0 && previousMl >= 0 && currentMl > previousMl && currentMl > goalMl;
    }

    /** Soma ao total do dia sem deixar negativo nem passar do limite. */
    public static int add(int currentMl, int deltaMl) {
        return Math.max(0, Math.min(MAX_DAY_ML, currentMl + deltaMl));
    }

    public static boolean isValidWeight(float kg) {
        return kg >= MIN_WEIGHT_KG && kg <= MAX_WEIGHT_KG;
    }

    /** "70,5" ou "70.5" -> 70.5; 0 se não for um número. */
    public static float parseWeight(String text) {
        try {
            return Float.parseFloat(text.trim().replace(',', '.'));
        } catch (NumberFormatException e) {
            return 0f;
        }
    }

    // ===================== Com os dados do usuário logado =====================

    public static int mlPerKg(Context ctx) {
        int fromPlan = NutritionPlan.get(ctx).waterMlPerKg;
        return fromPlan > 0 ? fromPlan : DEFAULT_ML_PER_KG;
    }

    public static int goalMl(Context ctx) {
        return goalMl(UserProfile.current(ctx).weightKg, mlPerKg(ctx));
    }
}
