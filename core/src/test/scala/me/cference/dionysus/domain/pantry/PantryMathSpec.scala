package me.cference.dionysus.domain.pantry

import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

final class PantryMathSpec extends AnyFunSuite with Matchers:

  test("decrementFor a batch cooked to the recipe's full servings uses the line quantity as-is") {
    PantryMath.decrementFor(
      lineQuantity = 200,
      recipeServings = 4,
      batchServingsMade = 4
    ) shouldBe 200.0
  }

  test("decrementFor a batch cooked to half the recipe's servings scales proportionally") {
    PantryMath.decrementFor(
      lineQuantity = 200,
      recipeServings = 4,
      batchServingsMade = 2
    ) shouldBe 100.0
  }

  test("decrementFor a batch cooked to more than the recipe's base servings scales up") {
    PantryMath.decrementFor(
      lineQuantity = 200,
      recipeServings = 4,
      batchServingsMade = 8
    ) shouldBe 400.0
  }
