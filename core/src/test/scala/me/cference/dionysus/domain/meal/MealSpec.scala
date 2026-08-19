package me.cference.dionysus.domain.meal

import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

import java.time.Instant

final class MealSpec extends AnyFunSuite with Matchers:

  private val batchLine = MealLine.BatchPortionLine(batchId = 1L, portions = 1)
  private val directLine =
    MealLine.DirectConsumableLine(ingredientId = 2L, quantity = 1, unit = "each")

  test("constructs successfully with at least one line") {
    Meal(Instant.EPOCH, List(batchLine)) shouldBe a[Right[?, ?]]
  }

  test("rejects zero lines") {
    Meal(Instant.EPOCH, List.empty) shouldBe Left("meal requires at least one line")
  }

  test("rejects a batch-portion line with zero portions") {
    Meal(Instant.EPOCH, List(batchLine.copy(portions = 0))) shouldBe
      Left("meal line quantity/portions must be positive")
  }

  test("rejects a batch-portion line with negative portions") {
    Meal(Instant.EPOCH, List(batchLine.copy(portions = -1))) shouldBe
      Left("meal line quantity/portions must be positive")
  }

  test("rejects a direct-consumable line with zero quantity") {
    Meal(Instant.EPOCH, List(directLine.copy(quantity = 0))) shouldBe
      Left("meal line quantity/portions must be positive")
  }

  test("accepts a meal mixing a batch-portion line and a direct-consumable line") {
    Meal(Instant.EPOCH, List(batchLine, directLine)) shouldBe a[Right[?, ?]]
  }

  test("has no id until persisted") {
    Meal(Instant.EPOCH, List(batchLine)).toOption.get.id shouldBe None
  }

  test("withId attaches an id") {
    Meal(Instant.EPOCH, List(batchLine)).toOption.get.withId(9L).id shouldBe Some(9L)
  }
