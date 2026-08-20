package me.cference.dionysus.db.recipe

import me.cference.dionysus.db.ingredient.IngredientRepository
import me.cference.dionysus.domain.ingredient.Nutrition
import me.cference.dionysus.domain.recipe.{Recipe, RecipeLine, RecipeNutrition}
import slick.jdbc.SQLiteProfile.api.*

import scala.concurrent.{ExecutionContext, Future}

final class RecipeRepository(db: Database, ingredients: IngredientRepository)(using
    ExecutionContext
):
  import RecipeTable.{recipeLines, recipes}

  /**
   * The recipe's per-serving nutrition, resolving every referenced ingredient's nutrition first.
   * Throws if a line references an ingredient that no longer exists — creation already guarantees
   * every ID was valid at the time, so this should never happen in practice.
   */
  def perServingNutrition(recipe: Recipe): Future[Nutrition] =
    Future
      .traverse(recipe.lines.map(_.ingredientId).distinct) { id =>
        ingredients.get(id).map {
          case Some(ingredient) => id -> ingredient.nutrition
          case None =>
            throw IllegalStateException(s"recipe line references missing ingredient id=$id")
        }
      }
      .map(_.toMap)
      .map(nutritionById => RecipeNutrition.perServingNutrition(recipe, nutritionById))

  /**
   * Validates every line's `ingredientId` exists, then inserts the recipe and its lines together.
   * Returns `Left` (without writing anything) if any ingredient ID is unknown.
   */
  def create(recipe: Recipe): Future[Either[String, Recipe]] =
    Future
      .traverse(recipe.lines)(line =>
        ingredients.exists(line.ingredientId).map(line.ingredientId -> _)
      )
      .flatMap { checks =>
        checks.find(!_._2) match
          case Some((unknownId, _)) => Future.successful(Left(s"unknown ingredientId: $unknownId"))
          case None =>
            val insertAction = for
              recipeId <- (recipes returning recipes
                .map(_.id)) += RecipeRow(None, recipe.name, recipe.servings)
              _ <- recipeLines ++= recipe.lines
                .map(l => RecipeLineRow(None, recipeId, l.ingredientId, l.quantity, l.unit))
            yield recipeId
            db.run(insertAction.transactionally).map(newId => Right(recipe.withId(newId)))
      }

  def get(id: Long): Future[Option[Recipe]] =
    val action =
      for
        recipeRow <- recipes.filter(_.id === id).result.headOption
        lineRows <- recipeLines.filter(_.recipeId === id).result
      yield recipeRow.map(r => toRecipe(r, lineRows))
    db.run(action)

  def list(): Future[Seq[Recipe]] =
    db.run(recipes.result)
      .flatMap { rows =>
        Future.traverse(rows)(r =>
          get(r.id.getOrElse(throw IllegalStateException("recipe row missing id")))
        )
      }
      .map(_.flatten)

  def delete(id: Long): Future[Boolean] =
    val action =
      for
        _ <- recipeLines.filter(_.recipeId === id).delete
        rowsDeleted <- recipes.filter(_.id === id).delete
      yield rowsDeleted > 0
    db.run(action.transactionally)

  def exists(id: Long): Future[Boolean] =
    db.run(recipes.filter(_.id === id).exists.result)

  private def toRecipe(row: RecipeRow, lineRows: Seq[RecipeLineRow]): Recipe =
    Recipe.fromPersisted(
      row.id.getOrElse(throw IllegalStateException("recipe row missing id")),
      row.name,
      row.servings,
      lineRows.map(l => RecipeLine(l.ingredientId, l.quantity, l.unit)).toList
    )
