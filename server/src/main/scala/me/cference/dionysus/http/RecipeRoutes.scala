package me.cference.dionysus.http

import me.cference.dionysus.db.recipe.RecipeRepository
import me.cference.dionysus.domain.recipe.{Recipe, RecipeLine}
import org.apache.pekko.http.scaladsl.model.StatusCodes
import org.apache.pekko.http.scaladsl.server.Directives.*
import org.apache.pekko.http.scaladsl.server.Route
import spray.json.RootJsonFormat

import scala.concurrent.{ExecutionContext, Future}

/**
 * `/api/recipes` — recipe templates (openspec: meal-planning-health, capability
 * `recipe-authoring`).
 */
object RecipeRoutes extends JsonSupport:

  final case class RecipeLineJson(ingredientId: Long, quantity: Double, unit: String)
  final case class RecipeRequest(name: String, servings: Int, lines: List[RecipeLineJson])
  final case class RecipeResponse(
      id: Option[Long],
      name: String,
      servings: Int,
      lines: List[RecipeLineJson],
      perServingNutrition: NutritionJson
  )

  given RootJsonFormat[RecipeLineJson] = jsonFormat3(RecipeLineJson.apply)
  given RootJsonFormat[RecipeRequest] = jsonFormat3(RecipeRequest.apply)
  given RootJsonFormat[RecipeResponse] = jsonFormat5(RecipeResponse.apply)
  given RootJsonFormat[List[RecipeResponse]] = listFormat[RecipeResponse]

  private def fromRequest(req: RecipeRequest): Either[String, Recipe] =
    Recipe(
      req.name,
      req.servings,
      req.lines.map(l => RecipeLine(l.ingredientId, l.quantity, l.unit))
    )

  private def toResponse(recipe: Recipe, perServing: NutritionJson): RecipeResponse =
    RecipeResponse(
      recipe.id,
      recipe.name,
      recipe.servings,
      recipe.lines.map(l => RecipeLineJson(l.ingredientId, l.quantity, l.unit)),
      perServing
    )

  private def withNutrition(repo: RecipeRepository, recipe: Recipe)(using
      ExecutionContext
  ): Future[RecipeResponse] =
    repo.perServingNutrition(recipe).map(n => toResponse(recipe, toJson(n)))

  def apply(repo: RecipeRepository)(using ExecutionContext): Route =
    pathPrefix("api" / "recipes") {
      concat(
        pathEnd {
          concat(
            post {
              entity(as[RecipeRequest]) { body =>
                fromRequest(body) match
                  case Left(err) => complete(StatusCodes.BadRequest -> ErrorResponse(err))
                  case Right(recipe) =>
                    onSuccess(repo.create(recipe)) {
                      case Left(err) => complete(StatusCodes.BadRequest -> ErrorResponse(err))
                      case Right(created) =>
                        onSuccess(withNutrition(repo, created)) { response =>
                          complete(StatusCodes.Created -> response)
                        }
                    }
              }
            },
            get {
              onSuccess(repo.list()) { all =>
                onSuccess(Future.traverse(all)(r => withNutrition(repo, r))) { responses =>
                  complete(responses.toList)
                }
              }
            }
          )
        },
        path(LongNumber) { id =>
          get {
            onSuccess(repo.get(id)) {
              case None => complete(StatusCodes.NotFound)
              case Some(recipe) =>
                onSuccess(withNutrition(repo, recipe)) { response =>
                  complete(response)
                }
            }
          }
        }
      )
    }
