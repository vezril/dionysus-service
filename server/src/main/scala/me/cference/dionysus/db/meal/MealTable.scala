package me.cference.dionysus.db.meal

import slick.jdbc.SQLiteProfile.api.*

final private[meal] case class MealRow(id: Option[Long], eatenAt: String)

final private[meal] class MealTable(tag: Tag) extends Table[MealRow](tag, "meal"):
  def id = column[Long]("id", O.PrimaryKey, O.AutoInc)
  def eatenAt = column[String]("eaten_at")
  def * = (id.?, eatenAt).mapTo[MealRow]

/**
 * A line is EITHER a batch-portion (`batchId`+`portions` set) OR a direct-consumable
 * (`ingredientId`+`quantity`+`unit` set) — `lineType` disambiguates rather than relying on
 * nullability alone, matching the V1 migration's `CHECK` constraint.
 */
final private[meal] case class MealLineRow(
    id: Option[Long],
    mealId: Long,
    lineType: String,
    batchId: Option[Long],
    portions: Option[Double],
    ingredientId: Option[Long],
    quantity: Option[Double],
    unit: Option[String]
)

final private[meal] class MealLineTable(tag: Tag) extends Table[MealLineRow](tag, "meal_line"):
  def id = column[Long]("id", O.PrimaryKey, O.AutoInc)
  def mealId = column[Long]("meal_id")
  def lineType = column[String]("line_type")
  def batchId = column[Option[Long]]("batch_id")
  def portions = column[Option[Double]]("portions")
  def ingredientId = column[Option[Long]]("ingredient_id")
  def quantity = column[Option[Double]]("quantity")
  def unit = column[Option[String]]("unit")
  def * =
    (id.?, mealId, lineType, batchId, portions, ingredientId, quantity, unit).mapTo[MealLineRow]

private[db] object MealTable:
  val meals = TableQuery[MealTable]
  val mealLines = TableQuery[MealLineTable]
