package me.cference.dionysus.domain.recipe

/**
 * One line of a recipe — `ingredientId` only, never a name (openspec: meal-planning-health,
 * carrying forward dionysus-planner's FR-24 ID-only-matching invariant).
 */
final case class RecipeLine(ingredientId: Long, quantity: Double, unit: String)

/**
 * A recipe is a template: a name, a servings count, and one or more ingredient lines. `id` is
 * `None` until persisted.
 */
final case class Recipe private (
    id: Option[Long],
    name: String,
    servings: Int,
    lines: List[RecipeLine]
):
  def withId(newId: Long): Recipe = copy(id = Some(newId))

object Recipe:
  /**
   * Smart constructor for a not-yet-persisted recipe (`id = None`). Does not check that referenced
   * ingredient IDs actually exist — that's the repository's job, since it's the one with database
   * access.
   */
  def apply(name: String, servings: Int, lines: List[RecipeLine]): Either[String, Recipe] =
    if name.trim.isEmpty then Left("name must not be blank")
    else if servings < 1 then Left("servings must be >= 1")
    else if lines.isEmpty then Left("recipe requires at least one ingredient line")
    else if lines.exists(_.quantity <= 0) then Left("recipe line quantity must be positive")
    else Right(new Recipe(None, name.trim, servings, lines))

  /** Reconstitutes a recipe already known to be valid (e.g. read back from the database). */
  def fromPersisted(id: Long, name: String, servings: Int, lines: List[RecipeLine]): Recipe =
    new Recipe(Some(id), name, servings, lines)
