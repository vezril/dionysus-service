# nutrition-rollup

## Requirements

### Requirement: A meal's nutrition total is the sum of its lines
The system SHALL compute a meal's total nutrition (calories, protein, carbs, fat, sodium, and alcohol where applicable) as the sum of every line's nutrition contribution, and SHALL include this total when a meal is read.

#### Scenario: Meal total reflects both line types
- **WHEN** a meal has a batch-portion line contributing 200mg sodium and a direct-consumable line contributing 50mg sodium
- **THEN** the meal's total sodium is reported as 250mg

### Requirement: Sodium is always present in a rollup, never null or omitted
The system SHALL always include a sodium total (defaulting to `0` when no lines contribute any) in every recipe, meal, and day nutrition rollup — sodium MUST NOT be an optional or absent field in any rollup response.

#### Scenario: Rollup with no sodium contribution still reports zero
- **WHEN** a day has zero meals logged
- **THEN** the day's rollup reports `sodiumMg: 0`, not a missing or null field

### Requirement: A day's nutrition is the sum of every meal eaten that day
The system SHALL compute a day's total nutrition as the sum of every meal whose `eatenAt` falls on that calendar date, exposed via `GET /api/log/{date}`.

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
