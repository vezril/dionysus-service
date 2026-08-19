package me.cference.dionysus.domain.ingredient

/**
 * Nutrition data for one unit of an ingredient (or a pre-scaled total — see `scale`/`+`).
 * `sodiumMg` is a required field, not `Option[Double]`, by design (openspec: meal-planning-health
 * design.md Decision 3) — a `Nutrition` cannot be constructed without it.
 */
final case class Nutrition private (
    caloriesKcal: Double,
    proteinG: Double,
    carbsG: Double,
    fatG: Double,
    sodiumMg: Double
):
  def +(other: Nutrition): Nutrition =
    new Nutrition(
      caloriesKcal + other.caloriesKcal,
      proteinG + other.proteinG,
      carbsG + other.carbsG,
      fatG + other.fatG,
      sodiumMg + other.sodiumMg
    )

  def scale(factor: Double): Nutrition =
    new Nutrition(
      caloriesKcal * factor,
      proteinG * factor,
      carbsG * factor,
      fatG * factor,
      sodiumMg * factor
    )

object Nutrition:
  val zero: Nutrition = new Nutrition(0, 0, 0, 0, 0)

  /** Smart constructor — rejects any negative component. */
  def apply(
      caloriesKcal: Double,
      proteinG: Double,
      carbsG: Double,
      fatG: Double,
      sodiumMg: Double
  ): Either[String, Nutrition] =
    if caloriesKcal < 0 then Left("caloriesKcal must be >= 0")
    else if proteinG < 0 then Left("proteinG must be >= 0")
    else if carbsG < 0 then Left("carbsG must be >= 0")
    else if fatG < 0 then Left("fatG must be >= 0")
    else if sodiumMg < 0 then Left("sodiumMg must be >= 0")
    else Right(new Nutrition(caloriesKcal, proteinG, carbsG, fatG, sodiumMg))
