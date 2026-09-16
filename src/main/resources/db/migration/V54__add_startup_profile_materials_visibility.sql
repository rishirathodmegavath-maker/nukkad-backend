-- Batch 1 of the Founder & Startup Product Update: rich profile fields, materials,
-- visibility and fundraising-visibility. All additive/nullable so existing startups
-- keep working unchanged; the old `traction` column is left untouched (never dropped).

ALTER TABLE startups
  ADD COLUMN location             VARCHAR(200) NULL AFTER logo_url,
  ADD COLUMN website               VARCHAR(500) NULL AFTER location,
  ADD COLUMN target_customer       TEXT NULL AFTER solution,
  ADD COLUMN business_model        TEXT NULL AFTER target_customer,
  ADD COLUMN what_building         TEXT NULL AFTER business_model,
  ADD COLUMN revenue                VARCHAR(200) NULL AFTER traction,
  ADD COLUMN customers              VARCHAR(200) NULL AFTER revenue,
  ADD COLUMN users                  VARCHAR(200) NULL AFTER customers,
  ADD COLUMN growth                 VARCHAR(200) NULL AFTER users,
  ADD COLUMN other_traction         TEXT NULL AFTER growth,
  ADD COLUMN keywords               TEXT NULL AFTER other_traction,
  ADD COLUMN visibility             ENUM('Public','Nukkad Members') NOT NULL DEFAULT 'Public' AFTER keywords,
  ADD COLUMN fundraising_visible    BOOLEAN NOT NULL DEFAULT TRUE AFTER visibility,
  ADD KEY idx_startups_visibility (visibility);

-- Preserve existing free-text traction by copying it into the new structured "other traction"
-- field, without touching or deleting the original `traction` column.
UPDATE startups SET other_traction = traction WHERE traction IS NOT NULL AND traction <> '';

CREATE TABLE startup_materials (
  id                    CHAR(36)     NOT NULL,
  startup_id            CHAR(36)     NOT NULL,
  material_type         ENUM('Website','Pitch Deck','Product Demo','Screenshots','LinkedIn','X','Other Document') NOT NULL,
  title                 VARCHAR(200) NULL,
  url                   VARCHAR(500) NOT NULL,
  original_file_name    VARCHAR(255) NULL,
  content_type          VARCHAR(100) NULL,
  sort_order            INT          NOT NULL DEFAULT 0,
  created_by_user_id    CHAR(36)     NOT NULL,
  created_at            TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at            TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_smaterial_startup (startup_id, material_type),
  CONSTRAINT fk_smaterial_startup FOREIGN KEY (startup_id) REFERENCES startups(id) ON DELETE CASCADE,
  CONSTRAINT fk_smaterial_creator FOREIGN KEY (created_by_user_id) REFERENCES users(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
