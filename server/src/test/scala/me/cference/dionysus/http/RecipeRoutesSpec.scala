package me.cference.dionysus.http

import me.cference.dionysus.db.TestDb
import me.cference.dionysus.db.ingredient.IngredientRepository
import me.cference.dionysus.db.recipe.RecipeRepository
import me.cference.dionysus.domain.ingredient.{Ingredient, Nutrition}
import me.cference.dionysus.http.RecipeRoutes.{RecipeLineJson, RecipeRequest, RecipeResponse}
import org.apache.pekko.http.scaladsl.model.StatusCodes
import org.apache.pekko.http.scaladsl.server.Route
import org.apache.pekko.http.scaladsl.testkit.ScalatestRouteTest
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

import scala.concurrent.Await
import scala.concurrent.duration.DurationInt

final class RecipeRoutesSpec
    extends AnyFunSuite
    with Matchers
    with ScalatestRouteTest
    with TestDb
    with JsonSupport:

  /**
   * A fresh DB with one seeded ingredient (id=1, "Onion", sodiumMg=4), plus the recipe routes wired
   * against it.
   */
  private def freshRoutesWithOnion(): Route =
    val db = freshDb()
    val ingredientRepo = new IngredientRepository(db)
    val onion = Ingredient("Onion", Nutrition(40, 1, 9, 0, 4).toOption.get).toOption.get
    Await.result(ingredientRepo.create(onion), 5.seconds)
    Route.seal(RecipeRoutes(new RecipeRepository(db, ingredientRepo)))

  test("POST /api/recipes rejects a line referencing an unknown ingredient id") {
    val request =
      RecipeRequest("Soup", 4, List(RecipeLineJson(ingredientId = 999, quantity = 100, unit = "g")))
    Post("/api/recipes", request) ~> freshRoutesWithOnion() ~> check {
      status shouldBe StatusCodes.BadRequest
    }
  }

  test("POST /api/recipes rejects zero ingredient lines") {
    val request = RecipeRequest("Soup", 4, List.empty)
    Post("/api/recipes", request) ~> freshRoutesWithOnion() ~> check {
      status shouldBe StatusCodes.BadRequest
    }
  }

  test("POST then GET round-trips a valid recipe with correct per-serving nutrition") {
    val routes = freshRoutesWithOnion()
    val request = RecipeRequest(
      "Onion Soup",
      servings = 4,
      List(RecipeLineJson(1L, quantity = 100, unit = "g"))
    )
    Post("/api/recipes", request) ~> routes ~> check {
      status shouldBe StatusCodes.Created
      val created = responseAs[RecipeResponse]
      created.id shouldBe defined
      // onion: 4mg sodium/unit * 100 units = 400mg total / 4 servings = 100mg/serving
      created.perServingNutrition.sodiumMg shouldBe 100.0

      Get(s"/api/recipes/${created.id.get}") ~> routes ~> check {
        status shouldBe StatusCodes.OK
        responseAs[RecipeResponse] shouldBe created
      }
    }
  }

  test("GET /api/recipes lists created recipes with nutrition") {
    val routes = freshRoutesWithOnion()
    val request = RecipeRequest(
      "Onion Soup",
      servings = 4,
      List(RecipeLineJson(1L, quantity = 100, unit = "g"))
    )
    Post("/api/recipes", request) ~> routes ~> check(status shouldBe StatusCodes.Created)
    Get("/api/recipes") ~> routes ~> check {
      status shouldBe StatusCodes.OK
      val all = responseAs[List[RecipeResponse]]
      all.map(_.name) shouldBe List("Onion Soup")
      all.head.perServingNutrition.sodiumMg shouldBe 100.0
    }
  }

  test("GET /api/recipes/{id} for an unknown id returns 404") {
    Get("/api/recipes/999") ~> freshRoutesWithOnion() ~> check {
      status shouldBe StatusCodes.NotFound
    }
  }
