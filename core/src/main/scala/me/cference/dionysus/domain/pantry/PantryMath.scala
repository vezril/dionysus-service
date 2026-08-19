package me.cference.dionysus.domain.pantry

/** Pure pantry-decrement math (openspec: meal-planning-health, capability `pantry-stock`). */
object PantryMath:

  /**
   * How much of a recipe line's ingredient to decrement from pantry stock when a batch is cooked:
   * the line's quantity scaled by how many of the recipe's servings the batch actually made
   * (`batchServingsMade / recipeServings`).
   */
  def decrementFor(lineQuantity: Double, recipeServings: Int, batchServingsMade: Double): Double =
    lineQuantity * (batchServingsMade / recipeServings)
