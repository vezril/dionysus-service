# recipe-authoring

## Requirements

### Requirement: A recipe is a named template with servings and ingredient lines
The system SHALL allow creating a recipe with a name, an integer `servings` count (>= 1), and one or more ingredient lines, each specifying an ingredient ID, a quantity, and a unit.

#### Scenario: Creating a valid recipe
- **WHEN** a client submits a recipe named "Chicken and Rice" with `servings: 4` and two ingredient lines
- **THEN** the system creates the recipe and returns its assigned ID

#### Scenario: A recipe requires at least one ingredient line
- **WHEN** a client submits a recipe with zero ingredient lines
- **THEN** the system rejects the request with a validation error

#### Scenario: Servings must be a positive integer
- **WHEN** a client submits a recipe with `servings: 0` or a non-integer value
- **THEN** the system rejects the request with a validation error

### Requirement: Recipe lines reference ingredients by ID only
The system SHALL resolve every recipe line's ingredient by its system-assigned ID. The system MUST NOT match ingredients by name when authoring or reading a recipe.

#### Scenario: Recipe line with an unknown ingredient ID is rejected
- **WHEN** a client submits a recipe line referencing an ingredient ID that does not exist
- **THEN** the system rejects the request with a validation error

#### Scenario: Recipe line quantity must be positive
- **WHEN** a client submits a recipe line with a zero or negative quantity
- **THEN** the system rejects the request with a validation error

### Requirement: A recipe's per-serving nutrition is derived from its lines
The system SHALL compute a recipe's per-serving nutrition (calories, protein, carbs, fat, and sodium) as the sum of each line's ingredient nutrition scaled by that line's quantity, divided by the recipe's `servings`.

#### Scenario: Per-serving sodium reflects all lines
- **WHEN** a recipe with `servings: 4` has two lines whose combined sodium contribution is 800mg
- **THEN** the recipe's per-serving sodium is reported as 200mg
