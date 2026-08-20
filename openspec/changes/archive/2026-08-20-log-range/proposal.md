# Proposal: log-range

## Why

dionysus-planner is growing a consumption dashboard (day/week/month/
year). Only `GET /api/log/{date}` exists — a year view would need 365
requests.

## What Changes

`GET /api/log/range?from=YYYY-MM-DD&to=YYYY-MM-DD`: one call returning
per-day rollups for the inclusive range — `{days: [{date,
totalNutrition, mealCount}]}`, sparse (days without meals omitted),
bucketed in the configured timezone exactly like the single-day
endpoint. Bad dates or from > to → 400; range capped at 400 days.

## Impact

One repo method (bucketed range listing reusing dayZone), one route,
tests. No schema changes.
