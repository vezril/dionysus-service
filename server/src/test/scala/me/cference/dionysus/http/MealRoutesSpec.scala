package me.cference.dionysus.http

import me.cference.dionysus.db.TestDb
import me.cference.dionysus.db.batch.BatchRepository
import me.cference.dionysus.db.ingredient.IngredientRepository
import me.cference.dionysus.db.meal.MealRepository
import me.cference.dionysus.db.pantry.PantryRepository
import me.cference.dionysus.db.recipe.RecipeRepository
import me.cference.dionysus.domain.batch.Batch
import me.cference.dionysus.domain.ingredient.{Ingredient, Nutrition}
import me.cference.dionysus.domain.recipe.{Recipe, RecipeLine}
import me.cference.dionysus.http.MealRoutes.{MealLineJson, MealRequest, MealResponse}
import org.apache.pekko.http.scaladsl.model.StatusCodes
import org.apache.pekko.http.scaladsl.server.Route
import org.apache.pekko.http.scaladsl.testkit.ScalatestRouteTest
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

import java.time.Instant
import scala.concurrent.Await
import scala.concurrent.duration.DurationInt

final class MealRoutesSpec
    extends AnyFunSuite
    with Matchers
    with ScalatestRouteTest
    with TestDb
    with JsonSupport:

  /**
   * A fresh DB seeded with:
   *   - ingredient id=1 "Onion" (NOT directly loggable)
   *   - ingredient id=2 "Wine" (directly loggable, sodium 5mg/unit)
   *   - recipe id=1 "Onion Soup" (servings=4, one line: 200g of ingredient 1)
   *   - batch id=1 (recipeId=1, servingsMade=4 -> 4 portions remaining)
   */
  private def freshRoutes(): Route =
    val db = freshDb()
    val ingredientRepo = new IngredientRepository(db)
    val recipeRepo = new RecipeRepository(db, ingredientRepo)
    val pantryRepo = new PantryRepository(db)
    val batchRepo = new BatchRepository(db, recipeRepo, pantryRepo)
    val mealRepo = new MealRepository(db, batchRepo, recipeRepo, ingredientRepo)

    val onion = Ingredient("Onion", Nutrition(40, 1, 9, 0, 4).toOption.get).toOption.get
    Await.result(ingredientRepo.create(onion), 5.seconds)
    val wine = Ingredient(
      "Wine",
      Nutrition(125, 0, 4, 0, 5).toOption.get,
      directlyLoggable = true
    ).toOption.get
    Await.result(ingredientRepo.create(wine), 5.seconds)

    val recipe = Recipe(
      "Onion Soup",
      servings = 4,
      List(RecipeLine(1L, quantity = 200, unit = "g"))
    ).toOption.get
    Await.result(recipeRepo.create(recipe), 5.seconds)

    val batch = Batch(
      recipeId = 1L,
      cookedAt = Instant.parse("2026-08-17T18:00:00Z"),
      servingsMade = 4
    ).toOption.get
    Await.result(batchRepo.create(batch), 5.seconds)

    Route.seal(MealRoutes(mealRepo))

  test("POST /api/meals rejects a batch-portion line exceeding the batch's remaining portions") {
    val request = MealRequest(
      eatenAt = "2026-08-19T18:00:00Z",
      lines = List(MealLineJson("batch_portion", batchId = Some(1L), portions = Some(5)))
    )
    Post("/api/meals", request) ~> freshRoutes() ~> check {
      status shouldBe StatusCodes.BadRequest
    }
  }

  test("POST /api/meals accepts a batch-portion line logging exactly the remaining portions") {
    val request = MealRequest(
      eatenAt = "2026-08-19T18:00:00Z",
      lines = List(MealLineJson("batch_portion", batchId = Some(1L), portions = Some(4)))
    )
    Post("/api/meals", request) ~> freshRoutes() ~> check {
      status shouldBe StatusCodes.Created
    }
  }

  // Regression (cross-validation review): per-line checks against the same remaining-portions
  // snapshot let one meal with several lines for the same batch over-log it. The sum across the
  // meal must be validated, not each line independently.
  test("POST /api/meals rejects multiple lines for the same batch whose SUM exceeds remaining") {
    val request = MealRequest(
      eatenAt = "2026-08-19T18:00:00Z",
      lines = List(
        MealLineJson("batch_portion", batchId = Some(1L), portions = Some(3)),
        MealLineJson("batch_portion", batchId = Some(1L), portions = Some(3))
      )
    )
    Post("/api/meals", request) ~> freshRoutes() ~> check {
      status shouldBe StatusCodes.BadRequest
    }
  }

  test("POST /api/meals accepts multiple lines for the same batch summing to exactly remaining") {
    val request = MealRequest(
      eatenAt = "2026-08-19T18:00:00Z",
      lines = List(
        MealLineJson("batch_portion", batchId = Some(1L), portions = Some(2)),
        MealLineJson("batch_portion", batchId = Some(1L), portions = Some(2))
      )
    )
    Post("/api/meals", request) ~> freshRoutes() ~> check {
      status shouldBe StatusCodes.Created
    }
  }

  // Regression (cross-validation review): instants are stored as ISO strings and range-compared
  // lexicographically; a fractional-second value sorts before its own whole-second form. The
  // route truncates to whole seconds on write so day-boundary queries stay correct.
  test("POST /api/meals truncates a fractional-second eatenAt to whole seconds") {
    val request = MealRequest(
      eatenAt = "2026-08-19T00:00:00.500Z",
      lines = List(MealLineJson("batch_portion", batchId = Some(1L), portions = Some(1)))
    )
    Post("/api/meals", request) ~> freshRoutes() ~> check {
      status shouldBe StatusCodes.Created
      responseAs[MealResponse].eatenAt shouldBe "2026-08-19T00:00:00Z"
    }
  }

  test(
    "POST /api/meals rejects a direct-consumable line for an ingredient that is not directlyLoggable"
  ) {
    val request = MealRequest(
      eatenAt = "2026-08-19T18:00:00Z",
      lines = List(
        MealLineJson(
          "direct_consumable",
          ingredientId = Some(1L),
          quantity = Some(1),
          unit = Some("g")
        )
      )
    )
    Post("/api/meals", request) ~> freshRoutes() ~> check {
      status shouldBe StatusCodes.BadRequest
    }
  }

  test("POST then GET round-trips a mixed meal (1 batch portion + a directly-logged consumable)") {
    val routes = freshRoutes()
    val request = MealRequest(
      eatenAt = "2026-08-19T18:00:00Z",
      lines = List(
        MealLineJson("batch_portion", batchId = Some(1L), portions = Some(1)),
        MealLineJson(
          "direct_consumable",
          ingredientId = Some(2L),
          quantity = Some(1),
          unit = Some("each")
        )
      )
    )
    Post("/api/meals", request) ~> routes ~> check {
      status shouldBe StatusCodes.Created
      val created = responseAs[MealResponse]
      created.lines should have size 2
      // batch portion: recipe per-serving sodium = 200g * 4mg/g / 4 servings = 200mg; portions=1 -> 200mg
      // direct consumable: wine 5mg/unit * 1 = 5mg
      created.totalNutrition.sodiumMg shouldBe 205.0

      Get(s"/api/meals/${created.id.get}") ~> routes ~> check {
        status shouldBe StatusCodes.OK
        responseAs[MealResponse] shouldBe created
      }
    }
  }

  test("GET /api/meals/{id} for an unknown id returns 404") {
    Get("/api/meals/999") ~> freshRoutes() ~> check {
      status shouldBe StatusCodes.NotFound
    }
  }

  test("DELETE /api/meals/{id} removes it, subsequent GET is 404") {
    val routes = freshRoutes()
    val request = MealRequest(
      eatenAt = "2026-08-19T18:00:00Z",
      lines = List(MealLineJson("batch_portion", batchId = Some(1L), portions = Some(1)))
    )
    val id = Post("/api/meals", request) ~> routes ~> check(responseAs[MealResponse].id.get)
    Delete(s"/api/meals/$id") ~> routes ~> check {
      status shouldBe StatusCodes.NoContent
    }
    Get(s"/api/meals/$id") ~> routes ~> check {
      status shouldBe StatusCodes.NotFound
    }
  }
