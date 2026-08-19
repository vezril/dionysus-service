## ADDED Requirements

### Requirement: A meal is a timestamped eating event with one or more lines
The system SHALL allow creating a meal specifying an `eatenAt` timestamp and one or more lines. Each line is either a batch-portion line or a direct-consumable line.

#### Scenario: Creating a meal with at least one line succeeds
- **WHEN** a client submits a meal with `eatenAt` and one batch-portion line
- **THEN** the system creates the meal and returns its assigned ID

#### Scenario: A meal requires at least one line
- **WHEN** a client submits a meal with zero lines
- **THEN** the system rejects the request with a validation error

### Requirement: A batch-portion line references a batch and a portion count
The system SHALL allow a meal line to reference an existing `batchId` and a `portions` value (positive number). The line's nutrition contribution SHALL be the batch's recipe per-serving nutrition multiplied by `portions`.

#### Scenario: Logging a portion of a batch
- **WHEN** a client submits a batch-portion line referencing an existing batch with `portions: 1`
- **THEN** the system creates the line, and the meal's nutrition total includes that batch's recipe per-serving nutrition once

#### Scenario: Batch-portion line requires an existing batch
- **WHEN** a client submits a batch-portion line referencing a `batchId` that does not exist
- **THEN** the system rejects the request with a validation error

#### Scenario: Portions must be positive
- **WHEN** a client submits a batch-portion line with `portions: 0` or negative
- **THEN** the system rejects the request with a validation error

### Requirement: A batch-portion line cannot exceed the batch's remaining portions
The system SHALL reject a batch-portion line whose `portions` would cause the referenced batch's total logged portions to exceed its `servingsMade`.

#### Scenario: Logging more than what remains is rejected
- **WHEN** a batch has `servingsMade: 4` with 3 portions already logged, and a client submits a new line for `portions: 2`
- **THEN** the system rejects the request with a validation error, since only 1 portion remains

#### Scenario: Logging exactly the remaining amount succeeds
- **WHEN** a batch has `servingsMade: 4` with 3 portions already logged, and a client submits a new line for `portions: 1`
- **THEN** the system creates the line, leaving 0 portions remaining

### Requirement: A direct-consumable line references a directly-loggable ingredient
The system SHALL allow a meal line to reference an `ingredientId` and a `quantity`/`unit`, provided that ingredient is flagged `directlyLoggable`. The line's nutrition contribution SHALL be that ingredient's nutrition scaled by the quantity.

#### Scenario: Logging a directly-loggable ingredient
- **WHEN** a client submits a direct-consumable line for an ingredient flagged `directlyLoggable: true`
- **THEN** the system creates the line, and the meal's nutrition total includes that ingredient's nutrition scaled by the logged quantity

#### Scenario: Logging a non-directly-loggable ingredient is rejected
- **WHEN** a client submits a direct-consumable line for an ingredient flagged `directlyLoggable: false`
- **THEN** the system rejects the request with a validation error

### Requirement: A single meal can mix batch-portion and direct-consumable lines
The system SHALL allow a meal to contain any combination of batch-portion and direct-consumable lines.

#### Scenario: Dinner with a batch portion and a direct consumable
- **WHEN** a client submits one meal with a batch-portion line (1 portion of a lasagna batch) and a direct-consumable line (1 glass of wine)
- **THEN** the system creates the meal with both lines, and its nutrition total includes the contribution of each
