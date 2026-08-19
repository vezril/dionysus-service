package me.cference.dionysus.domain.meal

import java.time.Instant

/**
 * One line of a meal — either a portion of a cooked batch, or an ingredient logged directly (eaten
 * as-is, no recipe/batch involved).
 */
sealed trait MealLine

object MealLine:
  final case class BatchPortionLine(batchId: Long, portions: Double) extends MealLine
  final case class DirectConsumableLine(ingredientId: Long, quantity: Double, unit: String)
      extends MealLine

/** A meal is a timestamped eating event with one or more lines. `id` is `None` until persisted. */
final case class Meal private (id: Option[Long], eatenAt: Instant, lines: List[MealLine]):
  def withId(newId: Long): Meal = copy(id = Some(newId))

object Meal:
  /**
   * Smart constructor for a not-yet-persisted meal (`id = None`). Does not check that referenced
   * batch/ingredient IDs exist, or that a batch-portion line stays within the batch's remaining
   * portions — those are the repository's job (it has database access).
   */
  def apply(eatenAt: Instant, lines: List[MealLine]): Either[String, Meal] =
    if lines.isEmpty then Left("meal requires at least one line")
    else if lines.exists(hasNonPositiveAmount) then
      Left("meal line quantity/portions must be positive")
    else Right(new Meal(None, eatenAt, lines))

  /** Reconstitutes a meal already known to be valid (e.g. read back from the database). */
  def fromPersisted(id: Long, eatenAt: Instant, lines: List[MealLine]): Meal =
    new Meal(Some(id), eatenAt, lines)

  private def hasNonPositiveAmount(line: MealLine): Boolean = line match
    case MealLine.BatchPortionLine(_, portions) => portions <= 0
    case MealLine.DirectConsumableLine(_, quantity, _) => quantity <= 0
