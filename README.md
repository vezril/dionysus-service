# dionysus-service

A pure API service for meal planning and eating-log tracking — Recipe → Batch (cook event) →
Meal (eating event), with sodium as a first-class, required nutrient. No dashboard/UI by design;
this is the backend a future phone-side HealthKit bridge will read from and write to. See
`openspec/changes/archive/*/proposal.md` for the "why", and `openspec/specs/` for the current
behavior contract.

## Status

Core eating-and-cooking domain implemented (openspec: meal-planning-health) — ingredient
catalog, pantry stock, recipes, batches, meals, and the per-day nutrition rollup. No auth, no
HealthKit bridge yet (future phases).

## Getting started

- **Layout**: `core/` (pure domain, no Pekko — types + math for ingredients, recipes, batches,
  meals, day rollups) and `server/` (Pekko HTTP routes, Slick/SQLite persistence, Docker).
- **Data**: SQLite, file path from `dionysus.database.sqlite-path` (default `/data/dionysus.db` in
  the Docker image — `/data` is a declared `VOLUME`, writable by the non-root runtime user;
  override via `DIONYSUS_DB_PATH`). Schema is Flyway-migrated on startup from
  `server/src/main/resources/db/migration/`. Single-writer, single-instance by design — this is a
  personal app, not a multi-tenant service.
- **Endpoints**:
  - `GET /` → hello; `GET /health` → status/service/version JSON (503 during shutdown)
  - `POST/GET /api/ingredients`, `GET/PUT/DELETE /api/ingredients/{id}` — the nutrition catalog
    (sodium required on every ingredient; `directlyLoggable` flags one eligible for a meal line
    on its own, e.g. a beer or a protein bar)
  - `GET /api/ingredients/{id}/stock`, `POST /api/ingredients/{id}/stock/adjust` — pantry on-hand
    quantity (may go negative; not a hard inventory constraint)
  - `POST/GET /api/recipes`, `GET /api/recipes/{id}` — recipe templates, with computed
    per-serving nutrition
  - `POST /api/batches`, `GET/DELETE /api/batches/{id}` — cook events; immutable once created;
    decrements pantry stock; remaining portions is always computed, never stored
  - `POST /api/meals`, `GET/DELETE /api/meals/{id}` — eating events; each line is either a
    portion of a batch or a directly-logged consumable
  - `GET /api/log/{date}` (`YYYY-MM-DD`) — a day's total nutrition plus the meals that
    contributed to it; the shape a future HealthKit-bridge Shortcut is expected to read
- **Develop**: `sbt compile`, `sbt test`, `sbt server/run` (port 8080, `HTTP_PORT` overrides),
  `sbt scalafmtAll`.
- **Docker**: `sbt server/Docker/publishLocal`, then `docker run -p 8080:8080 calvinference/dionysus:<version>`.
- **CI/CD**: ci.yml on PRs; dev.yml publishes `:dev` images from `development`; release.yml
  publishes `:X.Y.Z` + `:latest` from a `vX.Y.Z` tag; versioning is git-tag-driven
  (sbt-dynver); image publishing needs the `DOCKERHUB_*` secrets (skipped gracefully if absent).

## License

MIT — see [LICENSE.md](LICENSE.md).
