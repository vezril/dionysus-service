package me.cference.dionysus.db.ingredient

import me.cference.dionysus.domain.ingredient.{Ingredient, Nutrition}
import slick.jdbc.SQLiteProfile.api.*

import scala.concurrent.{ExecutionContext, Future}

final class IngredientRepository(db: Database)(using ExecutionContext):
  import IngredientMicronutrientTable.micronutrients
  import IngredientTable.ingredients

  def create(ingredient: Ingredient): Future[Ingredient] =
    // openspec: micronutrient-rollup — the ingredient row and its sparse
    // micronutrient rows land in one transaction.
    val action = for
      newId <- (ingredients returning ingredients.map(_.id)) += toRow(ingredient)
      _ <- micronutrients ++= microRows(newId, ingredient)
    yield newId
    db.run(action.transactionally).map(ingredient.withId)

  def get(id: Long): Future[Option[Ingredient]] =
    val action = for
      row <- ingredients.filter(_.id === id).result.headOption
      micros <- micronutrients.filter(_.ingredientId === id).result
    yield row.map(fromRow(_, micros))
    db.run(action)

  def list(): Future[Seq[Ingredient]] =
    val action = for
      rows <- ingredients.result
      micros <- micronutrients.result
    yield
      val byIngredient = micros.groupBy(_.ingredientId)
      rows.map(row => fromRow(row, byIngredient.getOrElse(row.id.getOrElse(-1L), Seq.empty)))
    db.run(action)

  /**
   * Overwrites every field of the ingredient at `id`, replace-setting its micronutrient rows in the
   * same transaction. Returns `false` if no such row exists.
   */
  def update(id: Long, ingredient: Ingredient): Future[Boolean] =
    val action = for
      updated <- ingredients.filter(_.id === id).update(toRow(ingredient).copy(id = Some(id)))
      _ <-
        if updated > 0 then
          for
            _ <- micronutrients.filter(_.ingredientId === id).delete
            _ <- micronutrients ++= microRows(id, ingredient)
          yield ()
        else DBIO.successful(())
    yield updated > 0
    db.run(action.transactionally)

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
      case false =>
        // pantry_stock's PK (and micronutrient rows) reference ingredient(id);
        // with foreign_keys=ON (Db.open) they must go in the same transaction
        // or the ingredient delete itself would trip the FK.
        val action = for
          _ <- sqlu"DELETE FROM pantry_stock WHERE ingredient_id = $id"
          _ <- micronutrients.filter(_.ingredientId === id).delete
          _ <- ingredients.filter(_.id === id).delete
        yield ()
        db.run(action.transactionally).map(Right(_))
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

  private def microRows(ingredientId: Long, i: Ingredient): Seq[IngredientMicronutrientRow] =
    i.nutrition.micronutrients.toSeq.map { case (key, amount) =>
      IngredientMicronutrientRow(ingredientId, key, amount)
    }

  private def fromRow(r: IngredientRow, micros: Seq[IngredientMicronutrientRow]): Ingredient =
    val nutrition = Nutrition(
      r.caloriesKcal,
      r.proteinG,
      r.carbsG,
      r.fatG,
      r.sodiumMg,
      micros.map(m => m.nutrientKey -> m.amount).toMap
    ).getOrElse(
      throw IllegalStateException(s"Corrupt ingredient row id=${r.id}: invalid nutrition")
    )
    Ingredient.fromPersisted(
      r.id.getOrElse(throw IllegalStateException("ingredient row missing id")),
      r.name,
      nutrition,
      r.abvPercent,
      r.directlyLoggable
    )
