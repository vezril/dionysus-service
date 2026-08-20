## ADDED Requirements

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
