package me.cference.dionysus.domain.batch

import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

import java.time.Instant

final class BatchSpec extends AnyFunSuite with Matchers:

  test("constructs successfully with a positive servingsMade") {
    Batch(recipeId = 1L, cookedAt = Instant.EPOCH, servingsMade = 4) shouldBe a[Right[?, ?]]
  }

  test("rejects zero servingsMade") {
    Batch(recipeId = 1L, cookedAt = Instant.EPOCH, servingsMade = 0) shouldBe Left(
      "servingsMade must be positive"
    )
  }

  test("rejects negative servingsMade") {
    Batch(recipeId = 1L, cookedAt = Instant.EPOCH, servingsMade = -1) shouldBe Left(
      "servingsMade must be positive"
    )
  }

  test("has no id until persisted") {
    Batch(1L, Instant.EPOCH, 4).toOption.get.id shouldBe None
  }

  test("withId attaches an id") {
    Batch(1L, Instant.EPOCH, 4).toOption.get.withId(9L).id shouldBe Some(9L)
  }
