package me.cference.dionysus.http

import me.cference.dionysus.db.TestDb
import me.cference.dionysus.db.batch.BatchRepository
import me.cference.dionysus.db.ingredient.IngredientRepository
import me.cference.dionysus.db.meal.MealRepository
import me.cference.dionysus.db.pantry.PantryRepository
import me.cference.dionysus.db.recipe.RecipeRepository
import me.cference.dionysus.domain.ingredient.{Ingredient, Nutrition}
import me.cference.dionysus.domain.meal.{Meal, MealLine}
import me.cference.dionysus.domain.recipe.{Recipe, RecipeLine}
import me.cference.dionysus.http.IngredientRoutes.IngredientJson
import org.apache.pekko.http.scaladsl.model.{ContentTypes, HttpEntity, StatusCodes}
import org.apache.pekko.http.scaladsl.server.Route
import org.apache.pekko.http.scaladsl.testkit.ScalatestRouteTest
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

import java.time.Instant
import scala.concurrent.Await
import scala.concurrent.duration.DurationInt

final class IngredientRoutesSpec
    extends AnyFunSuite
    with Matchers
    with ScalatestRouteTest
    with TestDb
    with JsonSupport:

  // Sealed, matching HttpServer.bind in production — turns rejections (e.g. malformed JSON) into
  // real HTTP responses instead of leaving them as raw RouteTest rejections.
  private def freshRoutes = Route.seal(IngredientRoutes(new IngredientRepository(freshDb())))

  test("POST /api/ingredients rejects a body missing sodiumMg") {
    val bodyJson =
      """{"name":"Onion","caloriesKcal":40,"proteinG":1.1,"carbsG":9,"fatG":0.1,"directlyLoggable":false}"""
    Post(
      "/api/ingredients",
      HttpEntity(ContentTypes.`application/json`, bodyJson)
    ) ~> freshRoutes ~> check {
      status shouldBe StatusCodes.BadRequest
    }
  }

  test("POST /api/ingredients rejects a negative sodiumMg") {
    val toCreate = IngredientJson(None, "Onion", 40, 1.1, 9, 0.1, -1, None, false, Map.empty)
    Post("/api/ingredients", toCreate) ~> freshRoutes ~> check {
      status shouldBe StatusCodes.BadRequest
    }
  }

  test("directlyLoggable defaults to false when omitted from the request body") {
    val bodyJson =
      """{"name":"Onion","caloriesKcal":40,"proteinG":1.1,"carbsG":9,"fatG":0.1,"sodiumMg":4}"""
    Post(
      "/api/ingredients",
      HttpEntity(ContentTypes.`application/json`, bodyJson)
    ) ~> freshRoutes ~> check {
      status shouldBe StatusCodes.Created
      responseAs[IngredientJson].directlyLoggable shouldBe false
    }
  }

  test("POST then GET round-trips a valid ingredient") {
    val routes = freshRoutes
    val toCreate =
      IngredientJson(None, "Onion", 40, 1.1, 9, 0.1, 4, None, directlyLoggable = false, Map.empty)
    Post("/api/ingredients", toCreate) ~> routes ~> check {
      status shouldBe StatusCodes.Created
      val created = responseAs[IngredientJson]
      created.id shouldBe defined
      created.sodiumMg shouldBe 4.0

      Get(s"/api/ingredients/${created.id.get}") ~> routes ~> check {
        status shouldBe StatusCodes.OK
        responseAs[IngredientJson] shouldBe created
      }
    }
  }

  test("GET /api/ingredients lists created ingredients") {
    val routes = freshRoutes
    Post(
      "/api/ingredients",
      IngredientJson(None, "Onion", 40, 1.1, 9, 0.1, 4, None, false, Map.empty)
    ) ~> routes ~> check {
      status shouldBe StatusCodes.Created
    }
    Get("/api/ingredients") ~> routes ~> check {
      status shouldBe StatusCodes.OK
      responseAs[List[IngredientJson]].map(_.name) shouldBe List("Onion")
    }
  }

  test("GET /api/ingredients/{id} for an unknown id returns 404") {
    Get("/api/ingredients/999") ~> freshRoutes ~> check {
      status shouldBe StatusCodes.NotFound
    }
  }

  test("PUT /api/ingredients/{id} updates an existing ingredient") {
    val routes = freshRoutes
    val id = Post(
      "/api/ingredients",
      IngredientJson(None, "Onion", 40, 1.1, 9, 0.1, 4, None, false, Map.empty)
    ) ~> routes ~> check {
      responseAs[IngredientJson].id.get
    }
    val updated =
      IngredientJson(
        None,
        "Red Onion",
        44,
        1.2,
        10,
        0.1,
        5,
        None,
        directlyLoggable = true,
        Map.empty
      )
    Put(s"/api/ingredients/$id", updated) ~> routes ~> check {
      status shouldBe StatusCodes.OK
      responseAs[IngredientJson].name shouldBe "Red Onion"
    }
    Get(s"/api/ingredients/$id") ~> routes ~> check {
      responseAs[IngredientJson].directlyLoggable shouldBe true
    }
  }

  test("DELETE /api/ingredients/{id} removes it, subsequent GET is 404") {
    val routes = freshRoutes
    val id = Post(
      "/api/ingredients",
      IngredientJson(None, "Onion", 40, 1.1, 9, 0.1, 4, None, false, Map.empty)
    ) ~> routes ~> check {
      responseAs[IngredientJson].id.get
    }
    Delete(s"/api/ingredients/$id") ~> routes ~> check {
      status shouldBe StatusCodes.NoContent
    }
    Get(s"/api/ingredients/$id") ~> routes ~> check {
      status shouldBe StatusCodes.NotFound
    }
  }

  // With foreign_keys=ON (Db.open), pantry_stock's FK to ingredient would block the delete
  // unless the stock row is removed in the same transaction.
  test(
    "DELETE /api/ingredients/{id} succeeds for an unreferenced ingredient that has pantry stock"
  ) {
    val db = freshDb()
    val ingredientRepo = new IngredientRepository(db)
    val pantryRepo = new PantryRepository(db)
    val routes = Route.seal(IngredientRoutes(ingredientRepo))

    val onion = Ingredient("Onion", Nutrition(40, 1, 9, 0, 4).toOption.get).toOption.get
    val id = Await.result(ingredientRepo.create(onion), 5.seconds).id.get
    Await.result(pantryRepo.adjust(id, 500), 5.seconds)

    Delete(s"/api/ingredients/$id") ~> routes ~> check {
      status shouldBe StatusCodes.NoContent
    }
    Get(s"/api/ingredients/$id") ~> routes ~> check {
      status shouldBe StatusCodes.NotFound
    }
  }

  // Regression: deleting an ingredient still referenced by a recipe line left `GET /api/recipes`
  // throwing a 500 the next time it tried to resolve that line's nutrition (discovered live on
  // the homelab deployment — a test-data cleanup deleted a referenced ingredient).
  test("DELETE /api/ingredients/{id} is rejected when a recipe line references it") {
    val db = freshDb()
    val ingredientRepo = new IngredientRepository(db)
    val recipeRepo = new RecipeRepository(db, ingredientRepo)
    val routes = Route.seal(IngredientRoutes(ingredientRepo))

    val onion = Ingredient("Onion", Nutrition(40, 1, 9, 0, 4).toOption.get).toOption.get
    val id = Await.result(ingredientRepo.create(onion), 5.seconds).id.get
    Await.result(
      recipeRepo.create(Recipe("Soup", servings = 4, List(RecipeLine(id, 200, "g"))).toOption.get),
      5.seconds
    )

    Delete(s"/api/ingredients/$id") ~> routes ~> check {
      status shouldBe StatusCodes.BadRequest
    }
    Get(s"/api/ingredients/$id") ~> routes ~> check {
      status shouldBe StatusCodes.OK
    }
  }

  test(
    "DELETE /api/ingredients/{id} is rejected when a meal's direct-consumable line references it"
  ) {
    val db = freshDb()
    val ingredientRepo = new IngredientRepository(db)
    val recipeRepo = new RecipeRepository(db, ingredientRepo)
    val pantryRepo = new PantryRepository(db)
    val batchRepo = new BatchRepository(db, recipeRepo, pantryRepo)
    val mealRepo = new MealRepository(db, batchRepo, recipeRepo, ingredientRepo)
    val routes = Route.seal(IngredientRoutes(ingredientRepo))

    val wine = Ingredient(
      "Wine",
      Nutrition(125, 0, 4, 0, 5).toOption.get,
      directlyLoggable = true
    ).toOption.get
    val id = Await.result(ingredientRepo.create(wine), 5.seconds).id.get
    Await.result(
      mealRepo.create(
        Meal(Instant.EPOCH, List(MealLine.DirectConsumableLine(id, 1, "each"))).toOption.get
      ),
      5.seconds
    )

    Delete(s"/api/ingredients/$id") ~> routes ~> check {
      status shouldBe StatusCodes.BadRequest
    }
  }
