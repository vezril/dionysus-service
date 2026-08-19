package me.cference.dionysus.db.pantry

import slick.jdbc.SQLiteProfile.api.*

import scala.concurrent.{ExecutionContext, Future}

final class PantryRepository(db: Database)(using ExecutionContext):
  import PantryStockTable.pantryStock

  def getOnHand(ingredientId: Long): Future[Double] =
    db.run(
      pantryStock.filter(_.ingredientId === ingredientId).map(_.onHandQuantity).result.headOption
    ).map(_.getOrElse(0.0))

  /**
   * Adjusts on-hand quantity by `delta` (may be negative — going negative is allowed, per spec
   * "Pantry stock may go negative"). Upserts: creates the row (starting at `delta`) if this
   * ingredient has never been stocked before.
   */
  def adjust(ingredientId: Long, delta: Double): Future[Unit] =
    val action =
      for
        rowsUpdated <-
          sqlu"UPDATE pantry_stock SET on_hand_quantity = on_hand_quantity + $delta WHERE ingredient_id = $ingredientId"
        _ <-
          if rowsUpdated == 0 then
            sqlu"INSERT INTO pantry_stock (ingredient_id, on_hand_quantity) VALUES ($ingredientId, $delta)"
          else DBIO.successful(0)
      yield ()
    db.run(action.transactionally)
