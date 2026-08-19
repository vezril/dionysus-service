package me.cference.dionysus.db

import slick.jdbc.SQLiteProfile.api.*

/** The Slick SQLite backend. Callers are responsible for running `Migrations.run` first. */
object Db:

  def open(sqlitePath: String): Database =
    Database.forURL(s"jdbc:sqlite:$sqlitePath", driver = "org.sqlite.JDBC")
