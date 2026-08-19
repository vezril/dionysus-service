package me.cference.dionysus.http

import me.cference.dionysus.domain.ingredient.Nutrition
import org.apache.pekko.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import spray.json.{DefaultJsonProtocol, RootJsonFormat}

/** A uniform error body for 4xx responses across every routes object. */
final case class ErrorResponse(error: String)

/**
 * The wire shape of a `Nutrition` value — always fully populated (sodium included) since it's only
 * ever a computed output, never a partial user input.
 */
final case class NutritionJson(
    caloriesKcal: Double,
    proteinG: Double,
    carbsG: Double,
    fatG: Double,
    sodiumMg: Double
)

/**
 * Mixed into every `*Routes` object: brings spray-json (de)serialization support plus
 * `DefaultJsonProtocol`'s `jsonFormatN` helpers into scope.
 */
trait JsonSupport extends SprayJsonSupport with DefaultJsonProtocol:
  given RootJsonFormat[ErrorResponse] = jsonFormat1(ErrorResponse.apply)
  given RootJsonFormat[NutritionJson] = jsonFormat5(NutritionJson.apply)

  def toJson(nutrition: Nutrition): NutritionJson =
    NutritionJson(
      nutrition.caloriesKcal,
      nutrition.proteinG,
      nutrition.carbsG,
      nutrition.fatG,
      nutrition.sodiumMg
    )
