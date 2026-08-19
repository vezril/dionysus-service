package me.cference.dionysus.db.ingredient

import me.cference.dionysus.domain.ingredient.{Ingredient, Nutrition}
import slick.jdbc.SQLiteProfile.api.*

import scala.concurrent.{ExecutionContext, Future}

final class IngredientRepository(db: Database)(using ExecutionContext):
  import IngredientTable.ingredients

  def create(ingredient: Ingredient): Future[Ingredient] =
    val insertQuery = (ingredients returning ingredients.map(_.id)) += toRow(ingredient)
    db.run(insertQuery).map(ingredient.withId)

  def get(id: Long): Future[Option[Ingredient]] =
    db.run(ingredients.filter(_.id === id).result.headOption).map(_.map(fromRow))

  def list(): Future[Seq[Ingredient]] =
    db.run(ingredients.result).map(_.map(fromRow))

  /** Overwrites every field of the ingredient at `id`. Returns `false` if no such row exists. */
  def update(id: Long, ingredient: Ingredient): Future[Boolean] =
    db.run(ingredients.filter(_.id === id).update(toRow(ingredient).copy(id = Some(id)))).map(_ > 0)

  /**
   * Rejects (without deleting) if any recipe line or meal direct-consumable line still references
   * this ingredient — deleting it out from under a reference left `GET /api/recipes` (which
   * resolves every line's ingredient to compute nutrition) throwing a 500 for any recipe still
   * pointing at the now-missing row. Mirrors `BatchRepository.delete`'s same discipline.
   */
  def delete(id: Long): Future[Either[String, Unit]] =
    isReferenced(id).flatMap {
      case true =>
        Future.successful(Left("cannot delete an ingredient referenced by a recipe or meal"))
      case false => db.run(ingredients.filter(_.id === id).delete).map(_ => Right(()))
    }

  private def isReferenced(id: Long): Future[Boolean] =
    db.run(
      sql"""SELECT
              (EXISTS(SELECT 1 FROM recipe_line WHERE ingredient_id = $id))
              OR (EXISTS(SELECT 1 FROM meal_line WHERE ingredient_id = $id))"""
        .as[Boolean]
    ).map(_.head)

  /**
   * True if `id` refers to an ingredient flagged `directlyLoggable`. Used by meal-logging to
   * validate a direct-consumable line before it's created.
   */
  def isDirectlyLoggable(id: Long): Future[Boolean] =
    db.run(ingredients.filter(r => r.id === id && r.directlyLoggable).exists.result)

  def exists(id: Long): Future[Boolean] =
    db.run(ingredients.filter(_.id === id).exists.result)

  private def toRow(i: Ingredient): IngredientRow =
    IngredientRow(
      i.id,
      i.name,
      i.nutrition.caloriesKcal,
      i.nutrition.proteinG,
      i.nutrition.carbsG,
      i.nutrition.fatG,
      i.nutrition.sodiumMg,
      i.abvPercent,
      i.directlyLoggable
    )

  private def fromRow(r: IngredientRow): Ingredient =
    val nutrition = Nutrition(r.caloriesKcal, r.proteinG, r.carbsG, r.fatG, r.sodiumMg)
      .getOrElse(
        throw IllegalStateException(s"Corrupt ingredient row id=${r.id}: invalid nutrition")
      )
    Ingredient.fromPersisted(
      r.id.getOrElse(throw IllegalStateException("ingredient row missing id")),
      r.name,
      nutrition,
      r.abvPercent,
      r.directlyLoggable
    )
