## ADDED Requirements

### Requirement: Every ingredient has an on-hand pantry quantity
The system SHALL track an on-hand quantity for every ingredient, defaulting to zero when the ingredient is created.

#### Scenario: New ingredient starts with zero stock
- **WHEN** a new ingredient is created
- **THEN** its on-hand pantry quantity is `0` until explicitly adjusted

### Requirement: Pantry quantity can be manually adjusted
The system SHALL allow a client to set or adjust an ingredient's on-hand pantry quantity directly (e.g. after grocery shopping), independent of cooking a batch.

#### Scenario: Manually restocking an ingredient
- **WHEN** a client submits a pantry adjustment adding `500` (grams) to an ingredient's on-hand quantity
- **THEN** the system increases that ingredient's on-hand quantity by 500

### Requirement: Cooking a batch decrements pantry stock
The system SHALL decrement each recipe line's ingredient on-hand quantity by that line's quantity multiplied by the batch's `servingsMade` divided by the recipe's `servings`, at the moment a batch is recorded.

#### Scenario: Batch decrements every ingredient it uses
- **WHEN** a batch is recorded for a recipe with servings=4 whose lines include 200g of an ingredient, with `servingsMade: 4`
- **THEN** that ingredient's on-hand pantry quantity decreases by 200g

#### Scenario: Batch scaled to fewer servings decrements proportionally
- **WHEN** a batch is recorded for the same recipe (servings=4, 200g line) with `servingsMade: 2`
- **THEN** that ingredient's on-hand pantry quantity decreases by 100g

### Requirement: Pantry stock may go negative
The system SHALL permit an ingredient's on-hand quantity to go negative as a result of cooking a batch, rather than rejecting the batch — pantry tracking is informational, not a hard inventory constraint.

#### Scenario: Cooking with insufficient recorded stock still succeeds
- **WHEN** a batch is recorded that requires more of an ingredient than its current on-hand quantity
- **THEN** the batch is created successfully and the ingredient's on-hand quantity becomes negative
