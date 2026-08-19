package me.cference.dionysus.http

import me.cference.dionysus.db.TestDb
import me.cference.dionysus.db.pantry.PantryRepository
import me.cference.dionysus.http.PantryRoutes.{AdjustRequest, StockJson}
import org.apache.pekko.http.scaladsl.model.StatusCodes
import org.apache.pekko.http.scaladsl.server.Route
import org.apache.pekko.http.scaladsl.testkit.ScalatestRouteTest
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

final class PantryRoutesSpec
    extends AnyFunSuite
    with Matchers
    with ScalatestRouteTest
    with TestDb
    with JsonSupport:

  private def freshRoutes = Route.seal(PantryRoutes(new PantryRepository(freshDb())))

  test("GET stock for a never-stocked ingredient reports zero") {
    Get("/api/ingredients/1/stock") ~> freshRoutes ~> check {
      status shouldBe StatusCodes.OK
      responseAs[StockJson] shouldBe StockJson(1, 0.0)
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
