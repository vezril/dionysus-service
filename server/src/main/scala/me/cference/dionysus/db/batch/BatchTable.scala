package me.cference.dionysus.db.batch

import slick.jdbc.SQLiteProfile.api.*

final private[batch] case class BatchRow(
    id: Option[Long],
    recipeId: Long,
    cookedAt: String,
    servingsMade: Double
)

final private[batch] class BatchTable(tag: Tag) extends Table[BatchRow](tag, "batch"):
  def id = column[Long]("id", O.PrimaryKey, O.AutoInc)
  def recipeId = column[Long]("recipe_id")
  def cookedAt = column[String]("cooked_at")
  def servingsMade = column[Double]("servings_made")
  def * = (id.?, recipeId, cookedAt, servingsMade).mapTo[BatchRow]

private[db] object BatchTable:
  val batches = TableQuery[BatchTable]
