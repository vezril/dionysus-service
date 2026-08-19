package me.cference.dionysus.domain.ingredient

/**
 * An ingredient in the catalog, identified and referenced elsewhere (recipe lines, meal lines) by
 * `id` only — never by `name` (openspec: meal-planning-health, carrying forward dionysus-planner's
 * FR-24 ID-only-matching invariant). `id` is `None` until persisted.
 */
final case class Ingredient private (
    id: Option[Long],
    name: String,
    nutrition: Nutrition,
    abvPercent: Option[Double],
    directlyLoggable: Boolean
):
  def withId(newId: Long): Ingredient = copy(id = Some(newId))

object Ingredient:
  /** Smart constructor for a not-yet-persisted ingredient (`id = None`). */
  def apply(
      name: String,
      nutrition: Nutrition,
      abvPercent: Option[Double] = None,
      directlyLoggable: Boolean = false
  ): Either[String, Ingredient] =
    if name.trim.isEmpty then Left("name must not be blank")
    else if abvPercent.exists(_ < 0) then Left("abvPercent must be >= 0")
    else Right(new Ingredient(None, name.trim, nutrition, abvPercent, directlyLoggable))

  /**
   * Reconstitutes an ingredient already known to be valid (e.g. read back from the database) —
   * bypasses the smart-constructor checks, which the database row already satisfied at write time.
   */
  def fromPersisted(
      id: Long,
      name: String,
      nutrition: Nutrition,
      abvPercent: Option[Double],
      directlyLoggable: Boolean
  ): Ingredient =
    new Ingredient(Some(id), name, nutrition, abvPercent, directlyLoggable)
