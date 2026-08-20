package me.cference.dionysus.http

import me.cference.dionysus.db.ingredient.IngredientRepository
import me.cference.dionysus.db.pantry.PantryRepository
import org.apache.pekko.http.scaladsl.model.StatusCodes
import org.apache.pekko.http.scaladsl.server.Directives.*
import org.apache.pekko.http.scaladsl.server.Route
import spray.json.RootJsonFormat

import scala.concurrent.ExecutionContext

/**
 * `/api/ingredients/{id}/stock` — on-hand pantry quantity (openspec: meal-planning-health,
 * capability `pantry-stock`). A nonexistent ingredient is a 404: stock is an attribute OF an
 * ingredient, and with foreign_keys=ON (Db.open) the DB would reject the phantom stock row anyway —
 * this surfaced when enabling FK enforcement in the cross-validation fixes.
 */
object PantryRoutes extends JsonSupport:

  final case class StockJson(ingredientId: Long, onHandQuantity: Double)
  final case class AdjustRequest(delta: Double)

  given RootJsonFormat[StockJson] = jsonFormat2(StockJson.apply)
  given RootJsonFormat[AdjustRequest] = jsonFormat1(AdjustRequest.apply)

  def apply(repo: PantryRepository, ingredients: IngredientRepository)(using
      ExecutionContext
  ): Route =
    pathPrefix("api" / "ingredients" / LongNumber / "stock") { ingredientId =>
      onSuccess(ingredients.exists(ingredientId)) {
        case false => complete(StatusCodes.NotFound)
        case true =>
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
    }
