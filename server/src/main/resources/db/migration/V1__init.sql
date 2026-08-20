-- openspec: meal-planning-health — Recipe -> Batch -> Meal domain.
-- SQLite: INTEGER PRIMARY KEY is an alias for the rowid (auto-increment).
-- Timestamps are stored as ISO-8601 text (UTC); booleans as INTEGER 0/1.

CREATE TABLE ingredient (
  id                 INTEGER PRIMARY KEY,
  name               TEXT NOT NULL,
  calories_kcal      REAL NOT NULL,
  protein_g          REAL NOT NULL,
  carbs_g            REAL NOT NULL,
  fat_g              REAL NOT NULL,
  sodium_mg          REAL NOT NULL,
  abv_percent        REAL,
  directly_loggable  INTEGER NOT NULL DEFAULT 0
);

-- One row per ingredient once stock is first touched; absence means zero on-hand.
CREATE TABLE pantry_stock (
  ingredient_id      INTEGER PRIMARY KEY REFERENCES ingredient(id),
  on_hand_quantity   REAL NOT NULL DEFAULT 0
);

CREATE TABLE recipe (
  id                 INTEGER PRIMARY KEY,
  name               TEXT NOT NULL,
  servings           INTEGER NOT NULL
);

CREATE TABLE recipe_line (
  id                 INTEGER PRIMARY KEY,
  recipe_id          INTEGER NOT NULL REFERENCES recipe(id),
  ingredient_id      INTEGER NOT NULL REFERENCES ingredient(id),
  quantity           REAL NOT NULL,
  unit               TEXT NOT NULL
);
CREATE INDEX idx_recipe_line_recipe_id ON recipe_line(recipe_id);

-- Immutable once created (design.md Decision 4) — no updated_at, no update path.
CREATE TABLE batch (
  id                 INTEGER PRIMARY KEY,
  recipe_id          INTEGER NOT NULL REFERENCES recipe(id),
  cooked_at          TEXT NOT NULL,
  servings_made      REAL NOT NULL
);
CREATE INDEX idx_batch_recipe_id ON batch(recipe_id);

CREATE TABLE meal (
  id                 INTEGER PRIMARY KEY,
  eaten_at           TEXT NOT NULL
);
CREATE INDEX idx_meal_eaten_at ON meal(eaten_at);

-- A line is EITHER a batch-portion (batch_id + portions set, ingredient_id/quantity/unit null)
-- OR a direct-consumable (ingredient_id + quantity + unit set, batch_id/portions null).
-- line_type disambiguates rather than relying on nullability alone.
CREATE TABLE meal_line (
  id                 INTEGER PRIMARY KEY,
  meal_id            INTEGER NOT NULL REFERENCES meal(id),
  line_type          TEXT NOT NULL CHECK (line_type IN ('batch_portion', 'direct_consumable')),
  batch_id           INTEGER REFERENCES batch(id),
  portions           REAL,
  ingredient_id      INTEGER REFERENCES ingredient(id),
  quantity           REAL,
  unit               TEXT
);
CREATE INDEX idx_meal_line_meal_id ON meal_line(meal_id);
CREATE INDEX idx_meal_line_batch_id ON meal_line(batch_id);
