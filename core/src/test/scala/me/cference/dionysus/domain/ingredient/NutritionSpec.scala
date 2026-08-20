package me.cference.dionysus.domain.ingredient

import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

final class NutritionSpec extends AnyFunSuite with Matchers:

  test("constructs successfully with all non-negative fields, including zero sodium") {
    val result = Nutrition(caloriesKcal = 100, proteinG = 5, carbsG = 10, fatG = 2, sodiumMg = 0)
    result shouldBe a[Right[?, ?]]
  }

  test("rejects a negative sodiumMg") {
    val result = Nutrition(caloriesKcal = 100, proteinG = 5, carbsG = 10, fatG = 2, sodiumMg = -1)
    result shouldBe Left("sodiumMg must be >= 0")
  }

  test("rejects a negative caloriesKcal") {
    Nutrition(caloriesKcal = -1, proteinG = 5, carbsG = 10, fatG = 2, sodiumMg = 0) shouldBe
      Left("caloriesKcal must be >= 0")
  }

  test("rejects a negative proteinG") {
    Nutrition(caloriesKcal = 100, proteinG = -1, carbsG = 10, fatG = 2, sodiumMg = 0) shouldBe
      Left("proteinG must be >= 0")
  }

  test("rejects a negative carbsG") {
    Nutrition(caloriesKcal = 100, proteinG = 5, carbsG = -1, fatG = 2, sodiumMg = 0) shouldBe
      Left("carbsG must be >= 0")
  }

  test("rejects a negative fatG") {
    Nutrition(caloriesKcal = 100, proteinG = 5, carbsG = 10, fatG = -1, sodiumMg = 0) shouldBe
      Left("fatG must be >= 0")
  }

  test("zero is the additive identity") {
    val n = Nutrition(100, 5, 10, 2, 200).toOption.get
    (n + Nutrition.zero) shouldBe n
  }

  test("+ sums every field, including sodium") {
    val a = Nutrition(100, 5, 10, 2, 200).toOption.get
    val b = Nutrition(50, 1, 2, 1, 300).toOption.get
    val sum = a + b
    sum.caloriesKcal shouldBe 150
    sum.proteinG shouldBe 6
    sum.carbsG shouldBe 12
    sum.fatG shouldBe 3
    sum.sodiumMg shouldBe 500
  }

  test("scale multiplies every field, including sodium") {
    val n = Nutrition(100, 5, 10, 2, 200).toOption.get
    val scaled = n.scale(2)
    scaled.caloriesKcal shouldBe 200
    scaled.proteinG shouldBe 10
    scaled.carbsG shouldBe 20
    scaled.fatG shouldBe 4
    scaled.sodiumMg shouldBe 400
  }
