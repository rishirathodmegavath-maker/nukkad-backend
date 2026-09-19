CREATE TABLE event_startups (
  id         CHAR(36)  NOT NULL,
  event_id   CHAR(36)  NOT NULL,
  startup_id CHAR(36)  NOT NULL,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uq_event_startup (event_id, startup_id),
  KEY idx_event_startups_startup (startup_id),
  CONSTRAINT fk_event_startups_event FOREIGN KEY (event_id) REFERENCES events(id) ON DELETE CASCADE,
  CONSTRAINT fk_event_startups_startup FOREIGN KEY (startup_id) REFERENCES startups(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
