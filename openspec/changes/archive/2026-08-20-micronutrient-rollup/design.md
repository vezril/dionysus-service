# Design: micronutrient-rollup

## D1 — The map lives on `Nutrition`, not beside it

Everything that aggregates nutrition (RecipeNutrition, MealNutrition,
DayRollup) composes `Nutrition.+`/`scale` and resolves ingredients
through `IngredientRepository` — putting `micronutrients: Map[String,
Double]` on `Nutrition` means zero changes to any math module. Smart
constructor rejects blank keys and negative amounts; `zero` carries the
empty map; `+` is `merged(a, b) = a.keySet ∪ b.keySet, summed`.

Partial-coverage semantics are deliberate: summing what's present makes
a day total an "at least" figure. The all-or-incomplete rule the macros
use (planner-side) would render sparse micros permanently unknown.

## D2 — Keys are free-form here

The planner owns the 16-nutrient registry and its label units; the
service (no outbound calls, no shared package) validates only shape:
non-blank key, amount ≥ 0. A key the planner later renames simply rolls
up under both names — acceptable for a single-writer system.

## D3 — Storage and back-compat

`ingredient_micronutrient(ingredient_id REFERENCES ingredient(id),
nutrient_key TEXT, amount REAL, PK(ingredient_id, nutrient_key))` —
Flyway V2. Repository: create inserts rows in the same transaction;
update replace-sets them; get/list join-and-group; delete removes them
with the pantry row in the existing transaction.

Wire: `IngredientJson.micronutrients` is read with
missing-or-null → empty (the hand-written format already does this for
`directlyLoggable`); `NutritionJson.micronutrients` is always written
(possibly `{}`), so planner types stay additive.
