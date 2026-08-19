package me.cference.dionysus.http

import me.cference.dionysus.db.TestDb
import me.cference.dionysus.db.batch.BatchRepository
import me.cference.dionysus.db.ingredient.IngredientRepository
import me.cference.dionysus.db.pantry.PantryRepository
import me.cference.dionysus.db.recipe.RecipeRepository
import me.cference.dionysus.domain.ingredient.{Ingredient, Nutrition}
import me.cference.dionysus.domain.recipe.{Recipe, RecipeLine}
import me.cference.dionysus.http.BatchRoutes.{BatchRequest, BatchResponse}
import org.apache.pekko.http.scaladsl.model.StatusCodes
import org.apache.pekko.http.scaladsl.server.Route
import org.apache.pekko.http.scaladsl.testkit.ScalatestRouteTest
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import slick.jdbc.SQLiteProfile.api.*

import scala.concurrent.Await
import scala.concurrent.duration.DurationInt

final class BatchRoutesSpec
    extends AnyFunSuite
    with Matchers
    with ScalatestRouteTest
    with TestDb
    with JsonSupport:

  private case class Fixture(
      db: Database,
      pantry: PantryRepository,
      batchRepo: BatchRepository,
      routes: Route
  )

  /**
   * A fresh DB seeded with ingredient id=1 ("Onion") and recipe id=1 (servings=4, one line: 200g of
   * ingredient 1), plus batch routes wired against it.
   */
  private def freshFixture(): Fixture =
    val db = freshDb()
    val ingredientRepo = new IngredientRepository(db)
    val recipeRepo = new RecipeRepository(db, ingredientRepo)
    val pantryRepo = new PantryRepository(db)
    val batchRepo = new BatchRepository(db, recipeRepo, pantryRepo)

    val onion = Ingredient("Onion", Nutrition(40, 1, 9, 0, 4).toOption.get).toOption.get
    Await.result(ingredientRepo.create(onion), 5.seconds)
    val recipe = Recipe(
      "Onion Soup",
      servings = 4,
      List(RecipeLine(1L, quantity = 200, unit = "g"))
    ).toOption.get
    Await.result(recipeRepo.create(recipe), 5.seconds)

    Fixture(db, pantryRepo, batchRepo, Route.seal(BatchRoutes(batchRepo)))

  test("POST /api/batches rejects an unknown recipeId") {
    val fixture = freshFixture()
    val request = BatchRequest(recipeId = 999, cookedAt = "2026-08-19T12:00:00Z", servingsMade = 4)
    Post("/api/batches", request) ~> fixture.routes ~> check {
      status shouldBe StatusCodes.BadRequest
    }
  }

  test("POST /api/batches rejects a non-positive servingsMade") {
    val fixture = freshFixture()
    val request = BatchRequest(recipeId = 1, cookedAt = "2026-08-19T12:00:00Z", servingsMade = 0)
    Post("/api/batches", request) ~> fixture.routes ~> check {
      status shouldBe StatusCodes.BadRequest
    }
  }

  test("POST /api/batches decrements pantry stock for the recipe's ingredients") {
    val fixture = freshFixture()
    val request = BatchRequest(recipeId = 1, cookedAt = "2026-08-19T12:00:00Z", servingsMade = 4)
    Post("/api/batches", request) ~> fixture.routes ~> check {
      status shouldBe StatusCodes.Created
      val created = responseAs[BatchResponse]
      created.remainingPortions shouldBe 4.0

      val onHand = Await.result(fixture.pantry.getOnHand(1L), 5.seconds)
      onHand shouldBe -200.0 // started at 0, batch used 200g, allowed to go negative
    }
  }

  test("GET /api/batches lists created batches with computed remaining portions") {
    val fixture = freshFixture()
    Post("/api/batches", BatchRequest(1, "2026-08-19T12:00:00Z", 4)) ~> fixture.routes ~> check {
      status shouldBe StatusCodes.Created
    }
    Get("/api/batches") ~> fixture.routes ~> check {
      status shouldBe StatusCodes.OK
      val all = responseAs[List[BatchResponse]]
      all should have size 1
      all.head.recipeId shouldBe 1
      all.head.remainingPortions shouldBe 4.0
    }
  }

  test("GET /api/batches on an empty database returns an empty list") {
    Get("/api/batches") ~> freshFixture().routes ~> check {
      status shouldBe StatusCodes.OK
      responseAs[List[BatchResponse]] shouldBe empty
    }
  }

  test("GET /api/batches/{id} for an unknown id returns 404") {
    Get("/api/batches/999") ~> freshFixture().routes ~> check {
      status shouldBe StatusCodes.NotFound
    }
  }

  test("DELETE /api/batches/{id} succeeds when no meal references it") {
    val fixture = freshFixture()
    val request = BatchRequest(recipeId = 1, cookedAt = "2026-08-19T12:00:00Z", servingsMade = 4)
    val id = Post("/api/batches", request) ~> fixture.routes ~> check {
      responseAs[BatchResponse].id.get
    }
    Delete(s"/api/batches/$id") ~> fixture.routes ~> check {
      status shouldBe StatusCodes.NoContent
    }
  }

  test("DELETE /api/batches/{id} is rejected when a meal references it") {
    val fixture = freshFixture()
    val request = BatchRequest(recipeId = 1, cookedAt = "2026-08-19T12:00:00Z", servingsMade = 4)
    val id = Post("/api/batches", request) ~> fixture.routes ~> check {
      responseAs[BatchResponse].id.get
    }

    // Insert a meal + meal_line referencing this batch directly (meal-logging capability isn't
    // built yet — this is exactly the row shape it will produce).
    Await.result(
      fixture.db.run(sqlu"INSERT INTO meal (eaten_at) VALUES ('2026-08-19T18:00:00Z')"),
      5.seconds
    )
    Await.result(
      fixture.db.run(
        sqlu"INSERT INTO meal_line (meal_id, line_type, batch_id, portions) VALUES (1, 'batch_portion', $id, 1)"
      ),
      5.seconds
    )

    Delete(s"/api/batches/$id") ~> fixture.routes ~> check {
      status shouldBe StatusCodes.BadRequest
    }
  }
