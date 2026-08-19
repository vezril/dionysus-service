package me.cference.dionysus.domain.ingredient

import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

final class IngredientSpec extends AnyFunSuite with Matchers:

  private val nutrition = Nutrition(100, 5, 10, 2, 50).toOption.get

  test("constructs successfully with a non-blank name") {
    val result = Ingredient("Onion", nutrition)
    result shouldBe a[Right[?, ?]]
  }

  test("rejects a blank name") {
    Ingredient("   ", nutrition) shouldBe Left("name must not be blank")
  }

  test("has no id until persisted") {
    val ingredient = Ingredient("Onion", nutrition).toOption.get
    ingredient.id shouldBe None
  }

  test("defaults directlyLoggable to false") {
    val ingredient = Ingredient("Onion", nutrition).toOption.get
    ingredient.directlyLoggable shouldBe false
  }

  test("defaults abvPercent to None") {
    val ingredient = Ingredient("Onion", nutrition).toOption.get
    ingredient.abvPercent shouldBe None
  }

  test("accepts an explicit directlyLoggable and abvPercent") {
    val ingredient =
      Ingredient("Beer", nutrition, abvPercent = Some(5.0), directlyLoggable = true).toOption.get
    ingredient.directlyLoggable shouldBe true
    ingredient.abvPercent shouldBe Some(5.0)
  }

  test("rejects a negative abvPercent") {
    Ingredient("Beer", nutrition, abvPercent = Some(-1.0)) shouldBe Left("abvPercent must be >= 0")
  }

  test("withId attaches an id, e.g. after persisting") {
    val ingredient = Ingredient("Onion", nutrition).toOption.get
    ingredient.withId(7L).id shouldBe Some(7L)
  }

  test("two ingredients with the same name are distinct values") {
    val a = Ingredient("Onion", nutrition).toOption.get.withId(1L)
    val b = Ingredient("Onion", nutrition).toOption.get.withId(2L)
    a.id should not be b.id
  }
