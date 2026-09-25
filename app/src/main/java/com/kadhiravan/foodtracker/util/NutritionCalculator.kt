package com.kadhiravan.foodtracker.util

import com.kadhiravan.foodtracker.data.prefs.ActivityLevel
import com.kadhiravan.foodtracker.data.prefs.NutritionGoal
import com.kadhiravan.foodtracker.data.prefs.Sex
import kotlin.math.roundToInt

data class NutritionTargets(
    val calorieGoal: Int,
    val proteinG: Int,
    val carbsG: Int,
    val fatG: Int
)

/**
 * Mifflin-St Jeor BMR × an activity multiplier gives TDEE, which is then shifted for the
 * stated goal (a ~500 kcal/day deficit or surplus is a common, moderate rate of change).
 * Protein scales with body weight (higher on a cut to help preserve muscle through a
 * deficit), fat is a flat 25% of calories, and carbs take whatever's left. This is a
 * reasonable general-fitness default, not personalized medical or dietetic advice.
 */
object NutritionCalculator {
    fun calculate(
        age: Int,
        heightCm: Float,
        weightKg: Double,
        sex: Sex,
        activityLevel: ActivityLevel,
        goal: NutritionGoal
    ): NutritionTargets {
        val bmr = when (sex) {
            Sex.MALE -> 10 * weightKg + 6.25 * heightCm - 5 * age + 5
            Sex.FEMALE -> 10 * weightKg + 6.25 * heightCm - 5 * age - 161
        }
        val tdee = bmr * activityLevel.multiplier
        val calorieGoal = when (goal) {
            NutritionGoal.LOSE -> tdee - 500
            NutritionGoal.MAINTAIN -> tdee
            NutritionGoal.GAIN -> tdee + 400
        }.roundToInt().coerceAtLeast(1200)

        val proteinPerKg = if (goal == NutritionGoal.LOSE) 2.0 else 1.8
        val proteinG = (weightKg * proteinPerKg).roundToInt()
        val fatG = (calorieGoal * 0.25 / 9.0).roundToInt()
        val carbsG = ((calorieGoal - proteinG * 4 - fatG * 9) / 4.0).roundToInt().coerceAtLeast(0)

        return NutritionTargets(calorieGoal, proteinG, carbsG, fatG)
    }

    /** Fallback when there isn't a full profile (age/height/sex) yet, a flat 30/40/30
     * split of whatever calorie goal is set, same as before this calculator existed. */
    fun fromCalorieGoalOnly(calorieGoal: Int): NutritionTargets {
        if (calorieGoal <= 0) return NutritionTargets(0, 0, 0, 0)
        return NutritionTargets(
            calorieGoal = calorieGoal,
            proteinG = (calorieGoal * 0.30 / 4.0).roundToInt(),
            carbsG = (calorieGoal * 0.40 / 4.0).roundToInt(),
            fatG = (calorieGoal * 0.30 / 9.0).roundToInt()
        )
    }

    /** A calorie goal the user typed in themselves wins over the calculated one; the
     * calculated macro split is scaled proportionally so it still adds up to that goal. */
    fun withManualGoal(base: NutritionTargets, manualGoal: Int): NutritionTargets {
        if (manualGoal <= 0) return base
        if (base.calorieGoal <= 0) return fromCalorieGoalOnly(manualGoal)
        val factor = manualGoal.toDouble() / base.calorieGoal
        return NutritionTargets(
            calorieGoal = manualGoal,
            proteinG = (base.proteinG * factor).roundToInt(),
            carbsG = (base.carbsG * factor).roundToInt(),
            fatG = (base.fatG * factor).roundToInt()
        )
    }

    /** Calories represented by a macro split, protein/carbs at 4 kcal/g, fat at 9 kcal/g. */
    fun caloriesFor(proteinG: Int, carbsG: Int, fatG: Int): Int = proteinG * 4 + carbsG * 4 + fatG * 9

    /** Replaces [base]'s macro split with a user-chosen one, but only if it actually fits
     * within the same calorie goal, an override that silently blew the calorie budget
     * would be more confusing than useful, so it's ignored (falls back to [base]) instead. */
    fun applyCustomMacros(base: NutritionTargets, proteinG: Int, carbsG: Int, fatG: Int): NutritionTargets {
        if (proteinG <= 0 && carbsG <= 0 && fatG <= 0) return base
        if (caloriesFor(proteinG, carbsG, fatG) > base.calorieGoal) return base
        return base.copy(proteinG = proteinG, carbsG = carbsG, fatG = fatG)
    }
}
