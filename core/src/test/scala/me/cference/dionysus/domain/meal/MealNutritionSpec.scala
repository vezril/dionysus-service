package me.cference.dionysus.domain.meal

import me.cference.dionysus.domain.ingredient.Nutrition
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

import java.time.Instant

final class MealNutritionSpec extends AnyFunSuite with Matchers:

  // batchId 1's recipe has per-serving sodium of 200mg; ingredient 2 (a "glass of wine") has 5mg
  // sodium per unit.
  private def perServingNutritionForBatch(batchId: Long): Nutrition =
    batchId match
      case 1L => Nutrition(300, 20, 30, 10, 200).toOption.get
      case other => fail(s"unexpected batchId in test: $other")

  private def nutritionForIngredient(ingredientId: Long): Nutrition =
    ingredientId match
      case 2L => Nutrition(120, 0, 4, 0, 5).toOption.get
      case other => fail(s"unexpected ingredientId in test: $other")

  test("a batch-portion line's nutrition scales the batch's per-serving nutrition by portions") {
    val line = MealLine.BatchPortionLine(batchId = 1L, portions = 2)
    val nutrition =
      MealNutrition.lineNutrition(line, perServingNutritionForBatch, nutritionForIngredient)
    nutrition.sodiumMg shouldBe 400.0
  }

  test("a direct-consumable line's nutrition scales the ingredient's nutrition by quantity") {
    val line = MealLine.DirectConsumableLine(ingredientId = 2L, quantity = 1, unit = "each")
    val nutrition =
      MealNutrition.lineNutrition(line, perServingNutritionForBatch, nutritionForIngredient)
    nutrition.sodiumMg shouldBe 5.0
  }

  test(
    "a mixed meal's total is the sum of both line types (dinner = 1 portion of lasagna + a glass of wine)"
  ) {
    val meal = Meal(
      Instant.EPOCH,
      List(
        MealLine.BatchPortionLine(1L, portions = 1),
        MealLine.DirectConsumableLine(2L, quantity = 1, unit = "each")
      )
    ).toOption.get
    val total =
      MealNutrition.totalNutrition(meal, perServingNutritionForBatch, nutritionForIngredient)
    total.sodiumMg shouldBe 205.0
    total.caloriesKcal shouldBe 420.0
  }
