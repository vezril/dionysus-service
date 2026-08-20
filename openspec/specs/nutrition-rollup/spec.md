# nutrition-rollup

## Requirements

### Requirement: A meal's nutrition total is the sum of its lines
The system SHALL compute a meal's total nutrition (calories, protein, carbs, fat, and sodium) as the sum of every line's nutrition contribution, and SHALL include this total when a meal is read. (Alcohol aggregation is deferred: `abvPercent` is stored on ingredients but not rolled up — converting a percentage into a consumed amount requires the unit system that is out of scope this phase.)

#### Scenario: Meal total reflects both line types
- **WHEN** a meal has a batch-portion line contributing 200mg sodium and a direct-consumable line contributing 50mg sodium
- **THEN** the meal's total sodium is reported as 250mg

### Requirement: Sodium is always present in a rollup, never null or omitted
The system SHALL always include a sodium total (defaulting to `0` when no lines contribute any) in every recipe, meal, and day nutrition rollup — sodium MUST NOT be an optional or absent field in any rollup response.

#### Scenario: Rollup with no sodium contribution still reports zero
- **WHEN** a day has zero meals logged
- **THEN** the day's rollup reports `sodiumMg: 0`, not a missing or null field

### Requirement: A day's nutrition is the sum of every meal eaten that day
The system SHALL compute a day's total nutrition as the sum of every meal whose `eatenAt` falls on that calendar date in the configured timezone (`dionysus.timezone` / `DIONYSUS_TZ`, default UTC), exposed via `GET /api/log/{date}`. Day boundaries MUST follow the configured zone — a naive UTC day splits the user's real day (e.g. it rolls over at 8pm in Montreal).

#### Scenario: Day boundaries follow the configured timezone
- **WHEN** the timezone is `America/Toronto` and a meal's `eatenAt` is `2026-08-20T01:00:00Z` (9pm on Aug 19 in Toronto)
- **THEN** `GET /api/log/2026-08-19` includes that meal and `GET /api/log/2026-08-20` does not

#### Scenario: Day rollup sums multiple meals
- **WHEN** two meals are logged on the same date, contributing 1200mg and 800mg of sodium respectively
- **THEN** `GET /api/log/{date}` for that date reports `sodiumMg: 2000`

#### Scenario: Day rollup excludes meals from other dates
- **WHEN** a meal is logged on 2026-08-18 and another on 2026-08-19
- **THEN** `GET /api/log/2026-08-19` includes only the second meal's contribution

### Requirement: The day rollup lists the contributing meals
The system SHALL include, alongside the day's totals, a list of that day's meals (each with its own `eatenAt` timestamp and its own nutrition total) in the `GET /api/log/{date}` response — this is the shape a future HealthKit-bridge Shortcut is expected to read.

#### Scenario: Day rollup includes per-meal detail
- **WHEN** a client requests `GET /api/log/{date}` for a date with two logged meals
- **THEN** the response includes the day's totals and a list of exactly those two meals with their individual timestamps and nutrition totals

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

### Requirement: Day rollups are queryable as a range
`GET /api/log/range?from=&to=` SHALL return, in one response, a per-day
entry for every day in the inclusive range that has meals — date, total
nutrition (micronutrients included), and meal count — bucketed by the
configured timezone identically to the single-day endpoint. Invalid
dates, from after to, or a range beyond 400 days SHALL yield 400.

#### Scenario: A week in one call
- **WHEN** meals exist on two days of a requested 7-day range
- **THEN** the response holds exactly those two day entries with their totals and counts

#### Scenario: Guardrails
- **WHEN** from is after to, or the range exceeds 400 days
- **THEN** the service responds 400
