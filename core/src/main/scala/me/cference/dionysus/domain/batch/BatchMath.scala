package me.cference.dionysus.domain.batch

/**
 * Pure remaining-portions math (openspec: meal-planning-health, capability `batch-tracking`).
 * Always computed, never stored (design.md Decision 4) — this is the single source of truth.
 */
object BatchMath:

  /**
   * `servingsMade` minus every portion already logged against this batch, across any number of
   * meals on any number of days.
   */
  def remainingPortions(servingsMade: Double, loggedPortions: Seq[Double]): Double =
    servingsMade - loggedPortions.sum
