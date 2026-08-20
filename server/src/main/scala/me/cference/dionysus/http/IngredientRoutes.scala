package me.cference.dionysus.http

import me.cference.dionysus.db.ingredient.IngredientRepository
import me.cference.dionysus.domain.ingredient.{Ingredient, Nutrition}
import org.apache.pekko.http.scaladsl.model.StatusCodes
import org.apache.pekko.http.scaladsl.server.Directives.*
import org.apache.pekko.http.scaladsl.server.Route
import spray.json.*

import scala.concurrent.ExecutionContext

/**
 * `/api/ingredients` — the ingredient catalog (openspec: meal-planning-health, capability
 * `ingredient-catalog`).
 */
object IngredientRoutes extends JsonSupport:

  final case class IngredientJson(
      id: Option[Long],
      name: String,
      caloriesKcal: Double,
      proteinG: Double,
      carbsG: Double,
      fatG: Double,
      sodiumMg: Double,
      abvPercent: Option[Double],
      directlyLoggable: Boolean,
      // openspec: micronutrient-rollup — optional on requests (missing/null
      // reads as empty), always echoed on responses.
      micronutrients: Map[String, Double]
  )

  // Hand-written rather than jsonFormat9: `directlyLoggable` must default to false when the key
  // is absent from the request body (spec: "Default is not directly loggable"), which spray-json's
  // jsonFormatN macro can't express for a non-Option field.
  given RootJsonFormat[IngredientJson] with
    def write(obj: IngredientJson): JsValue = JsObject(
      "id" -> obj.id.toJson,
      "name" -> obj.name.toJson,
      "caloriesKcal" -> obj.caloriesKcal.toJson,
      "proteinG" -> obj.proteinG.toJson,
      "carbsG" -> obj.carbsG.toJson,
      "fatG" -> obj.fatG.toJson,
      "sodiumMg" -> obj.sodiumMg.toJson,
      "abvPercent" -> obj.abvPercent.toJson,
      "directlyLoggable" -> obj.directlyLoggable.toJson,
      "micronutrients" -> obj.micronutrients.toJson
    )

    def read(json: JsValue): IngredientJson =
      val fields = json.asJsObject.fields
      // A key mapped to JsNull (e.g. writing back `abvPercent: None`) means "absent" the same as
      // the key being missing entirely — both must read back as None, not fail to convert.
      def optField(key: String): Option[JsValue] = fields.get(key).filterNot(_ == JsNull)
      def required[T: JsonReader](key: String): T =
        optField(key)
          .getOrElse(throw DeserializationException(s"Object is missing required member '$key'"))
          .convertTo[T]
      IngredientJson(
        id = optField("id").map(_.convertTo[Long]),
        name = required[String]("name"),
        caloriesKcal = required[Double]("caloriesKcal"),
        proteinG = required[Double]("proteinG"),
        carbsG = required[Double]("carbsG"),
        fatG = required[Double]("fatG"),
        sodiumMg = required[Double]("sodiumMg"),
        abvPercent = optField("abvPercent").map(_.convertTo[Double]),
        directlyLoggable = optField("directlyLoggable").map(_.convertTo[Boolean]).getOrElse(false),
        micronutrients =
          optField("micronutrients").map(_.convertTo[Map[String, Double]]).getOrElse(Map.empty)
      )

  // spray-json's Iterable/Seq formats are ambiguous for Scala 3 given resolution — pin List explicitly.
  given RootJsonFormat[List[IngredientJson]] = listFormat[IngredientJson]

  def toJson(ingredient: Ingredient): IngredientJson =
    IngredientJson(
      ingredient.id,
      ingredient.name,
      ingredient.nutrition.caloriesKcal,
      ingredient.nutrition.proteinG,
      ingredient.nutrition.carbsG,
      ingredient.nutrition.fatG,
      ingredient.nutrition.sodiumMg,
      ingredient.abvPercent,
      ingredient.directlyLoggable,
      ingredient.nutrition.micronutrients
    )

  private def fromJson(json: IngredientJson): Either[String, Ingredient] =
    for
      nutrition <- Nutrition(
        json.caloriesKcal,
        json.proteinG,
        json.carbsG,
        json.fatG,
        json.sodiumMg,
        json.micronutrients
      )
      ingredient <- Ingredient(json.name, nutrition, json.abvPercent, json.directlyLoggable)
    yield ingredient

  def apply(repo: IngredientRepository)(using ExecutionContext): Route =
    pathPrefix("api" / "ingredients") {
      concat(
        pathEnd {
          concat(
            post {
              entity(as[IngredientJson]) { body =>
                fromJson(body) match
                  case Left(err) => complete(StatusCodes.BadRequest -> ErrorResponse(err))
                  case Right(ingredient) =>
                    onSuccess(repo.create(ingredient)) { created =>
                      complete(StatusCodes.Created -> toJson(created))
                    }
              }
            },
            get {
              onSuccess(repo.list()) { all =>
                complete(all.map(toJson).toList)
              }
            }
          )
        },
        path(LongNumber) { id =>
          concat(
            get {
              onSuccess(repo.get(id)) {
                case Some(ingredient) => complete(toJson(ingredient))
                case None => complete(StatusCodes.NotFound)
              }
            },
            put {
              entity(as[IngredientJson]) { body =>
                fromJson(body) match
                  case Left(err) => complete(StatusCodes.BadRequest -> ErrorResponse(err))
                  case Right(ingredient) =>
                    onSuccess(repo.update(id, ingredient)) {
                      case true => complete(toJson(ingredient.withId(id)))
                      case false => complete(StatusCodes.NotFound)
                    }
              }
            },
            delete {
              onSuccess(repo.delete(id)) {
                case Left(err) => complete(StatusCodes.BadRequest -> ErrorResponse(err))
                case Right(()) => complete(StatusCodes.NoContent)
              }
            }
          )
        }
      )
    }
