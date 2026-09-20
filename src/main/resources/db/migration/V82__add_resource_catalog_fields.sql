-- Resource library redesign: a curated catalogue with categories, thumbnails and a "featured" shelf.
-- The type ENUM gains the formats a learning library needs (article, guide, course, tool, deck).
-- category holds a fixed slug (see ResourceCategory); provider is the source's display name ("Y Combinator").
ALTER TABLE resources
  MODIFY COLUMN type ENUM('Document','Link','Video','Note','Template','Article','Guide','Course','Tool','Deck') NOT NULL,
  ADD COLUMN category         VARCHAR(30)  NULL,
  ADD COLUMN provider         VARCHAR(120) NULL,
  ADD COLUMN thumbnail_url    VARCHAR(500) NULL,
  ADD COLUMN duration_minutes INT          NULL,
  ADD COLUMN featured         BOOLEAN      NOT NULL DEFAULT FALSE,
  ADD KEY idx_resources_category (category);
