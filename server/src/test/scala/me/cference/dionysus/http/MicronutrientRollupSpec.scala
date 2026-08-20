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
import me.cference.dionysus.http.IngredientRoutes.IngredientJson
import me.cference.dionysus.http.LogRoutes.DayLogResponse
import org.apache.pekko.http.scaladsl.model.{ContentTypes, HttpEntity, StatusCodes}
import org.apache.pekko.http.scaladsl.server.Route
import org.apache.pekko.http.scaladsl.testkit.ScalatestRouteTest
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

import java.time.Instant
import scala.concurrent.Await
import scala.concurrent.duration.DurationInt

/**
 * openspec: micronutrient-rollup — wire round-trip (create/read/update, omitted-field back-compat,
 * validation) and the rollup path: supplement in a day log, batch-portion scaling.
 */
final class MicronutrientRollupSpec
    extends AnyFunSuite
    with Matchers
    with ScalatestRouteTest
    with TestDb
    with JsonSupport:

  test("POST + GET round-trips micronutrients; PUT replace-sets them") {
    val db = freshDb()
    val repo = new IngredientRepository(db)
    val routes = Route.seal(IngredientRoutes(repo))

    val toCreate =
      IngredientJson(None, "Vitamin D3", 0, 0, 0, 0, 0, None, true, Map("vitaminD" -> 25.0))
    Post("/api/ingredients", toCreate) ~> routes ~> check {
      status shouldBe StatusCodes.Created
      responseAs[IngredientJson].micronutrients shouldBe Map("vitaminD" -> 25.0)
    }
    Get("/api/ingredients/1") ~> routes ~> check {
      responseAs[IngredientJson].micronutrients shouldBe Map("vitaminD" -> 25.0)
    }

    val replaced =
      IngredientJson(
        None,
        "Vitamin D3",
        0,
        0,
        0,
        0,
        0,
        None,
        true,
        Map("vitaminD" -> 50.0, "calcium" -> 120.0)
      )
    Put("/api/ingredients/1", replaced) ~> routes ~> check {
      status shouldBe StatusCodes.OK
    }
    Get("/api/ingredients/1") ~> routes ~> check {
      responseAs[IngredientJson].micronutrients shouldBe Map("vitaminD" -> 50.0, "calcium" -> 120.0)
    }
  }

  test("a body omitting micronutrients creates an ingredient with an empty map (back-compat)") {
    val routes = Route.seal(IngredientRoutes(new IngredientRepository(freshDb())))
    val bodyJson =
      """{"name":"Onion","caloriesKcal":40,"proteinG":1.1,"carbsG":9,"fatG":0.1,"sodiumMg":4}"""
    Post(
      "/api/ingredients",
      HttpEntity(ContentTypes.`application/json`, bodyJson)
    ) ~> routes ~> check {
      status shouldBe StatusCodes.Created
      responseAs[IngredientJson].micronutrients shouldBe empty
    }
  }

  test("a negative micronutrient amount is a 400 and stores nothing") {
    val db = freshDb()
    val repo = new IngredientRepository(db)
    val routes = Route.seal(IngredientRoutes(repo))
    val bad = IngredientJson(None, "Bad", 0, 0, 0, 0, 0, None, false, Map("iron" -> -1.0))
    Post("/api/ingredients", bad) ~> routes ~> check {
      status shouldBe StatusCodes.BadRequest
    }
    Await.result(repo.list(), 5.seconds) shouldBe empty
  }

  test("day log totals include a supplement's micronutrients and scale batch portions") {
    val db = freshDb()
    val ingredientRepo = new IngredientRepository(db)
    val recipeRepo = new RecipeRepository(db, ingredientRepo)
    val pantryRepo = new PantryRepository(db)
    val batchRepo = new BatchRepository(db, recipeRepo, pantryRepo)
    val mealRepo = new MealRepository(db, batchRepo, recipeRepo, ingredientRepo)

    // id=1: supplement, directly loggable, vitamin D 25 per unit.
    Await.result(
      ingredientRepo.create(
        Ingredient(
          "Vitamin D3",
          Nutrition(0, 0, 0, 0, 0, Map("vitaminD" -> 25.0)).toOption.get,
          directlyLoggable = true
        ).toOption.get
      ),
      5.seconds
    )
    // id=2: food with vitamin C 0.3/g; recipe 200 g across 2 servings -> 30 per serving.
    Await.result(
      ingredientRepo.create(
        Ingredient(
          "OJ",
          Nutrition(0.45, 0, 0.1, 0, 0, Map("vitaminC" -> 0.3)).toOption.get
        ).toOption.get
      ),
      5.seconds
    )
    Await.result(
      recipeRepo.create(
        Recipe(
          "Juice",
          servings = 2,
          List(RecipeLine(2L, quantity = 200, unit = "mL"))
        ).toOption.get
      ),
      5.seconds
    )
    Await.result(
      batchRepo.create(
        Batch(
          recipeId = 1L,
          cookedAt = Instant.parse("2026-08-20T08:00:00Z"),
          servingsMade = 2
        ).toOption.get
      ),
      5.seconds
    )

    // One meal: 1 supplement capsule + 1.5 portions of the juice batch.
    Await.result(
      mealRepo.create(
        Meal(
          Instant.parse("2026-08-20T12:00:00Z"),
          List(
            MealLine.DirectConsumableLine(ingredientId = 1L, quantity = 1, unit = "each"),
            MealLine.BatchPortionLine(batchId = 1L, portions = 1.5)
          )
        ).toOption.get
      ),
      5.seconds
    )

    val routes = Route.seal(LogRoutes(mealRepo))
    Get("/api/log/2026-08-20") ~> routes ~> check {
      status shouldBe StatusCodes.OK
      val totals = responseAs[DayLogResponse].totalNutrition.micronutrients
      totals("vitaminD") shouldBe 25.0
      totals("vitaminC") shouldBe 45.0 +- 1e-9 // 30 per serving × 1.5 portions
    }
  }
