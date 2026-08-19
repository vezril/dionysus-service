package me.cference.dionysus.domain.recipe

import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

final class RecipeSpec extends AnyFunSuite with Matchers:

  private val line = RecipeLine(ingredientId = 1L, quantity = 200, unit = "g")

  test("constructs successfully with a name, positive servings, and at least one line") {
    Recipe("Chicken and Rice", 4, List(line)) shouldBe a[Right[?, ?]]
  }

  test("rejects a blank name") {
    Recipe("  ", 4, List(line)) shouldBe Left("name must not be blank")
  }

  test("rejects zero servings") {
    Recipe("Soup", 0, List(line)) shouldBe Left("servings must be >= 1")
  }

  test("rejects negative servings") {
    Recipe("Soup", -1, List(line)) shouldBe Left("servings must be >= 1")
  }

  test("rejects zero ingredient lines") {
    Recipe("Soup", 4, List.empty) shouldBe Left("recipe requires at least one ingredient line")
  }

  test("rejects a line with zero quantity") {
    Recipe("Soup", 4, List(line.copy(quantity = 0))) shouldBe Left(
      "recipe line quantity must be positive"
    )
  }

  test("rejects a line with negative quantity") {
    Recipe("Soup", 4, List(line.copy(quantity = -5))) shouldBe Left(
      "recipe line quantity must be positive"
    )
  }

  test("has no id until persisted") {
    Recipe("Soup", 4, List(line)).toOption.get.id shouldBe None
  }

  test("withId attaches an id") {
    Recipe("Soup", 4, List(line)).toOption.get.withId(9L).id shouldBe Some(9L)
  }
