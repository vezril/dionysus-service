package me.cference.dionysus.db.batch

import me.cference.dionysus.db.pantry.PantryRepository
import me.cference.dionysus.db.recipe.RecipeRepository
import me.cference.dionysus.domain.batch.{Batch, BatchMath}
import me.cference.dionysus.domain.pantry.PantryMath
import slick.jdbc.SQLiteProfile.api.*

import java.time.Instant
import scala.concurrent.{ExecutionContext, Future}

final case class BatchWithRemaining(batch: Batch, remainingPortions: Double)

final class BatchRepository(db: Database, recipes: RecipeRepository, pantry: PantryRepository)(using
    ExecutionContext
):
  import BatchTable.batches

  /**
   * Validates the recipe exists, then inserts the batch and decrements pantry stock for every
   * recipe line in ONE transaction (design.md Decision 4 / pantry-stock capability) — a partial
   * failure must not leave a committed batch with half-applied decrements. Duplicate ingredient
   * lines are summed first so the upsert never touches the same row twice.
   */
  def create(batch: Batch): Future[Either[String, Batch]] =
    recipes.get(batch.recipeId).flatMap {
      case None => Future.successful(Left(s"unknown recipeId: ${batch.recipeId}"))
      case Some(recipe) =>
        val row = BatchRow(None, batch.recipeId, batch.cookedAt.toString, batch.servingsMade)
        val deltaByIngredient: Map[Long, Double] = recipe.lines
          .groupMapReduce(_.ingredientId)(line =>
            PantryMath.decrementFor(line.quantity, recipe.servings, batch.servingsMade)
          )(_ + _)
        val action = for
          newId <- (batches returning batches.map(_.id)) += row
          _ <- DBIO.sequence(deltaByIngredient.toList.map { case (ingredientId, delta) =>
            pantry.adjustAction(ingredientId, -delta)
          })
        yield newId
        db.run(action.transactionally).map(newId => Right(batch.withId(newId)))
    }

  def list(): Future[Seq[BatchWithRemaining]] =
    db.run(batches.result).flatMap { rows =>
      Future.traverse(rows) { row =>
        val b = toBatch(row)
        loggedPortionsFor(b.id.getOrElse(throw IllegalStateException("batch row missing id"))).map {
          logged =>
            BatchWithRemaining(b, BatchMath.remainingPortions(b.servingsMade, logged))
        }
      }
    }

  def get(id: Long): Future[Option[BatchWithRemaining]] =
    db.run(batches.filter(_.id === id).result.headOption).flatMap {
      case None => Future.successful(None)
      case Some(row) =>
        loggedPortionsFor(id).map { logged =>
          val b = toBatch(row)
          Some(BatchWithRemaining(b, BatchMath.remainingPortions(b.servingsMade, logged)))
        }
    }

  def exists(id: Long): Future[Boolean] =
    db.run(batches.filter(_.id === id).exists.result)

  /** Rejects (without deleting) if any meal line still references this batch. */
  def delete(id: Long): Future[Either[String, Unit]] =
    hasMealLines(id).flatMap {
      case true => Future.successful(Left("cannot delete a batch referenced by a meal"))
      case false => db.run(batches.filter(_.id === id).delete).map(_ => Right(()))
    }

  private def loggedPortionsFor(batchId: Long): Future[Seq[Double]] =
    db.run(
      sql"SELECT portions FROM meal_line WHERE batch_id = $batchId AND line_type = 'batch_portion'"
        .as[Double]
    )

  private def hasMealLines(batchId: Long): Future[Boolean] =
    db.run(sql"SELECT COUNT(*) FROM meal_line WHERE batch_id = $batchId".as[Int]).map(_.head > 0)

  private def toBatch(row: BatchRow): Batch =
    Batch.fromPersisted(
      row.id.getOrElse(throw IllegalStateException("batch row missing id")),
      row.recipeId,
      Instant.parse(row.cookedAt),
      row.servingsMade
    )
