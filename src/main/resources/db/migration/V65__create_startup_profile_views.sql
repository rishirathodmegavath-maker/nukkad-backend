CREATE TABLE startup_profile_views (
  id          CHAR(36)  NOT NULL,
  startup_id  CHAR(36)  NOT NULL,
  viewer_id   CHAR(36)  NULL,
  created_at  TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_spv_startup (startup_id),
  CONSTRAINT fk_spv_startup FOREIGN KEY (startup_id) REFERENCES startups(id) ON DELETE CASCADE,
  CONSTRAINT fk_spv_viewer FOREIGN KEY (viewer_id) REFERENCES users(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
