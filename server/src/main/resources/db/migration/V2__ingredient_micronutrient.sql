-- openspec: micronutrient-rollup — sparse per-ingredient micronutrient
-- amounts (key -> amount per one unit, same basis as the macro columns).
CREATE TABLE ingredient_micronutrient (
  ingredient_id  INTEGER NOT NULL REFERENCES ingredient(id),
  nutrient_key   TEXT NOT NULL,
  amount         REAL NOT NULL,
  PRIMARY KEY (ingredient_id, nutrient_key)
);
