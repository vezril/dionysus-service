package me.cference.dionysus.http

import me.cference.dionysus.db.TestDb
import me.cference.dionysus.db.ingredient.IngredientRepository
import me.cference.dionysus.db.pantry.PantryRepository
import me.cference.dionysus.domain.ingredient.{Ingredient, Nutrition}
import me.cference.dionysus.http.PantryRoutes.{AdjustRequest, StockJson}
import org.apache.pekko.http.scaladsl.model.StatusCodes
import org.apache.pekko.http.scaladsl.server.Route
import org.apache.pekko.http.scaladsl.testkit.ScalatestRouteTest
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

import scala.concurrent.Await
import scala.concurrent.duration.DurationInt

final class PantryRoutesSpec
    extends AnyFunSuite
    with Matchers
    with ScalatestRouteTest
    with TestDb
    with JsonSupport:

  /**
   * Fresh DB with ingredient id=1 seeded — with foreign_keys=ON, stock rows for a nonexistent
   * ingredient are rejected, so every stock test needs a real ingredient (and the routes 404 on an
   * unknown one).
   */
  private def freshRoutes: Route =
    val db = freshDb()
    val ingredientRepo = new IngredientRepository(db)
    val onion = Ingredient("Onion", Nutrition(40, 1, 9, 0, 4).toOption.get).toOption.get
    Await.result(ingredientRepo.create(onion), 5.seconds)
    Route.seal(PantryRoutes(new PantryRepository(db), ingredientRepo))

  test("GET stock for a never-stocked ingredient reports zero") {
    Get("/api/ingredients/1/stock") ~> freshRoutes ~> check {
      status shouldBe StatusCodes.OK
      responseAs[StockJson] shouldBe StockJson(1, 0.0)
    }
  }

  test("GET stock for a nonexistent ingredient returns 404") {
    Get("/api/ingredients/999/stock") ~> freshRoutes ~> check {
      status shouldBe StatusCodes.NotFound
    }
  }

  test("adjusting stock for a nonexistent ingredient returns 404") {
    Post("/api/ingredients/999/stock/adjust", AdjustRequest(500)) ~> freshRoutes ~> check {
      status shouldBe StatusCodes.NotFound
    }
  }

  test("manual restock increases on-hand quantity") {
    val routes = freshRoutes
    Post("/api/ingredients/1/stock/adjust", AdjustRequest(500)) ~> routes ~> check {
      status shouldBe StatusCodes.OK
      responseAs[StockJson] shouldBe StockJson(1, 500.0)
    }
  }

  test("adjust can go negative, is not rejected") {
    val routes = freshRoutes
    Post("/api/ingredients/1/stock/adjust", AdjustRequest(-200)) ~> routes ~> check {
      status shouldBe StatusCodes.OK
      responseAs[StockJson] shouldBe StockJson(1, -200.0)
    }
  }

  test("repeated adjustments accumulate") {
    val routes = freshRoutes
    Post("/api/ingredients/1/stock/adjust", AdjustRequest(100)) ~> routes ~> check {
      status shouldBe StatusCodes.OK
    }
    Post("/api/ingredients/1/stock/adjust", AdjustRequest(50)) ~> routes ~> check {
      responseAs[StockJson] shouldBe StockJson(1, 150.0)
    }
  }
