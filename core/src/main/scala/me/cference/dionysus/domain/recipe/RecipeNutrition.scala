package me.cference.dionysus.domain.recipe

import me.cference.dionysus.domain.ingredient.Nutrition

/**
 * Recipe nutrition math (openspec: meal-planning-health, capability `recipe-authoring`).
 *
 * Phase 1 has no unit-conversion system (out of scope, per design.md): an ingredient's `Nutrition`
 * is treated as "per 1 unit of whatever quantity a line specifies it in", so a line's contribution
 * is simply `nutritionFor(ingredientId).scale(quantity)`.
 */
object RecipeNutrition:

  /**
   * The recipe's total nutrition (before dividing by servings) — the sum of every line's ingredient
   * nutrition scaled by that line's quantity. `nutritionFor` resolves an ingredient ID to its
   * `Nutrition`.
   */
  def totalNutrition(recipe: Recipe, nutritionFor: Long => Nutrition): Nutrition =
    recipe.lines.foldLeft(Nutrition.zero) { (acc, line) =>
      acc + nutritionFor(line.ingredientId).scale(line.quantity)
    }

  /** Per-serving nutrition: the recipe's total nutrition divided by `recipe.servings`. */
  def perServingNutrition(recipe: Recipe, nutritionFor: Long => Nutrition): Nutrition =
    totalNutrition(recipe, nutritionFor).scale(1.0 / recipe.servings)
