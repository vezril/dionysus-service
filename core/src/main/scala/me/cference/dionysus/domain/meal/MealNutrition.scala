package me.cference.dionysus.domain.meal

import me.cference.dionysus.domain.ingredient.Nutrition

/**
 * Meal nutrition math (openspec: meal-planning-health, capability `meal-logging`). Both resolvers
 * are injected rather than looked up here — this stays a pure function of already- resolved
 * nutrition data, with all the database access left to the repository layer.
 */
object MealNutrition:

  /**
   * A single line's nutrition contribution: a batch-portion line scales the batch's recipe's
   * per-serving nutrition by `portions`; a direct-consumable line scales the ingredient's nutrition
   * by `quantity`.
   */
  def lineNutrition(
      line: MealLine,
      perServingNutritionForBatch: Long => Nutrition,
      nutritionForIngredient: Long => Nutrition
  ): Nutrition = line match
    case MealLine.BatchPortionLine(batchId, portions) =>
      perServingNutritionForBatch(batchId).scale(portions)
    case MealLine.DirectConsumableLine(ingredientId, quantity, _) =>
      nutritionForIngredient(ingredientId).scale(quantity)

  /**
   * The meal's total nutrition — the sum of every line's contribution. Sodium is always present
   * (never optional) since it's a field on `Nutrition` itself.
   */
  def totalNutrition(
      meal: Meal,
      perServingNutritionForBatch: Long => Nutrition,
      nutritionForIngredient: Long => Nutrition
  ): Nutrition =
    meal.lines.foldLeft(Nutrition.zero) { (acc, line) =>
      acc + lineNutrition(line, perServingNutritionForBatch, nutritionForIngredient)
    }
