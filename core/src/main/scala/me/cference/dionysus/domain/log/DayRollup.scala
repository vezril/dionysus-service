package me.cference.dionysus.domain.log

import me.cference.dionysus.domain.ingredient.Nutrition
import me.cference.dionysus.domain.meal.Meal

/** One meal's contribution to a day rollup, alongside its already-resolved total nutrition. */
final case class MealSummary(meal: Meal, nutrition: Nutrition)

/**
 * A day's total nutrition plus the meals that contributed to it (openspec: meal-planning-health,
 * capability `nutrition-rollup`). This is the shape a future HealthKit-bridge Shortcut will `GET
 * /api/log/{date}` for.
 */
final case class DayRollup(totalNutrition: Nutrition, meals: List[MealSummary])

object DayRollup:
  /**
   * Sums nutrition already resolved per meal (nutrition resolution needs database access —
   * batch/ingredient lookups — so it happens in the repository layer; this stays a pure sum).
   * Sodium is always present in the total, even with zero meals, since it's a required field on
   * `Nutrition` — `Nutrition.zero` already has `sodiumMg = 0`, never a missing value.
   */
  def from(mealsWithNutrition: List[(Meal, Nutrition)]): DayRollup =
    val total = mealsWithNutrition.foldLeft(Nutrition.zero) { case (acc, (_, nutrition)) =>
      acc + nutrition
    }
    DayRollup(
      total,
      mealsWithNutrition.map { case (meal, nutrition) => MealSummary(meal, nutrition) }
    )
