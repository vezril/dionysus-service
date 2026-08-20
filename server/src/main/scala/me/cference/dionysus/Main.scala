package me.cference.dionysus

import me.cference.dionysus.build.BuildInfo
import me.cference.dionysus.config.AppConfig
import me.cference.dionysus.db.{Db, Migrations}
import me.cference.dionysus.db.batch.BatchRepository
import me.cference.dionysus.db.ingredient.IngredientRepository
import me.cference.dionysus.db.meal.MealRepository
import me.cference.dionysus.db.pantry.PantryRepository
import me.cference.dionysus.db.recipe.RecipeRepository
import me.cference.dionysus.http.{
  BatchRoutes,
  HealthRoutes,
  HelloRoutes,
  HttpServer,
  IngredientRoutes,
  LogRoutes,
  MealRoutes,
  PantryRoutes,
  RecipeRoutes
}
import com.typesafe.config.ConfigFactory
import org.apache.pekko.actor.typed.scaladsl.Behaviors
import org.apache.pekko.actor.typed.ActorSystem
import org.apache.pekko.http.scaladsl.Http.ServerBinding
import org.apache.pekko.http.scaladsl.server.Directives.*
import org.slf4j.LoggerFactory

import java.util.concurrent.atomic.AtomicBoolean
import scala.util.{Failure, Success}

/**
 * Entry point. Loads configuration, binds the HTTP surface (`GET /` hello + `GET /health`), and
 * wires Pekko Coordinated Shutdown (withdraw readiness -> unbind -> drain -> terminate). A bind
 * failure (e.g. an occupied port) logs clearly and exits non-zero.
 */
object Main:
  private val log = LoggerFactory.getLogger(getClass)

  def main(args: Array[String]): Unit =
    val raw = ConfigFactory.load()
    val cfg = AppConfig.load(raw)

    Migrations.run(cfg.database.sqlitePath)

    given system: ActorSystem[Nothing] =
      ActorSystem[Nothing](Behaviors.empty[Nothing], "dionysus", raw)
    import system.executionContext

    // openspec: meal-planning-health — the eating-and-cooking domain, wired bottom-up since each
    // repository composes the ones before it (batches need recipes+pantry, meals need
    // batches+recipes+ingredients).
    val db = Db.open(cfg.database.sqlitePath)
    val ingredientRepo = new IngredientRepository(db)
    val pantryRepo = new PantryRepository(db)
    val recipeRepo = new RecipeRepository(db, ingredientRepo)
    val batchRepo = new BatchRepository(db, recipeRepo, pantryRepo)
    val mealRepo = new MealRepository(db, batchRepo, recipeRepo, ingredientRepo, cfg.timezone)

    // Readiness flips UP once the server is bound; withdrawn first on shutdown.
    val readiness = new AtomicBoolean(false)
    val routes =
      HelloRoutes() ~ HealthRoutes(BuildInfo.version, () => readiness.get()) ~
        IngredientRoutes(ingredientRepo) ~ PantryRoutes(pantryRepo, ingredientRepo) ~ RecipeRoutes(
          recipeRepo
        ) ~
        BatchRoutes(batchRepo) ~ MealRoutes(mealRepo) ~ LogRoutes(mealRepo)

    HttpServer.bind(routes, cfg.http.host, cfg.http.port).onComplete {
      case Success(binding: ServerBinding) =>
        HttpServer.wireShutdown(binding, readiness)
        readiness.set(true)
        log.info(
          "dionysus {} bound HTTP :{} — readiness UP",
          BuildInfo.version,
          Integer.valueOf(binding.localAddress.getPort)
        )
      case Failure(ex) =>
        log.error(
          s"Failed to bind HTTP ${cfg.http.host}:${cfg.http.port} — ${ex.getMessage}",
          ex
        )
        system.terminate()
        System.exit(1)
    }
