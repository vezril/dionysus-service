## Context

`dionysus-service` is a brand-new Scala 3 + Pekko service (scaffolded from `claude-toolkit:new-scala-pekko-service`, PR #1 merged). It currently has only the scaffold sample (`Greeting`, `/hello`, `/health`) split across `core` (pure domain, zero Pekko deps) and `server` (Pekko HTTP + `Main`). No persistence layer, no domain model, no auth exists yet.

This is a personal, single-user health-tracking service — the direct successor in spirit to `dionysus-planner` (a Next.js app that tracked pantry/recipes/nutrition), but rebuilt API-first with a sharper focus: capture what Calvin eats and when, well enough that a later phone-side bridge can correlate it with blood pressure via HealthKit. That bridge, and everything past the eating log itself, is explicitly out of scope here (see proposal.md).

`dionysus-planner`'s domain layer had one invariant worth carrying forward even into a fresh codebase: **ingredient matching is by ID, never by name** (its FR-24). The same reasoning applies here — a recipe line or meal line references an `ingredientId`, full stop.

## Goals / Non-Goals

**Goals:**
- A `core`/`server` split for the new domain, matching the existing scaffold's layering: `core` holds pure types and pure logic (no Pekko, no DB, unit-testable in isolation); `server` holds Pekko HTTP routes and persistence.
- Sodium modeled as a required field at the type level (not `Option[Double]`) — a nutrition value literally cannot be constructed without it.
- A persistence choice that keeps this a simple, single-file, single-instance deployment — this is a personal app for one household, not a multi-tenant service.
- An HTTP API surface for ingredients, recipes, batches, and meals, plus a per-day nutrition summary endpoint shaped for the future HealthKit-bridge Shortcut to read.

**Non-Goals:**
- No UI/dashboard (per proposal.md — API-only for this service, permanently, not just for this phase).
- No auth in this phase — the service is assumed to run on a private/home network, reachable only by Calvin's own tooling. Exposing it beyond that (which the future HealthKit bridge will require, since it runs from a phone) is an open question for that later phase, not this one.
- No "what can I cook" matching/cookability logic (`dionysus-planner`'s FR-17–22) — pantry stock exists here only to support batch cooking (decrementing on cook), not for recipe recommendation.
- No data migration from `dionysus-planner` — that app's SQLite data is not imported. This is a fresh domain, fresh data.

## Decisions

**1. Persistence: Slick 3 + SQLite (via the `xerial` JDBC driver) + Flyway for schema migrations.**
Alternatives considered:
- *doobie/cats-effect* — more idiomatic in FP-heavy Scala shops, but it introduces a second effect system (`IO`) alongside Pekko's `Future`-based idioms already used by the scaffold's routes (`pekko-http` + `Future`). Slick's `Future`-based API composes directly with existing route code with no new paradigm to learn or bridge.
- *Postgres* — better multi-writer story, but this is a single-user personal app; `dionysus-planner` deliberately chose SQLite for the same reason (and even shaped its Helm chart around single-replica deployment to match). Postgres would add an operational dependency (a second container/service to run and back up) with no corresponding benefit here.
- *Plain JDBC, no Slick* — viable given the domain is small, but Slick's typesafe query DSL and Flyway's migration story are cheap to adopt now and pay off as the schema grows across the six new capabilities.

**2. `core` holds the domain types and pure logic; `server` holds Slick table mappings, repositories, and Pekko HTTP routes.**
This mirrors `dionysus-planner`'s domain/data boundary (a pure `domain/*.ts` layer with ESLint-enforced import restrictions keeping Drizzle out of it). Here the boundary is structural (module dependency: `core` has zero DB/Pekko library dependencies in `build.sbt`) rather than lint-enforced, which is stronger. Pure logic that belongs in `core`:
- Batch portions-remaining: `servingsMade - sum(portionsLoggedAgainstThisBatch)`.
- Nutrition rollup math: summing sodium/calories/macros across meal lines (batch-portion lines scaled by the recipe's per-serving nutrition × portions; direct-consumable lines taken from the ingredient's nutrition × quantity).

**3. Sodium is `Double` (required), not `Option[Double]`, on the nutrition value object. Alcohol content (`abvPercent: Option[Double]`) is optional, on `Ingredient`.**
This is a direct type-level enforcement of the proposal's "sodium is first-class" requirement — a malformed/incomplete ingredient simply does not compile/construct, rather than being a validation rule that could be bypassed.

**4. Batch cooking decrements pantry stock synchronously at batch-creation time; portions-remaining is always computed, never stored.**
Storing a mutable "portions remaining" counter would require every meal-log write to also touch the batch row (a second write, a race, a place to drift out of sync). Computing it on read (`servingsMade` minus a `SUM` over meal lines referencing that batch) keeps `Batch` immutable after creation and makes the derivation testable as a pure function in `core`.

**5. JSON marshalling: continue with `pekko-http-spray-json`** (already a `server` dependency from the scaffold) rather than introducing `pekko-http-circe`. No functional gap for this phase's needs; avoids a new dependency for a purely stylistic gain.

**6. API shape:** `/api/ingredients`, `/api/recipes`, `/api/batches`, `/api/meals` for CRUD/creation, plus `/api/log/{date}` returning that day's nutrition rollup (calories/protein/carbs/fat/sodium/alcohol totals + the meals that contributed) — the shape the future HealthKit-bridge Shortcut will `GET`. Building the endpoint now, even though nothing consumes it yet, keeps this phase's rollup logic honest (it has to actually answer "what did I eat today" cleanly).

## Risks / Trade-offs

- **[Risk] SQLite is single-writer.** → **Mitigation:** matches `dionysus-planner`'s precedent for the same personal-use case; repositories are defined behind Scala traits in `core`/`server` boundary terms (a `server`-side trait per aggregate), so a future swap to Postgres would be contained to `server`'s Slick implementations, not a domain rewrite.
- **[Risk] No auth in this phase.** → **Mitigation:** explicitly scoped as a non-goal; the service must not be deployed reachable from the open internet until the health-bridge phase adds auth. Flagged as an open question below so it isn't forgotten.
- **[Risk] Computing portions-remaining and nutrition rollups on every read adds query cost.** → **Mitigation:** acceptable at personal-app scale (a household logs at most a handful of meals/batches a day); revisit with an indexed/materialized view only if it's ever actually slow.
- **[Risk] Real nutrition data (especially sodium) has to come from somewhere.** → **Mitigation:** ingredients are entered manually via the API in this phase (same as `dionysus-planner`'s custom-ingredient flow); bulk-importing a reference dataset (e.g. the Canadian Nutrient File, as Codex's design note mentions) is left as an open question, not blocking this phase.

## Migration Plan

Greenfield — no existing data to migrate. "Migration" here means schema evolution: Flyway versioned SQL migrations (`server/src/main/resources/db/migration/V1__init.sql`, etc.) define and evolve the schema from an empty SQLite file. No rollback strategy beyond "don't ship a broken migration" is needed at this stage (single environment, no production traffic yet).

## Open Questions

- **Nutrition data sourcing:** manual entry only for this phase, or worth scripting a one-time bulk import from a reference dataset (e.g. Canadian Nutrient File, per Codex's note) to reduce the up-front data-entry burden? Left for a follow-up change if manual entry proves too slow in practice.
- **Auth/exposure boundary for the future HealthKit bridge:** the bridge (a phone Shortcut) will need to reach this service from outside "just running on my machine." What that requires (a token, network-level restriction, something else) is a decision for that later phase's own design, not this one — noted here so it isn't lost.
