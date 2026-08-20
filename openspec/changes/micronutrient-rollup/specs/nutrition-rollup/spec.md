## ADDED Requirements

### Requirement: Micronutrients roll up through recipes, meals, and days
Every computed nutrition value (recipe per-serving, meal total, day-log
total) SHALL include a `micronutrients` object aggregated key-wise from
the constituent ingredients: sums scale with line quantities and batch
portions exactly as the macro fields do, and keys absent from an
ingredient simply contribute nothing (an "at least" total — never an
error, never a forced zero row).

#### Scenario: Supplement in a day log
- **WHEN** a directly-loggable ingredient with `{"vitaminD": 25}` is eaten once today
- **THEN** GET /api/log/{today} totals include `"vitaminD": 25`

#### Scenario: Batch portion scales micronutrients
- **WHEN** a recipe whose lines sum to `{"vitaminC": 60}` across 2 servings is cooked and 1 portion is eaten
- **THEN** the meal's total includes `"vitaminC": 30`

#### Scenario: Mixed coverage sums what's present
- **WHEN** a meal combines one ingredient with `{"iron": 2}` and one with no micronutrients
- **THEN** the total includes `"iron": 2` and no other keys
