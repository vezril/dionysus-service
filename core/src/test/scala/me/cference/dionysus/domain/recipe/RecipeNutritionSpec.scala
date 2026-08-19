package me.cference.dionysus.domain.recipe

import me.cference.dionysus.domain.ingredient.Nutrition
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

final class RecipeNutritionSpec extends AnyFunSuite with Matchers:

  private val onion =
    Nutrition(caloriesKcal = 40, proteinG = 1, carbsG = 9, fatG = 0, sodiumMg = 4).toOption.get
  private val soySauce =
    Nutrition(caloriesKcal = 8, proteinG = 1, carbsG = 1, fatG = 0, sodiumMg = 900).toOption.get

  private def nutritionFor(ingredientId: Long): Nutrition =
    ingredientId match
      case 1L => onion
      case 2L => soySauce
      case other => fail(s"unexpected ingredientId in test: $other")

  test("per-serving sodium reflects all lines, divided by servings (spec scenario)") {
    // servings=4, two lines whose combined sodium contribution is 800mg -> 200mg/serving.
    // onion (4mg/unit) x 100 = 400mg; soy sauce (900mg/unit) x (400/900) = 400mg -> total 800mg.
    val recipe = Recipe(
      "Stir Fry",
      servings = 4,
      List(
        RecipeLine(1L, quantity = 100, unit = "g"),
        RecipeLine(2L, quantity = 400.0 / 900.0, unit = "tbsp")
      )
    ).toOption.get
    RecipeNutrition.perServingNutrition(recipe, nutritionFor).sodiumMg shouldBe 200.0 +- 0.0001
  }

  test("total nutrition sums every line's scaled contribution") {
    val recipe = Recipe(
      "Onion only",
      servings = 1,
      List(RecipeLine(1L, quantity = 2, unit = "each"))
    ).toOption.get
    val total = RecipeNutrition.totalNutrition(recipe, nutritionFor)
    total.caloriesKcal shouldBe 80.0
    total.sodiumMg shouldBe 8.0
  }

  test("per-serving nutrition divides the total by servings") {
    val recipe = Recipe(
      "Onion only",
      servings = 2,
      List(RecipeLine(1L, quantity = 2, unit = "each"))
    ).toOption.get
    val perServing = RecipeNutrition.perServingNutrition(recipe, nutritionFor)
    perServing.caloriesKcal shouldBe 40.0
    perServing.sodiumMg shouldBe 4.0
  }
