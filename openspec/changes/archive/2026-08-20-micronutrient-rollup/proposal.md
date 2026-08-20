# Proposal: micronutrient-rollup

## Why

dionysus-planner v2.9.0 tracks per-ingredient micronutrients (vitamins,
minerals) but the service — the system of record for what was actually
EATEN — knows only the five macro fields. Day logs can't answer "did I
take my vitamin D today" until micronutrients flow through
Recipe → Batch → Meal → day rollup.

## What Changes

1. **`Nutrition` carries a sparse micronutrient map** (`Map[String,
   Double]`, key → amount, empty = none recorded). `+` merges key-wise
   (partial coverage sums what's present — the honest "at least"
   semantics), `scale` multiplies values. Keys are free-form non-blank
   strings: the planner owns the registry; the service stays permissive
   (no outbound calls, no shared constant).
2. **Storage:** `ingredient_micronutrient(ingredient_id, nutrient_key,
   amount)` (Flyway V2). `IngredientRepository` reads/writes it with the
   ingredient; delete cleans it in the same transaction.
3. **Wire:** `NutritionJson` (responses: recipe perServing, meal totals,
   day rollup) gains an always-present `micronutrients` object.
   `IngredientJson` (request + response) gains an optional
   `micronutrients` object defaulting to empty — existing clients that
   omit it keep working unchanged.
4. Recipe/meal/day math changes NOWHERE else — they already compose
   through `Nutrition.+`/`scale` and resolve via `IngredientRepository`.

## Impact

- core: Nutrition + tests. server: migration, ingredient table/repo,
  two JSON shapes, route tests. Planner consumes this in its own
  follow-up change (mirroring micros in the cook flow, day-log display).
