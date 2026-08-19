package me.cference.dionysus.http

import me.cference.dionysus.db.meal.MealRepository
import me.cference.dionysus.domain.log.DayRollup
import me.cference.dionysus.domain.meal.Meal
import org.apache.pekko.http.scaladsl.model.StatusCodes
import org.apache.pekko.http.scaladsl.server.Directives.*
import org.apache.pekko.http.scaladsl.server.Route
import spray.json.RootJsonFormat

import java.time.LocalDate
import java.time.format.DateTimeParseException
import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success, Try}

/**
 * `/api/log/{date}` — the per-day nutrition summary (openspec: meal-planning-health, capability
 * `nutrition-rollup`). Shaped for a future HealthKit-bridge Shortcut to `GET` — not built in this
 * phase, but the shape is deliberate.
 */
object LogRoutes extends JsonSupport:

  final case class MealSummaryJson(id: Option[Long], eatenAt: String, nutrition: NutritionJson)
  final case class DayLogResponse(
      date: String,
      totalNutrition: NutritionJson,
      meals: List[MealSummaryJson]
  )

  given RootJsonFormat[MealSummaryJson] = jsonFormat3(MealSummaryJson.apply)
  given RootJsonFormat[DayLogResponse] = jsonFormat3(DayLogResponse.apply)

  private def toResponse(date: LocalDate, rollup: DayRollup): DayLogResponse =
    DayLogResponse(
      date.toString,
      toJson(rollup.totalNutrition),
      rollup.meals.map(s =>
        MealSummaryJson(s.meal.id, s.meal.eatenAt.toString, toJson(s.nutrition))
      )
    )

  def apply(mealRepo: MealRepository)(using ExecutionContext): Route =
    pathPrefix("api" / "log" / Segment) { dateSegment =>
      get {
        Try(LocalDate.parse(dateSegment)) match
          case Failure(_: DateTimeParseException) =>
            complete(
              StatusCodes.BadRequest -> ErrorResponse(
                s"not a valid date (expected YYYY-MM-DD): $dateSegment"
              )
            )
          case Failure(other) => throw other
          case Success(date) =>
            val rollupFuture: Future[DayRollup] =
              mealRepo.listOnDate(date).flatMap { (meals: Seq[Meal]) =>
                Future
                  .traverse(meals)(m => mealRepo.totalNutrition(m).map(m -> _))
                  .map(_.toList)
                  .map(DayRollup.from)
              }
            onSuccess(rollupFuture) { rollup =>
              complete(toResponse(date, rollup))
            }
      }
    }
