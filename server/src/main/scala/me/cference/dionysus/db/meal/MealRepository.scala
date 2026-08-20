package me.cference.dionysus.db.meal

import me.cference.dionysus.db.batch.BatchRepository
import me.cference.dionysus.db.ingredient.IngredientRepository
import me.cference.dionysus.db.recipe.RecipeRepository
import me.cference.dionysus.domain.ingredient.Nutrition
import me.cference.dionysus.domain.meal.{Meal, MealLine, MealNutrition}
import slick.jdbc.SQLiteProfile.api.*

import java.time.{Instant, LocalDate, ZoneId, ZoneOffset}
import scala.concurrent.{ExecutionContext, Future}

final class MealRepository(
    db: Database,
    batches: BatchRepository,
    recipes: RecipeRepository,
    ingredients: IngredientRepository,
    dayZone: ZoneId = ZoneOffset.UTC
)(using ExecutionContext):
  import MealTable.{mealLines, meals}

  /**
   * Validates every line, then inserts the meal and its lines together. A batch-portion line must
   * reference an existing batch and not exceed its remaining portions; a direct-consumable line
   * must reference an ingredient flagged `directlyLoggable`.
   */
  def create(meal: Meal): Future[Either[String, Meal]] =
    validateLines(meal.lines).flatMap {
      case Left(err) => Future.successful(Left(err))
      case Right(()) =>
        val insertAction =
          for
            mealId <- (meals returning meals.map(_.id)) += MealRow(None, meal.eatenAt.toString)
            _ <- mealLines ++= meal.lines.map(toRow(mealId, _))
          yield mealId
        db.run(insertAction.transactionally).map(newId => Right(meal.withId(newId)))
    }

  def get(id: Long): Future[Option[Meal]] =
    val action =
      for
        mealRow <- meals.filter(_.id === id).result.headOption
        lineRows <- mealLines.filter(_.mealId === id).result
      yield mealRow.map(m => toMeal(m, lineRows))
    db.run(action)

  def delete(id: Long): Future[Boolean] =
    val action =
      for
        _ <- mealLines.filter(_.mealId === id).delete
        rowsDeleted <- meals.filter(_.id === id).delete
      yield rowsDeleted > 0
    db.run(action.transactionally)

  /**
   * Meals whose `eatenAt` falls within `[start, end)` — used by the nutrition-rollup capability.
   * ISO-8601 UTC instants compare correctly as plain strings.
   */
  def listBetween(start: Instant, end: Instant): Future[Seq[Meal]] =
    db.run(meals.filter(m => m.eatenAt >= start.toString && m.eatenAt < end.toString).result)
      .flatMap { rows =>
        Future.traverse(rows)(r =>
          get(r.id.getOrElse(throw IllegalStateException("meal row missing id")))
        )
      }
      .map(_.flatten)

  /**
   * Meals whose `eatenAt` falls on `date` in the configured `dayZone` (openspec:
   * meal-planning-health, capability `nutrition-rollup`). Originally UTC-only per design.md's
   * phase-1 scope; cross-validation showed a naive UTC day flips at 8pm in Montreal, so the zone is
   * now configurable via `dionysus.timezone` / `DIONYSUS_TZ` (default UTC).
   */
  def listOnDate(date: LocalDate): Future[Seq[Meal]] =
    val start = date.atStartOfDay(dayZone).toInstant
    val end = date.plusDays(1).atStartOfDay(dayZone).toInstant
    listBetween(start, end)

  /**
   * openspec: log-range — every meal in the inclusive [from, to] range, bucketed per local date
   * with the SAME zone rule as `listOnDate`.
   */
  def listOnRange(from: LocalDate, to: LocalDate): Future[Map[LocalDate, Seq[Meal]]] =
    val start = from.atStartOfDay(dayZone).toInstant
    val end = to.plusDays(1).atStartOfDay(dayZone).toInstant
    listBetween(start, end).map { meals =>
      meals.groupBy(meal => meal.eatenAt.atZone(dayZone).toLocalDate)
    }

  def totalNutrition(meal: Meal): Future[Nutrition] =
    val batchIds = meal.lines.collect { case MealLine.BatchPortionLine(batchId, _) =>
      batchId
    }.distinct
    val ingredientIds = meal.lines.collect {
      case MealLine.DirectConsumableLine(ingredientId, _, _) => ingredientId
    }.distinct
    for
      batchMap <- Future
        .traverse(batchIds)(id => perServingNutritionForBatch(id).map(id -> _))
        .map(_.toMap)
      ingredientMap <- Future
        .traverse(ingredientIds) { id =>
          ingredients.get(id).map {
            case Some(ingredient) => id -> ingredient.nutrition
            case None =>
              throw IllegalStateException(s"meal line references missing ingredient id=$id")
          }
        }
        .map(_.toMap)
    yield MealNutrition.totalNutrition(
      meal,
      batchId =>
        batchMap.getOrElse(
          batchId,
          throw IllegalStateException(s"missing nutrition for batchId=$batchId")
        ),
      ingredientId =>
        ingredientMap.getOrElse(
          ingredientId,
          throw IllegalStateException(s"missing nutrition for ingredientId=$ingredientId")
        )
    )

  private def perServingNutritionForBatch(batchId: Long): Future[Nutrition] =
    batches.get(batchId).flatMap {
      case None => Future.failed(IllegalStateException(s"batch not found: $batchId"))
      case Some(withRemaining) =>
        recipes.get(withRemaining.batch.recipeId).flatMap {
          case None => Future.failed(IllegalStateException(s"recipe not found for batch $batchId"))
          case Some(recipe) => recipes.perServingNutrition(recipe)
        }
    }

  private def validateLines(lines: List[MealLine]): Future[Either[String, Unit]] =
    // Batch portions are validated as a SUM per batch across the whole meal —
    // per-line checks against the same remaining-portions snapshot let one
    // meal with two lines for the same batch over-log it (found in
    // cross-validation review; spec: meal-logging "total logged portions").
    val portionsByBatch: Map[Long, Double] =
      lines
        .collect { case MealLine.BatchPortionLine(batchId, portions) => batchId -> portions }
        .groupMapReduce(_._1)(_._2)(_ + _)
    val directIngredientIds: List[Long] =
      lines.collect { case MealLine.DirectConsumableLine(ingredientId, _, _) =>
        ingredientId
      }.distinct

    val batchChecks = Future.traverse(portionsByBatch.toList) { case (batchId, totalPortions) =>
      batches.get(batchId).map {
        case None => Some(s"unknown batchId: $batchId")
        case Some(withRemaining) if totalPortions > withRemaining.remainingPortions =>
          Some(
            s"portions ($totalPortions total across this meal) exceeds remaining (${withRemaining.remainingPortions}) for batchId=$batchId"
          )
        case _ => None
      }
    }
    val ingredientChecks = Future.traverse(directIngredientIds) { ingredientId =>
      ingredients
        .isDirectlyLoggable(ingredientId)
        .map(ok => if ok then None else Some(s"ingredientId=$ingredientId is not directlyLoggable"))
    }

    for
      batchErrs <- batchChecks
      ingredientErrs <- ingredientChecks
    yield (batchErrs.flatten ++ ingredientErrs.flatten) match
      case Nil => Right(())
      case errs => Left(errs.mkString("; "))

  private def toRow(mealId: Long, line: MealLine): MealLineRow = line match
    case MealLine.BatchPortionLine(batchId, portions) =>
      MealLineRow(None, mealId, "batch_portion", Some(batchId), Some(portions), None, None, None)
    case MealLine.DirectConsumableLine(ingredientId, quantity, unit) =>
      MealLineRow(
        None,
        mealId,
        "direct_consumable",
        None,
        None,
        Some(ingredientId),
        Some(quantity),
        Some(unit)
      )

  private def toMeal(row: MealRow, lineRows: Seq[MealLineRow]): Meal =
    Meal.fromPersisted(
      row.id.getOrElse(throw IllegalStateException("meal row missing id")),
      Instant.parse(row.eatenAt),
      lineRows.map(toMealLine).toList
    )

  private def toMealLine(row: MealLineRow): MealLine = row.lineType match
    case "batch_portion" =>
      MealLine.BatchPortionLine(
        row.batchId.getOrElse(throw IllegalStateException("batch_portion row missing batch_id")),
        row.portions.getOrElse(throw IllegalStateException("batch_portion row missing portions"))
      )
    case "direct_consumable" =>
      MealLine.DirectConsumableLine(
        row.ingredientId.getOrElse(
          throw IllegalStateException("direct_consumable row missing ingredient_id")
        ),
        row.quantity.getOrElse(
          throw IllegalStateException("direct_consumable row missing quantity")
        ),
        row.unit.getOrElse(throw IllegalStateException("direct_consumable row missing unit"))
      )
    case other => throw IllegalStateException(s"unknown meal_line line_type: $other")
