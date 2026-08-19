package me.cference.dionysus.http

import me.cference.dionysus.db.meal.MealRepository
import me.cference.dionysus.domain.meal.{Meal, MealLine}
import org.apache.pekko.http.scaladsl.model.StatusCodes
import org.apache.pekko.http.scaladsl.server.Directives.*
import org.apache.pekko.http.scaladsl.server.Route
import spray.json.RootJsonFormat

import java.time.Instant
import java.time.format.DateTimeParseException
import scala.concurrent.ExecutionContext
import scala.util.{Failure, Success, Try}

/** `/api/meals` — eating events (openspec: meal-planning-health, capability `meal-logging`). */
object MealRoutes extends JsonSupport:

  /**
   * Flat wire shape mirroring the `meal_line` table: `lineType` disambiguates which of the two line
   * kinds this is, with the other kind's fields simply absent.
   */
  final case class MealLineJson(
      lineType: String,
      batchId: Option[Long] = None,
      portions: Option[Double] = None,
      ingredientId: Option[Long] = None,
      quantity: Option[Double] = None,
      unit: Option[String] = None
  )
  final case class MealRequest(eatenAt: String, lines: List[MealLineJson])
  final case class MealResponse(
      id: Option[Long],
      eatenAt: String,
      lines: List[MealLineJson],
      totalNutrition: NutritionJson
  )

  given RootJsonFormat[MealLineJson] = jsonFormat6(MealLineJson.apply)
  given RootJsonFormat[MealRequest] = jsonFormat2(MealRequest.apply)
  given RootJsonFormat[MealResponse] = jsonFormat4(MealResponse.apply)

  private def lineFromJson(j: MealLineJson): Either[String, MealLine] = j.lineType match
    case "batch_portion" =>
      (j.batchId, j.portions) match
        case (Some(batchId), Some(portions)) => Right(MealLine.BatchPortionLine(batchId, portions))
        case _ => Left("batch_portion line requires batchId and portions")
    case "direct_consumable" =>
      (j.ingredientId, j.quantity, j.unit) match
        case (Some(ingredientId), Some(quantity), Some(unit)) =>
          Right(MealLine.DirectConsumableLine(ingredientId, quantity, unit))
        case _ => Left("direct_consumable line requires ingredientId, quantity, and unit")
    case other => Left(s"unknown line type: $other")

  private def fromRequest(req: MealRequest): Either[String, Meal] =
    Try(Instant.parse(req.eatenAt)) match
      case Failure(_: DateTimeParseException) =>
        Left(s"eatenAt is not a valid ISO-8601 instant: ${req.eatenAt}")
      case Failure(other) => throw other
      case Success(instant) =>
        req.lines
          .foldLeft[Either[String, List[MealLine]]](Right(Nil)) { (acc, lineJson) =>
            for
              lines <- acc
              line <- lineFromJson(lineJson)
            yield lines :+ line
          }
          .flatMap(lines => Meal(instant, lines))

  private def toJson(line: MealLine): MealLineJson = line match
    case MealLine.BatchPortionLine(batchId, portions) =>
      MealLineJson("batch_portion", batchId = Some(batchId), portions = Some(portions))
    case MealLine.DirectConsumableLine(ingredientId, quantity, unit) =>
      MealLineJson(
        "direct_consumable",
        ingredientId = Some(ingredientId),
        quantity = Some(quantity),
        unit = Some(unit)
      )

  private def toResponse(meal: Meal, nutrition: NutritionJson): MealResponse =
    MealResponse(meal.id, meal.eatenAt.toString, meal.lines.map(toJson), nutrition)

  def apply(repo: MealRepository)(using ExecutionContext): Route =
    pathPrefix("api" / "meals") {
      concat(
        pathEndOrSingleSlash {
          post {
            entity(as[MealRequest]) { body =>
              fromRequest(body) match
                case Left(err) => complete(StatusCodes.BadRequest -> ErrorResponse(err))
                case Right(meal) =>
                  onSuccess(repo.create(meal)) {
                    case Left(err) => complete(StatusCodes.BadRequest -> ErrorResponse(err))
                    case Right(created) =>
                      onSuccess(repo.totalNutrition(created)) { nutrition =>
                        complete(StatusCodes.Created -> toResponse(created, toJson(nutrition)))
                      }
                  }
            }
          }
        },
        path(LongNumber) { id =>
          concat(
            get {
              onSuccess(repo.get(id)) {
                case None => complete(StatusCodes.NotFound)
                case Some(meal) =>
                  onSuccess(repo.totalNutrition(meal)) { nutrition =>
                    complete(toResponse(meal, toJson(nutrition)))
                  }
              }
            },
            delete {
              onSuccess(repo.delete(id)) {
                case true => complete(StatusCodes.NoContent)
                case false => complete(StatusCodes.NotFound)
              }
            }
          )
        }
      )
    }
