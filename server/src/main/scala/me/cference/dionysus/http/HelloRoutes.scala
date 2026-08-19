package me.cference.dionysus.http

import me.cference.dionysus.Greeting
import org.apache.pekko.http.scaladsl.model.*
import org.apache.pekko.http.scaladsl.server.Directives.*
import org.apache.pekko.http.scaladsl.server.Route

/** `GET /` -> a plain-text hello, sourced from the pure `core` Greeting. */
object HelloRoutes:

  def apply(): Route =
    pathEndOrSingleSlash {
      get {
        complete(HttpEntity(ContentTypes.`text/plain(UTF-8)`, Greeting.message()))
      }
    }
