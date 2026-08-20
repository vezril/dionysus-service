package me.cference.dionysus.http

import me.cference.dionysus.db.TestDb
import me.cference.dionysus.db.batch.BatchRepository
import me.cference.dionysus.db.ingredient.IngredientRepository
import me.cference.dionysus.db.meal.MealRepository
import me.cference.dionysus.db.pantry.PantryRepository
import me.cference.dionysus.db.recipe.RecipeRepository
import me.cference.dionysus.domain.ingredient.{Ingredient, Nutrition}
import me.cference.dionysus.domain.meal.{Meal, MealLine}
import me.cference.dionysus.http.LogRoutes.RangeLogResponse
import org.apache.pekko.http.scaladsl.model.StatusCodes
import org.apache.pekko.http.scaladsl.server.Route
import org.apache.pekko.http.scaladsl.testkit.ScalatestRouteTest
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

import java.time.Instant
import scala.concurrent.Await
import scala.concurrent.duration.DurationInt

/** openspec: log-range — one call, per-day rollups, sparse, guarded. */
final class LogRangeSpec
    extends AnyFunSuite
    with Matchers
    with ScalatestRouteTest
    with TestDb
    with JsonSupport:

  private def routesWithMeals(): Route =
    val db = freshDb()
    val ingredientRepo = new IngredientRepository(db)
    val recipeRepo = new RecipeRepository(db, ingredientRepo)
    val pantryRepo = new PantryRepository(db)
    val batchRepo = new BatchRepository(db, recipeRepo, pantryRepo)
    val mealRepo = new MealRepository(db, batchRepo, recipeRepo, ingredientRepo)

    // id=1: directly loggable snack, 100 kcal + vitamin D 25 per unit.
    Await.result(
      ingredientRepo.create(
        Ingredient(
          "Snack",
          Nutrition(100, 5, 10, 2, 50, Map("vitaminD" -> 25.0)).toOption.get,
          directlyLoggable = true
        ).toOption.get
      ),
      5.seconds
    )
    def log(eatenAt: String, quantity: Double): Unit =
      Await.result(
        mealRepo.create(
          Meal(
            Instant.parse(eatenAt),
            List(MealLine.DirectConsumableLine(1L, quantity, "each"))
          ).toOption.get
        ),
        5.seconds
      )
    log("2026-08-18T12:00:00Z", 1) // day 1
    log("2026-08-18T18:00:00Z", 2) // day 1 again
    log("2026-08-20T12:00:00Z", 1) // day 3
    log("2026-08-25T12:00:00Z", 1) // outside range

    Route.seal(LogRoutes(mealRepo))

  test("a week in one call: sparse per-day totals and counts") {
    Get("/api/log/range?from=2026-08-17&to=2026-08-23") ~> routesWithMeals() ~> check {
      status shouldBe StatusCodes.OK
      val days = responseAs[RangeLogResponse].days
      days.map(_.date) shouldBe List("2026-08-18", "2026-08-20")
      days.head.mealCount shouldBe 2
      days.head.totalNutrition.caloriesKcal shouldBe 300.0
      days.head.totalNutrition.micronutrients("vitaminD") shouldBe 75.0
      days(1).mealCount shouldBe 1
    }
  }

  test("guardrails: reversed range and oversized range are 400") {
    val routes = routesWithMeals()
    Get("/api/log/range?from=2026-08-23&to=2026-08-17") ~> routes ~> check {
      status shouldBe StatusCodes.BadRequest
    }
    Get("/api/log/range?from=2020-01-01&to=2026-08-17") ~> routes ~> check {
      status shouldBe StatusCodes.BadRequest
    }
    Get("/api/log/range?from=nope&to=2026-08-17") ~> routes ~> check {
      status shouldBe StatusCodes.BadRequest
    }
  }
