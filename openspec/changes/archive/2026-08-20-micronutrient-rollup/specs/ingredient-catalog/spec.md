## ADDED Requirements

### Requirement: Ingredients carry sparse micronutrient amounts
An ingredient SHALL accept an optional `micronutrients` object (non-blank
key → amount ≥ 0) on create and update, persisted sparsely and returned
on every read. A request omitting the field (or sending null) SHALL
behave as an empty map — existing clients keep working unchanged.
Deleting an ingredient SHALL remove its micronutrient rows in the same
transaction.

#### Scenario: Create with micronutrients
- **WHEN** POST /api/ingredients includes `"micronutrients": {"vitaminD": 25}`
- **THEN** the created ingredient echoes the map, and GET returns it

#### Scenario: Omitted field is empty
- **WHEN** POST /api/ingredients omits `micronutrients`
- **THEN** the ingredient is created with an empty map and reads back as `{}`

#### Scenario: Invalid values rejected
- **WHEN** a request maps a blank key or a negative amount
- **THEN** the service responds 400 and nothing is stored

#### Scenario: Update replace-sets
- **WHEN** PUT /api/ingredients/{id} sends a different micronutrient map
- **THEN** reads return exactly the new map
