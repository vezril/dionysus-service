package me.cference.dionysus.config

import com.typesafe.config.Config

/** Typed view over the `dionysus.http` config block. */
final case class HttpConfig(host: String, port: Int)

/** Typed view over the `dionysus.database` config block — the SQLite file path. */
final case class DatabaseConfig(sqlitePath: String)

final case class AppConfig(http: HttpConfig, database: DatabaseConfig)

object AppConfig:

  /** Read + type the operational config. Fails fast (Typesafe Config throws) on a missing key. */
  def load(raw: Config): AppConfig =
    val http = raw.getConfig("dionysus.http")
    val database = raw.getConfig("dionysus.database")
    AppConfig(
      HttpConfig(http.getString("host"), http.getInt("port")),
      DatabaseConfig(database.getString("sqlite-path"))
    )
