package me.cference.dionysus.db

import slick.jdbc.SQLiteProfile.api.*

import java.util.Properties

/** The Slick SQLite backend. Callers are responsible for running `Migrations.run` first. */
object Db:

  def open(sqlitePath: String): Database =
    // SQLite ships with foreign-key enforcement OFF per connection — without
    // this pragma every REFERENCES clause in V1__init.sql is inert, so a
    // check-then-act race (e.g. DELETE ingredient vs concurrent POST recipe)
    // could commit a dangling reference (found in cross-validation review).
    // sqlite-jdbc applies connection properties as pragmas.
    val props = new Properties()
    props.setProperty("foreign_keys", "true")
    Database.forURL(s"jdbc:sqlite:$sqlitePath", driver = "org.sqlite.JDBC", prop = props)
