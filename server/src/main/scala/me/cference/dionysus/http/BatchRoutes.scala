package me.cference.dionysus.http

import me.cference.dionysus.db.batch.{BatchRepository, BatchWithRemaining}
import me.cference.dionysus.domain.batch.Batch
import org.apache.pekko.http.scaladsl.model.StatusCodes
import org.apache.pekko.http.scaladsl.server.Directives.*
import org.apache.pekko.http.scaladsl.server.Route
import spray.json.RootJsonFormat

import java.time.Instant
import java.time.format.DateTimeParseException
import scala.concurrent.ExecutionContext
import scala.util.{Failure, Success, Try}

/**
 * `/api/batches` — cook events (openspec: meal-planning-health, capability `batch-tracking`). No
 * update endpoint: batches are immutable once created.
 */
object BatchRoutes extends JsonSupport:

  final case class BatchRequest(recipeId: Long, cookedAt: String, servingsMade: Double)
  final case class BatchResponse(
      id: Option[Long],
      recipeId: Long,
      cookedAt: String,
      servingsMade: Double,
      remainingPortions: Double
  )

  given RootJsonFormat[BatchRequest] = jsonFormat3(BatchRequest.apply)
  given RootJsonFormat[BatchResponse] = jsonFormat5(BatchResponse.apply)
  // spray-json's Iterable/Seq formats are ambiguous for Scala 3 given resolution — pin List explicitly.
  given RootJsonFormat[List[BatchResponse]] = listFormat[BatchResponse]

  private def fromRequest(req: BatchRequest): Either[String, Batch] =
    // Whole seconds only — same string-comparison rationale as MealRoutes.
    Try(Instant.parse(req.cookedAt).truncatedTo(java.time.temporal.ChronoUnit.SECONDS)) match
      case Failure(_: DateTimeParseException) =>
        Left(s"cookedAt is not a valid ISO-8601 instant: ${req.cookedAt}")
      case Failure(other) => throw other
      case Success(instant) => Batch(req.recipeId, instant, req.servingsMade)

  private def toResponse(withRemaining: BatchWithRemaining): BatchResponse =
    val b = withRemaining.batch
    BatchResponse(
      b.id,
      b.recipeId,
      b.cookedAt.toString,
      b.servingsMade,
      withRemaining.remainingPortions
    )

  def apply(repo: BatchRepository)(using ExecutionContext): Route =
    pathPrefix("api" / "batches") {
      concat(
        pathEndOrSingleSlash {
          concat(
            post {
              entity(as[BatchRequest]) { body =>
                fromRequest(body) match
                  case Left(err) => complete(StatusCodes.BadRequest -> ErrorResponse(err))
                  case Right(batch) =>
                    onSuccess(repo.create(batch)) {
                      case Left(err) => complete(StatusCodes.BadRequest -> ErrorResponse(err))
                      case Right(created) =>
                        onSuccess(
                          repo.get(
                            created.id.getOrElse(
                              throw IllegalStateException("created batch has no id")
                            )
                          )
                        ) {
                          case Some(withRemaining) =>
                            complete(StatusCodes.Created -> toResponse(withRemaining))
                          case None =>
                            throw IllegalStateException("batch vanished immediately after creation")
                        }
                    }
              }
            },
            get {
              onSuccess(repo.list()) { all =>
                complete(all.map(toResponse).toList)
              }
            }
          )
        },
        path(LongNumber) { id =>
          concat(
            get {
              onSuccess(repo.get(id)) {
                case Some(withRemaining) => complete(toResponse(withRemaining))
                case None => complete(StatusCodes.NotFound)
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
