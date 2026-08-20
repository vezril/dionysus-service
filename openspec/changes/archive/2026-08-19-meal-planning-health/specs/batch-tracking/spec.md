## ADDED Requirements

### Requirement: A batch records a single cook event
The system SHALL allow creating a batch specifying a `recipeId`, a `cookedAt` timestamp, and a `servingsMade` value (positive number). Creating a batch decrements pantry stock per the pantry-stock capability.

#### Scenario: Creating a valid batch
- **WHEN** a client submits a batch for an existing recipe with `servingsMade: 4` and a `cookedAt` timestamp
- **THEN** the system creates the batch, returns its assigned ID, and decrements pantry stock for the recipe's ingredients

#### Scenario: Batch requires a positive servingsMade
- **WHEN** a client submits a batch with `servingsMade: 0` or negative
- **THEN** the system rejects the request with a validation error

#### Scenario: Batch requires an existing recipe
- **WHEN** a client submits a batch referencing a `recipeId` that does not exist
- **THEN** the system rejects the request with a validation error

### Requirement: A batch is immutable once created
The system SHALL NOT allow `recipeId`, `cookedAt`, or `servingsMade` to be edited after a batch is created. A mistaken batch is corrected by deletion, not modification.

#### Scenario: Batch fields cannot be edited
- **WHEN** a client attempts to update an existing batch's `servingsMade`
- **THEN** the system rejects the request (no update endpoint for batch fields exists)

### Requirement: Portions remaining on a batch is always derived, never stored
The system SHALL compute a batch's remaining portions as `servingsMade` minus the sum of portions logged against that batch across all meal-log lines, computed at read time. The system MUST NOT persist a mutable "portions remaining" counter.

#### Scenario: Remaining portions decreases as meals log against the batch
- **WHEN** a batch has `servingsMade: 4` and a meal logs a batch-portion line of 1 portion against it
- **THEN** the batch's computed remaining portions is 3

#### Scenario: Remaining portions reflects multiple meals over multiple days
- **WHEN** a batch has `servingsMade: 4`, one meal logs 1 portion on day 1, and another meal logs 2 portions on day 3
- **THEN** the batch's computed remaining portions is 1, queryable at any time regardless of which day is "today"

### Requirement: Deleting a batch requires no dependent meal lines
The system SHALL reject deletion of a batch that has one or more meal-log lines referencing it, to avoid orphaning logged nutrition data.

#### Scenario: Deleting a batch with no logged meals
- **WHEN** a client deletes a batch that no meal has ever logged a portion against
- **THEN** the system deletes the batch

#### Scenario: Deleting a batch with logged meals is rejected
- **WHEN** a client attempts to delete a batch that at least one meal-log line references
- **THEN** the system rejects the request with a validation error
