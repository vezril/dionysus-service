package me.cference.dionysus.domain.log

import me.cference.dionysus.domain.ingredient.Nutrition
import me.cference.dionysus.domain.meal.{Meal, MealLine}
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

import java.time.Instant

final class DayRollupSpec extends AnyFunSuite with Matchers:

  private def meal(portions: Double) =
    Meal(Instant.EPOCH, List(MealLine.BatchPortionLine(1L, portions))).toOption.get

  test("an empty day reports zero totals, sodium included, never missing") {
    val rollup = DayRollup.from(List.empty)
    rollup.totalNutrition shouldBe Nutrition.zero
    rollup.totalNutrition.sodiumMg shouldBe 0.0
    rollup.meals shouldBe empty
  }

  test("sums multiple meals' sodium into the day total (spec scenario: 1200mg + 800mg = 2000mg)") {
    val n1 = Nutrition(300, 20, 30, 10, 1200).toOption.get
    val n2 = Nutrition(200, 15, 20, 5, 800).toOption.get
    val rollup = DayRollup.from(List(meal(1) -> n1, meal(2) -> n2))
    rollup.totalNutrition.sodiumMg shouldBe 2000.0
  }

  test("lists each contributing meal alongside its own nutrition") {
    val n1 = Nutrition(300, 20, 30, 10, 1200).toOption.get
    val n2 = Nutrition(200, 15, 20, 5, 800).toOption.get
    val rollup = DayRollup.from(List(meal(1) -> n1, meal(2) -> n2))
    rollup.meals.map(_.nutrition.sodiumMg) shouldBe List(1200.0, 800.0)
  }
