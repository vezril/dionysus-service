## 1. Persistence foundation

- [x] 1.1 Add Slick, the SQLite JDBC driver (`xerial`), and Flyway as `server` dependencies in `build.sbt`
- [x] 1.2 Add `server/src/main/resources/db/migration/V1__init.sql` (Flyway) defining tables for `ingredient`, `pantry_stock`, `recipe`, `recipe_line`, `batch`, `meal`, `meal_line`
- [x] 1.3 Wire Flyway migration-on-startup into `Main.scala` (run migrations before binding the HTTP server)
- [x] 1.4 Add a `Database` config value (SQLite file path) to `AppConfig.scala` and `application.conf`, with a sensible local default (e.g. `./data/dionysus.db`)

## 2. Ingredient catalog

- [x] 2.1 `core`: define `Ingredient` and `Nutrition` types — `sodiumMg: Double` required (not `Option`), `caloriesKcal`/`proteinG`/`carbsG`/`fatG` required, `abvPercent: Option[Double]`, `directlyLoggable: Boolean`
- [x] 2.2 `core`: unit tests for `Ingredient`/`Nutrition` construction — reject negative sodium/macros at the type/smart-constructor level
- [x] 2.3 `server`: Slick table mapping + repository for `Ingredient` (create, get by ID, list, update, delete)
- [x] 2.4 `server`: `IngredientRoutes` — `POST /api/ingredients`, `GET /api/ingredients`, `GET /api/ingredients/{id}`, `PUT /api/ingredients/{id}`, `DELETE /api/ingredients/{id}`
- [x] 2.5 `server`: route tests (pekko-http-testkit, matching `HelloRoutesSpec` style) — missing sodium rejected, valid ingredient round-trips, `directlyLoggable` defaults to false

## 3. Pantry stock

- [x] 3.1 `core`: pure function computing a pantry decrement amount from a recipe line, `servingsMade`, and the recipe's `servings`
- [x] 3.2 `core`: unit tests for the decrement calculation, including the proportional-scaling scenario from the spec
- [x] 3.3 `server`: Slick repository for `pantry_stock` (get on-hand quantity by ingredient ID, adjust by delta — delta may be negative, no floor at zero)
- [x] 3.4 `server`: `PantryRoutes` — `GET /api/ingredients/{id}/stock`, `POST /api/ingredients/{id}/stock/adjust`
- [x] 3.5 `server`: route tests — manual restock, and (via batch creation in section 5) decrement going negative without rejection

## 4. Recipe authoring

- [x] 4.1 `core`: define `Recipe` and `RecipeLine` types (`ingredientId`, `quantity`, `unit`); `servings: Int` must be >= 1
- [x] 4.2 `core`: pure function computing a recipe's per-serving nutrition from its lines' ingredient nutrition
- [x] 4.3 `core`: unit tests — per-serving nutrition math (including the sodium-scaling scenario from the spec), rejects zero lines, rejects non-positive servings
- [x] 4.4 `server`: Slick tables + repository for `Recipe`/`RecipeLine` (create, get with lines, list, delete); creation validates every line's `ingredientId` exists
- [x] 4.5 `server`: `RecipeRoutes` — `POST /api/recipes`, `GET /api/recipes`, `GET /api/recipes/{id}` (includes computed per-serving nutrition)
- [x] 4.6 `server`: route tests — unknown ingredient ID rejected, valid recipe round-trips with correct per-serving nutrition

## 5. Batch tracking

- [x] 5.1 `core`: define `Batch` type (`recipeId`, `cookedAt`, `servingsMade`); `servingsMade` must be positive
- [x] 5.2 `core`: pure function computing remaining portions (`servingsMade - sum(loggedPortions)`) given a batch and its logged portion amounts
- [x] 5.3 `core`: unit tests — remaining-portions math across multiple meals/days (the leftovers scenario from the spec)
- [x] 5.4 `server`: Slick table + repository for `Batch` (create — validates recipe exists, decrements pantry stock per section 3; get with computed remaining portions; delete — rejects if any meal line references it)
- [x] 5.5 `server`: `BatchRoutes` — `POST /api/batches`, `GET /api/batches/{id}` (includes computed remaining portions), `DELETE /api/batches/{id}`; no update endpoint (batches are immutable)
- [x] 5.6 `server`: route tests — unknown recipe rejected, non-positive servingsMade rejected, pantry decrements on creation, delete blocked when meals reference the batch

## 6. Meal logging

- [x] 6.1 `core`: define `Meal`, and the `MealLine` sum type (`BatchPortionLine(batchId, portions)` | `DirectConsumableLine(ingredientId, quantity, unit)`)
- [x] 6.2 `core`: pure function computing a meal line's nutrition contribution (batch-portion: recipe per-serving × portions; direct-consumable: ingredient nutrition × quantity) and a meal's total as the sum of its lines
- [x] 6.3 `core`: unit tests — mixed-line meal totals, sodium always present (defaults to 0 with zero lines is N/A here since a meal requires >=1 line, but assert the field is never optional in the type)
- [x] 6.4 `server`: Slick tables + repository for `Meal`/`MealLine` (create — validates: batch-portion lines don't exceed the batch's remaining portions at creation time; direct-consumable lines reference a `directlyLoggable` ingredient; get, list by date range, delete)
- [x] 6.5 `server`: `MealRoutes` — `POST /api/meals`, `GET /api/meals/{id}`, `DELETE /api/meals/{id}`
- [x] 6.6 `server`: route tests — over-portioning a batch rejected, exact-remaining portion accepted, non-directly-loggable ingredient rejected, mixed batch-portion + direct-consumable meal round-trips correctly

## 7. Nutrition rollup

- [x] 7.1 `core`: pure function computing a day's total nutrition and per-meal breakdown from a list of meals (sodium always present, defaults to 0)
- [x] 7.2 `core`: unit tests — multi-meal day totals, date-boundary exclusion (a meal on an adjacent date is not included)
- [x] 7.3 `server`: repository query fetching all meals (with their lines resolved) whose `eatenAt` falls on a given calendar date
- [x] 7.4 `server`: `LogRoutes` — `GET /api/log/{date}` returning day totals + the list of contributing meals with their own timestamps and totals
- [x] 7.5 `server`: route tests — multi-meal day sums correctly, meals on other dates excluded, empty day returns zeroed totals (not a missing/null sodium field)

## 8. Wiring and verification

- [x] 8.1 Compose all new routes (`IngredientRoutes`, `PantryRoutes`, `RecipeRoutes`, `BatchRoutes`, `MealRoutes`, `LogRoutes`) into `HttpServer.scala` alongside the existing `HealthRoutes`/`HelloRoutes`
- [x] 8.2 Update `README.md` with the new API surface (endpoint list) and the SQLite data-file location/config
- [x] 8.3 Run `sbt -batch scalafmtAll compile Test/compile test` and confirm a fully green suite before opening the PR
- [x] 8.4 Manual smoke test: start the server locally, walk through create-ingredient → create-recipe → create-batch → log-meal → `GET /api/log/{date}` end to end with `curl`, confirm sodium appears correctly at every step
