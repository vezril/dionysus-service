# ingredient-catalog

## Requirements

### Requirement: Sodium is required on every ingredient
The system SHALL require a non-negative sodium value (milligrams) on every ingredient's nutrition data. An ingredient MUST NOT be creatable or updatable without a sodium value.

#### Scenario: Creating an ingredient without sodium is rejected
- **WHEN** a client submits a new ingredient with no `sodiumMg` field (or a negative value)
- **THEN** the system rejects the request with a validation error and does not create the ingredient

#### Scenario: Creating an ingredient with sodium succeeds
- **WHEN** a client submits a new ingredient including `sodiumMg: 0` or any non-negative value
- **THEN** the system creates the ingredient and returns its assigned ID

### Requirement: Core macro nutrition is required
The system SHALL require non-negative values for calories, protein, carbohydrates, and fat on every ingredient, in addition to sodium.

#### Scenario: Missing a required macro field is rejected
- **WHEN** a client submits a new ingredient missing `caloriesKcal`, `proteinG`, `carbsG`, or `fatG`
- **THEN** the system rejects the request with a validation error identifying the missing field

### Requirement: Alcohol content is optional
The system SHALL allow an ingredient to optionally carry an alcohol-by-volume percentage (`abvPercent`), for ingredients where it applies (e.g. beer, wine).

#### Scenario: Ingredient with no alcohol content
- **WHEN** a client creates an ingredient without `abvPercent`
- **THEN** the system creates the ingredient with `abvPercent` absent, and existing behavior is unaffected

#### Scenario: Ingredient with alcohol content
- **WHEN** a client creates an ingredient with `abvPercent: 5.0`
- **THEN** the system stores and returns that value on the ingredient

### Requirement: Ingredients can be flagged as directly loggable
The system SHALL allow an ingredient to be flagged `directlyLoggable`, marking it eligible to appear in a meal-log line on its own (no recipe or batch involved).

#### Scenario: Flagging an ingredient directly loggable
- **WHEN** a client creates or updates an ingredient with `directlyLoggable: true`
- **THEN** the ingredient may later be referenced directly in a meal-log line (see meal-logging capability)

#### Scenario: Default is not directly loggable
- **WHEN** a client creates an ingredient without specifying `directlyLoggable`
- **THEN** the system defaults it to `false`

### Requirement: Ingredients are identified and referenced by ID only
The system SHALL assign each ingredient a unique system-generated ID at creation, and every reference to an ingredient elsewhere in the system (recipe lines, meal lines) SHALL be by that ID, never by name.

#### Scenario: Two ingredients with the same name are distinct
- **WHEN** a client creates two ingredients both named "Onion" with different nutrition data
- **THEN** the system creates two distinct ingredients with distinct IDs, and each is referenced independently
