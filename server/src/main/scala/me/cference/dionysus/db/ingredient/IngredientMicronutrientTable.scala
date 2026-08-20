package me.cference.dionysus.db.ingredient

import slick.jdbc.SQLiteProfile.api.*

/** openspec: micronutrient-rollup — sparse key → amount rows per ingredient. */
final private[ingredient] case class IngredientMicronutrientRow(
    ingredientId: Long,
    nutrientKey: String,
    amount: Double
)

final private[ingredient] class IngredientMicronutrientTable(tag: Tag)
    extends Table[IngredientMicronutrientRow](tag, "ingredient_micronutrient"):
  def ingredientId = column[Long]("ingredient_id")
  def nutrientKey = column[String]("nutrient_key")
  def amount = column[Double]("amount")

  def * = (ingredientId, nutrientKey, amount).mapTo[IngredientMicronutrientRow]

private[db] object IngredientMicronutrientTable:
  val micronutrients = TableQuery[IngredientMicronutrientTable]
