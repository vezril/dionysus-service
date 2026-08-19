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
    db.run(adjustAction(ingredientId, delta).transactionally)

  /**
   * The upsert as a composable DBIO, so callers (BatchRepository.create) can fold several
   * adjustments plus their own writes into ONE transaction — previously each adjustment ran as its
   * own concurrent transaction, so a failure mid-batch left the batch row committed with partial
   * decrements, and two concurrent upserts for a never-stocked ingredient raced on the INSERT
   * (found in cross-validation review).
   */
  private[db] def adjustAction(ingredientId: Long, delta: Double): DBIO[Unit] =
    for
      rowsUpdated <-
        sqlu"UPDATE pantry_stock SET on_hand_quantity = on_hand_quantity + $delta WHERE ingredient_id = $ingredientId"
      _ <-
        if rowsUpdated == 0 then
          sqlu"INSERT INTO pantry_stock (ingredient_id, on_hand_quantity) VALUES ($ingredientId, $delta)"
        else DBIO.successful(0)
    yield ()
