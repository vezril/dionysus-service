package me.cference.dionysus.http

import me.cference.dionysus.db.meal.MealRepository
import me.cference.dionysus.domain.ingredient.Nutrition
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

  // openspec: log-range
  final case class RangeDayJson(date: String, totalNutrition: NutritionJson, mealCount: Int)
  final case class RangeLogResponse(days: List[RangeDayJson])
  given RootJsonFormat[RangeDayJson] = jsonFormat3(RangeDayJson.apply)
  given RootJsonFormat[RangeLogResponse] = jsonFormat1(RangeLogResponse.apply)

  private val MaxRangeDays = 400L

  private def toResponse(date: LocalDate, rollup: DayRollup): DayLogResponse =
    DayLogResponse(
      date.toString,
      toJson(rollup.totalNutrition),
      rollup.meals.map(s =>
        MealSummaryJson(s.meal.id, s.meal.eatenAt.toString, toJson(s.nutrition))
      )
    )

  def apply(mealRepo: MealRepository)(using ExecutionContext): Route =
    concat(rangeRoute(mealRepo), dayRoute(mealRepo))

  /**
   * openspec: log-range — per-day rollups for an inclusive range in one call, same timezone
   * bucketing as the single-day endpoint. Sparse: days without meals are omitted.
   */
  private def rangeRoute(mealRepo: MealRepository)(using ExecutionContext): Route =
    path("api" / "log" / "range") {
      get {
        parameters("from", "to") { (fromRaw, toRaw) =>
          (Try(LocalDate.parse(fromRaw)), Try(LocalDate.parse(toRaw))) match
            case (Success(from), Success(to)) =>
              if from.isAfter(to) then
                complete(StatusCodes.BadRequest -> ErrorResponse("from must not be after to"))
              else if java.time.temporal.ChronoUnit.DAYS.between(from, to) >= MaxRangeDays then
                complete(
                  StatusCodes.BadRequest -> ErrorResponse(s"range capped at $MaxRangeDays days")
                )
              else
                val responseFuture = mealRepo.listOnRange(from, to).flatMap { byDate =>
                  Future
                    .traverse(byDate.toList.sortBy(_._1)) { (date, meals) =>
                      Future
                        .traverse(meals)(m => mealRepo.totalNutrition(m))
                        .map { nutritions =>
                          val total = nutritions.foldLeft(Nutrition.zero)(_ + _)
                          RangeDayJson(date.toString, toJson(total), meals.size)
                        }
                    }
                    .map(days => RangeLogResponse(days))
                }
                onSuccess(responseFuture)(response => complete(response))
            case _ =>
              complete(
                StatusCodes.BadRequest -> ErrorResponse("from/to must be valid YYYY-MM-DD dates")
              )
        }
      }
    }

  private def dayRoute(mealRepo: MealRepository)(using ExecutionContext): Route =
    // `path`, not `pathPrefix`: the prefix form matched /api/log/{date}/anything.
    path("api" / "log" / Segment) { dateSegment =>
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
