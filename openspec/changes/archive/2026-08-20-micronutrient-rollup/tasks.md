## 1. Core

- [x] 1.1 `Nutrition` += `micronutrients: Map[String, Double]` (default empty): key-wise `+`, value-wise `scale`, smart-constructor validation (non-blank keys, amounts ≥ 0), `zero`
- [x] 1.2 Core tests: merge, scale, validation, and one RecipeNutrition/MealNutrition pass-through each proving no math module needed changes

## 2. Server

- [x] 2.1 Flyway `V2__ingredient_micronutrient.sql`
- [x] 2.2 `IngredientRepository`: transactional create/update replace-set, get/list join-and-group, delete cleanup
- [x] 2.3 JSON: `IngredientJson.micronutrients` (missing/null → empty, invalid → 400), `NutritionJson.micronutrients` always written
- [x] 2.4 Server tests: route round-trip incl. omitted field, replace-set on PUT, day-log rollup with a supplement, batch-portion scaling

## 3. Verification

- [x] 3.1 `sbt scalafmtAll compile test` green
- [x] 3.2 Ship: PR to development → :dev image → verify against a live container → release tag + helm upgrade on the homelab
