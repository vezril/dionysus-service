package me.cference.dionysus.domain.batch

import java.time.Instant

/**
 * A batch is a single cook event — "I made this recipe" — immutable once created (openspec:
 * meal-planning-health design.md Decision 4). `id` is `None` until persisted.
 */
final case class Batch private (
    id: Option[Long],
    recipeId: Long,
    cookedAt: Instant,
    servingsMade: Double
):
  def withId(newId: Long): Batch = copy(id = Some(newId))

object Batch:
  /**
   * Smart constructor for a not-yet-persisted batch (`id = None`). Does not check that `recipeId`
   * actually exists — that's the repository's job.
   */
  def apply(recipeId: Long, cookedAt: Instant, servingsMade: Double): Either[String, Batch] =
    if servingsMade <= 0 then Left("servingsMade must be positive")
    else Right(new Batch(None, recipeId, cookedAt, servingsMade))

  /** Reconstitutes a batch already known to be valid (e.g. read back from the database). */
  def fromPersisted(id: Long, recipeId: Long, cookedAt: Instant, servingsMade: Double): Batch =
    new Batch(Some(id), recipeId, cookedAt, servingsMade)
