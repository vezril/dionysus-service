package me.cference.dionysus.domain.ingredient

import me.cference.dionysus.domain.meal.{Meal, MealLine, MealNutrition}
import me.cference.dionysus.domain.recipe.{Recipe, RecipeLine, RecipeNutrition}
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

import java.time.Instant

/**
 * openspec: micronutrient-rollup — the sparse map on Nutrition: key-wise merge, value-wise scale,
 * validation, and pass-through proof that the recipe/meal math modules needed no changes.
 */
final class MicronutrientSpec extends AnyFunSuite with Matchers:

  private def nutrition(micros: Map[String, Double]): Nutrition =
    Nutrition(100, 5, 10, 2, 50, micros).toOption.get

  test("+ merges key-wise, summing shared keys and keeping disjoint ones") {
    val combined =
      nutrition(Map("vitaminD" -> 25, "iron" -> 2)) + nutrition(Map("iron" -> 1, "zinc" -> 11))
    combined.micronutrients shouldBe Map("vitaminD" -> 25.0, "iron" -> 3.0, "zinc" -> 11.0)
  }

  test("scale multiplies every amount") {
    nutrition(Map("vitaminC" -> 60)).scale(0.5).micronutrients shouldBe Map("vitaminC" -> 30.0)
  }

  test("zero carries the empty map; adding to it is identity for micronutrients") {
    (Nutrition.zero + nutrition(Map("calcium" -> 120))).micronutrients shouldBe Map(
      "calcium" -> 120.0
    )
  }

  test("default construction has no micronutrients (source-compatible call sites)") {
    Nutrition(1, 1, 1, 1, 1).toOption.get.micronutrients shouldBe empty
  }

  test("rejects a blank key") {
    Nutrition(1, 1, 1, 1, 1, Map(" " -> 5)) shouldBe Left("micronutrient keys must not be blank")
  }

  test("rejects a negative amount") {
    Nutrition(1, 1, 1, 1, 1, Map("iron" -> -1)) shouldBe Left("micronutrient amounts must be >= 0")
  }

  test("recipe per-serving nutrition rolls micronutrients up unchanged math") {
    val recipe = Recipe(
      name = "Fixture",
      servings = 2,
      lines = List(RecipeLine(ingredientId = 1, quantity = 2, unit = "each"))
    ).toOption.get
    val perServing =
      RecipeNutrition.perServingNutrition(recipe, Map(1L -> nutrition(Map("vitaminC" -> 30))).apply)
    // 2 units × 30 per unit / 2 servings
    perServing.micronutrients shouldBe Map("vitaminC" -> 30.0)
  }

  test("meal totals scale batch-portion micronutrients by portions") {
    val meal = Meal(
      eatenAt = Instant.parse("2026-08-20T12:00:00Z"),
      lines = List(MealLine.BatchPortionLine(batchId = 7, portions = 1.5))
    ).toOption.get
    val total = MealNutrition.totalNutrition(
      meal,
      perServingNutritionForBatch = _ => nutrition(Map("vitaminD" -> 20)),
      nutritionForIngredient = _ => Nutrition.zero
    )
    total.micronutrients shouldBe Map("vitaminD" -> 30.0)
  }
