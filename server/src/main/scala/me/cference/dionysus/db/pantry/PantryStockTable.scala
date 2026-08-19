package me.cference.dionysus.db.pantry

import slick.jdbc.SQLiteProfile.api.*

final private[pantry] class PantryStockTable(tag: Tag)
    extends Table[(Long, Double)](tag, "pantry_stock"):
  def ingredientId = column[Long]("ingredient_id", O.PrimaryKey)
  def onHandQuantity = column[Double]("on_hand_quantity")
  def * = (ingredientId, onHandQuantity)

private[db] object PantryStockTable:
  val pantryStock = TableQuery[PantryStockTable]
