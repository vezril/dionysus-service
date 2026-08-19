package me.cference.dionysus.db.recipe

import slick.jdbc.SQLiteProfile.api.*

final private[recipe] case class RecipeRow(id: Option[Long], name: String, servings: Int)

final private[recipe] class RecipeTable(tag: Tag) extends Table[RecipeRow](tag, "recipe"):
  def id = column[Long]("id", O.PrimaryKey, O.AutoInc)
  def name = column[String]("name")
  def servings = column[Int]("servings")
  def * = (id.?, name, servings).mapTo[RecipeRow]

final private[recipe] case class RecipeLineRow(
    id: Option[Long],
    recipeId: Long,
    ingredientId: Long,
    quantity: Double,
    unit: String
)

final private[recipe] class RecipeLineTable(tag: Tag)
    extends Table[RecipeLineRow](tag, "recipe_line"):
  def id = column[Long]("id", O.PrimaryKey, O.AutoInc)
  def recipeId = column[Long]("recipe_id")
  def ingredientId = column[Long]("ingredient_id")
  def quantity = column[Double]("quantity")
  def unit = column[String]("unit")
  def * = (id.?, recipeId, ingredientId, quantity, unit).mapTo[RecipeLineRow]

private[db] object RecipeTable:
  val recipes = TableQuery[RecipeTable]
  val recipeLines = TableQuery[RecipeLineTable]
