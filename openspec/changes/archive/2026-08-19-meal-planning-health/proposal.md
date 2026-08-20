## Why

Calvin has high blood pressure and wants what and when he eats captured as real data — eventually exported to Apple HealthKit and correlated against his BP readings. `dionysus-service` is starting fresh (Scala 3 + Pekko, API-only, no dashboard for now) specifically to serve this use case: a durable, queryable eating log that a phone-side bridge can pull from and push into, without the service ever calling out itself.

Today the service has no domain at all beyond the scaffold's `Greeting`/`Hello` sample. This change establishes the core eating-and-cooking domain: an ingredient/nutrition catalog with sodium as a required field (not optional — sodium is the make-or-break nutrient for hypertension tracking), recipes as templates, batches as discrete cook events with leftovers-aware portion tracking, and meals as timestamped log entries that can reference either a batch portion or a directly-logged consumable (a bar, a beer, a glass of wine).

## What Changes

- New ingredient/nutrition catalog: ingredients carry calories, protein, carbs, fat, and **sodium as a required (non-nullable) field** — no ingredient can be saved without it. Alcohol content (ABV or standard-drink equivalent) is an optional field on ingredients, for consumables where it applies.
- Ingredients can be flagged `directlyLoggable` — eligible to appear in a meal log line on their own, with no recipe/batch involved (e.g. a protein bar, a beer).
- New recipe capability: a recipe is a template — name, servings, and ingredient lines (ingredient + quantity + unit), matched by ingredient ID only, never by name.
- New pantry-stock capability: ingredients have an on-hand quantity, decremented when a batch is cooked. This is the minimal pantry model needed to support batches — no "what can I cook" matching in this phase.
- New batch capability (cook event): recording a batch captures `recipeId`, `cookedAt`, and `servingsMade`; it decrements pantry stock by the recipe's ingredient lines × servings. **Portions remaining is derived** (`servingsMade` − sum of portions already logged against this batch from meals), never stored as a mutable counter — this is what makes leftovers correct across multiple days.
- New meal-logging capability (eating event): a meal is `eatenAt` + one or more lines. Each line is **either** a batch-portion reference (`batchId` + `portions`, nutrition = recipe's per-serving nutrition × portions) **or** a direct-consumable reference (`ingredientId` + `quantity` + `unit`, ingredient must be `directlyLoggable`). A single meal can mix both kinds of lines.
- New nutrition-rollup capability: sodium and the other core macros/calories are aggregated and exposed per-recipe (per serving), per-meal, and per-day. The per-day rollup is shaped as a clean daily summary (a later phase's HealthKit-bridge Shortcut will `GET` a day's totals) — this phase builds the rollup and its endpoint, not the bridge itself.
- All of the above is exposed as an HTTP API only — **BREAKING** in the sense that there is no UI in this phase; the service is API-first by design, consistent with dropping the dashboard.
- Standing invariant carried forward, not new: the service makes no outbound network calls — it is pulled from and pushed to, never the initiator.

## Capabilities

### New Capabilities
- `ingredient-catalog`: ingredient CRUD and nutrition data, sodium required, alcohol content optional, `directlyLoggable` flag.
- `pantry-stock`: per-ingredient on-hand quantity, decremented by batches.
- `recipe-authoring`: recipe templates (name, servings, ID-matched ingredient lines).
- `batch-tracking`: cook events; pantry decrement on creation; derived remaining-portions calculation.
- `meal-logging`: eating events with batch-portion and/or direct-consumable lines.
- `nutrition-rollup`: sodium/macro/calorie aggregation at recipe, meal, and day granularity, including the per-day summary endpoint shape future phases will read from.

### Modified Capabilities
(none — fresh service, nothing existing to modify)

## Impact

- Affects: `core` and `server` Scala modules (new domain types in `core`, new HTTP routes in `server`, alongside the existing `Greeting`/`Hello` scaffold).
- New: a persistence layer (not yet chosen — design.md will decide, e.g. Slick/JDBC vs. an in-memory store for this phase) for ingredients, pantry stock, recipes, batches, and meals.
- No UI/dashboard work in this phase or planned for this service.
- No outbound integrations in this phase (HealthKit bridge, `/health` correlation view, shopping lists are explicitly out of scope — noted as anticipated future work, not designed here).
