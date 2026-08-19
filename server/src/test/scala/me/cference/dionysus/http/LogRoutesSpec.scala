package me.cference.dionysus.http

import me.cference.dionysus.db.TestDb
import me.cference.dionysus.db.batch.BatchRepository
import me.cference.dionysus.db.ingredient.IngredientRepository
import me.cference.dionysus.db.meal.MealRepository
import me.cference.dionysus.db.pantry.PantryRepository
import me.cference.dionysus.db.recipe.RecipeRepository
import me.cference.dionysus.domain.batch.Batch
import me.cference.dionysus.domain.ingredient.{Ingredient, Nutrition}
import me.cference.dionysus.domain.meal.{Meal, MealLine}
import me.cference.dionysus.domain.recipe.{Recipe, RecipeLine}
import me.cference.dionysus.http.LogRoutes.DayLogResponse
import org.apache.pekko.http.scaladsl.model.StatusCodes
import org.apache.pekko.http.scaladsl.server.Route
import org.apache.pekko.http.scaladsl.testkit.ScalatestRouteTest
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

import java.time.Instant
import scala.concurrent.Await
import scala.concurrent.duration.DurationInt

final class LogRoutesSpec
    extends AnyFunSuite
    with Matchers
    with ScalatestRouteTest
    with TestDb
    with JsonSupport:

  /**
   * A fresh DB with ingredient id=1 (sodium 4mg/g), recipe id=1 (servings=4, 200g line -> 200mg
   * sodium/serving), and a batch (id=1, servingsMade=8 -> plenty of portions). Two meals logged on
   * 2026-08-19 (600mg + 400mg sodium via portions 3 and 2), one meal logged on 2026-08-18 (100mg
   * sodium via portions 0.5) to prove date-boundary exclusion.
   */
  private def freshRoutes(): Route =
    val db = freshDb()
    val ingredientRepo = new IngredientRepository(db)
    val recipeRepo = new RecipeRepository(db, ingredientRepo)
    val pantryRepo = new PantryRepository(db)
    val batchRepo = new BatchRepository(db, recipeRepo, pantryRepo)
    val mealRepo = new MealRepository(db, batchRepo, recipeRepo, ingredientRepo)

    val ingredient = Ingredient("Salty Thing", Nutrition(40, 1, 9, 0, 4).toOption.get).toOption.get
    Await.result(ingredientRepo.create(ingredient), 5.seconds)
    val recipe =
      Recipe("Recipe", servings = 4, List(RecipeLine(1L, quantity = 200, unit = "g"))).toOption.get
    Await.result(recipeRepo.create(recipe), 5.seconds)
    val batch = Batch(
      recipeId = 1L,
      cookedAt = Instant.parse("2026-08-17T12:00:00Z"),
      servingsMade = 8
    ).toOption.get
    Await.result(batchRepo.create(batch), 5.seconds)

    def logMeal(eatenAt: String, portions: Double): Unit =
      val meal =
        Meal(Instant.parse(eatenAt), List(MealLine.BatchPortionLine(1L, portions))).toOption.get
      Await.result(mealRepo.create(meal), 5.seconds)

    logMeal("2026-08-19T08:00:00Z", portions = 3) // 200mg/serving * 3 = 600mg
    logMeal("2026-08-19T18:00:00Z", portions = 2) // 200mg/serving * 2 = 400mg
    logMeal("2026-08-18T18:00:00Z", portions = 0.5) // different date — must be excluded

    Route.seal(LogRoutes(mealRepo))

  test("GET /api/log/{date} sums multiple meals on that date") {
    Get("/api/log/2026-08-19") ~> freshRoutes() ~> check {
      status shouldBe StatusCodes.OK
      val response = responseAs[DayLogResponse]
      response.totalNutrition.sodiumMg shouldBe 1000.0
      response.meals should have size 2
    }
  }

  test("GET /api/log/{date} excludes meals logged on a different date") {
    Get("/api/log/2026-08-18") ~> freshRoutes() ~> check {
      status shouldBe StatusCodes.OK
      val response = responseAs[DayLogResponse]
      response.totalNutrition.sodiumMg shouldBe 100.0
      response.meals should have size 1
    }
  }

  test("GET /api/log/{date} for a day with zero meals returns zeroed totals, sodium included") {
    Get("/api/log/2026-01-01") ~> freshRoutes() ~> check {
      status shouldBe StatusCodes.OK
      val response = responseAs[DayLogResponse]
      response.totalNutrition.sodiumMg shouldBe 0.0
      response.meals shouldBe empty
    }
  }

  test("GET /api/log/{date} rejects a malformed date") {
    Get("/api/log/not-a-date") ~> freshRoutes() ~> check {
      status shouldBe StatusCodes.BadRequest
    }
  }
