package me.cference.dionysus.db.ingredient

import slick.jdbc.SQLiteProfile.api.*

final private[ingredient] case class IngredientRow(
    id: Option[Long],
    name: String,
    caloriesKcal: Double,
    proteinG: Double,
    carbsG: Double,
    fatG: Double,
    sodiumMg: Double,
    abvPercent: Option[Double],
    directlyLoggable: Boolean
)

final private[ingredient] class IngredientTable(tag: Tag)
    extends Table[IngredientRow](tag, "ingredient"):
  def id = column[Long]("id", O.PrimaryKey, O.AutoInc)
  def name = column[String]("name")
  def caloriesKcal = column[Double]("calories_kcal")
  def proteinG = column[Double]("protein_g")
  def carbsG = column[Double]("carbs_g")
  def fatG = column[Double]("fat_g")
  def sodiumMg = column[Double]("sodium_mg")
  def abvPercent = column[Option[Double]]("abv_percent")
  def directlyLoggable = column[Boolean]("directly_loggable")

  def * = (id.?, name, caloriesKcal, proteinG, carbsG, fatG, sodiumMg, abvPercent, directlyLoggable)
    .mapTo[IngredientRow]

private[db] object IngredientTable:
  val ingredients = TableQuery[IngredientTable]
