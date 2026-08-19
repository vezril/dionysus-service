package me.cference.dionysus.db

import org.flywaydb.core.Flyway

import java.io.File

/**
 * Runs Flyway migrations (`db/migration/V*.sql` on the classpath) against the SQLite file at
 * `sqlitePath`, creating the parent directory first if it doesn't exist yet.
 */
object Migrations:

  def run(sqlitePath: String): Unit =
    Option(new File(sqlitePath).getParentFile).foreach(_.mkdirs())
    Flyway
      .configure()
      .dataSource(s"jdbc:sqlite:$sqlitePath", null, null)
      .locations("classpath:db/migration")
      .load()
      .migrate()
    ()
