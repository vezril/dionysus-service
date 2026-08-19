package me.cference.dionysus.db

import slick.jdbc.SQLiteProfile.api.*

import java.nio.file.Files

/** A fresh, migrated SQLite database backed by a temp file — call once per test for isolation. */
trait TestDb:
  def freshDb(): Database =
    val tempFile = Files.createTempFile("dionysus-test-", ".db")
    tempFile.toFile.deleteOnExit()
    Migrations.run(tempFile.toString)
    Db.open(tempFile.toString)
