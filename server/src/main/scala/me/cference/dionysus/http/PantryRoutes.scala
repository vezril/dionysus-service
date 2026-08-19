package me.cference.dionysus.http

import me.cference.dionysus.db.pantry.PantryRepository
import org.apache.pekko.http.scaladsl.server.Directives.*
import org.apache.pekko.http.scaladsl.server.Route
import spray.json.RootJsonFormat

import scala.concurrent.ExecutionContext

/**
 * `/api/ingredients/{id}/stock` — on-hand pantry quantity (openspec: meal-planning-health,
 * capability `pantry-stock`).
 */
object PantryRoutes extends JsonSupport:

  final case class StockJson(ingredientId: Long, onHandQuantity: Double)
  final case class AdjustRequest(delta: Double)

  given RootJsonFormat[StockJson] = jsonFormat2(StockJson.apply)
  given RootJsonFormat[AdjustRequest] = jsonFormat1(AdjustRequest.apply)

  def apply(repo: PantryRepository)(using ExecutionContext): Route =
    pathPrefix("api" / "ingredients" / LongNumber / "stock") { ingredientId =>
      concat(
        pathEnd {
          get {
            onSuccess(repo.getOnHand(ingredientId)) { onHand =>
              complete(StockJson(ingredientId, onHand))
            }
          }
        },
        path("adjust") {
          post {
            entity(as[AdjustRequest]) { body =>
              val adjusted =
                repo.adjust(ingredientId, body.delta).flatMap(_ => repo.getOnHand(ingredientId))
              onSuccess(adjusted) { onHand =>
                complete(StockJson(ingredientId, onHand))
              }
            }
          }
        }
      )
    }
