package me.cference.dionysus.domain.ingredient

/**
 * Nutrition data for one unit of an ingredient (or a pre-scaled total — see `scale`/`+`).
 * `sodiumMg` is a required field, not `Option[Double]`, by design (openspec: meal-planning-health
 * design.md Decision 3) — a `Nutrition` cannot be constructed without it.
 *
 * `micronutrients` (openspec: micronutrient-rollup) is a sparse key → amount map (empty = none
 * recorded). `+` merges key-wise and `scale` multiplies values, so micronutrients roll up through
 * recipes, meals, and day logs with zero changes to any math module. Summing what's present makes
 * aggregated values an "at least" figure — deliberately NOT all-or-nothing (design.md D1). Keys are
 * free-form non-blank strings: the planner owns the nutrient registry (design.md D2).
 */
final case class Nutrition private (
    caloriesKcal: Double,
    proteinG: Double,
    carbsG: Double,
    fatG: Double,
    sodiumMg: Double,
    micronutrients: Map[String, Double]
):
  def +(other: Nutrition): Nutrition =
    new Nutrition(
      caloriesKcal + other.caloriesKcal,
      proteinG + other.proteinG,
      carbsG + other.carbsG,
      fatG + other.fatG,
      sodiumMg + other.sodiumMg,
      (micronutrients.keySet ++ other.micronutrients.keySet).map { key =>
        key -> (micronutrients.getOrElse(key, 0.0) + other.micronutrients.getOrElse(key, 0.0))
      }.toMap
    )

  def scale(factor: Double): Nutrition =
    new Nutrition(
      caloriesKcal * factor,
      proteinG * factor,
      carbsG * factor,
      fatG * factor,
      sodiumMg * factor,
      micronutrients.view.mapValues(_ * factor).toMap
    )

object Nutrition:
  val zero: Nutrition = new Nutrition(0, 0, 0, 0, 0, Map.empty)

  /**
   * Smart constructor — rejects any negative component, blank micronutrient keys, and negative
   * micronutrient amounts.
   */
  def apply(
      caloriesKcal: Double,
      proteinG: Double,
      carbsG: Double,
      fatG: Double,
      sodiumMg: Double,
      micronutrients: Map[String, Double] = Map.empty
  ): Either[String, Nutrition] =
    if caloriesKcal < 0 then Left("caloriesKcal must be >= 0")
    else if proteinG < 0 then Left("proteinG must be >= 0")
    else if carbsG < 0 then Left("carbsG must be >= 0")
    else if fatG < 0 then Left("fatG must be >= 0")
    else if sodiumMg < 0 then Left("sodiumMg must be >= 0")
    else if micronutrients.keys.exists(_.trim.isEmpty) then
      Left("micronutrient keys must not be blank")
    else if micronutrients.values.exists(_ < 0) then Left("micronutrient amounts must be >= 0")
    else Right(new Nutrition(caloriesKcal, proteinG, carbsG, fatG, sodiumMg, micronutrients))
