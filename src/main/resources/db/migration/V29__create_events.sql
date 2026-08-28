CREATE TABLE events (
  id                 CHAR(36)     NOT NULL,
  title              VARCHAR(200) NOT NULL,
  description        TEXT         NULL,
  chapter_id         CHAR(36)     NULL,
  organizer_user_id  CHAR(36)     NOT NULL,
  start_at           TIMESTAMP    NOT NULL,
  end_at             TIMESTAMP    NOT NULL,
  is_online          BOOLEAN      NOT NULL DEFAULT FALSE,
  location           VARCHAR(300) NULL,
  meeting_url        VARCHAR(500) NULL,
  cover_image_url    VARCHAR(500) NULL,
  capacity           INT          NULL,
  created_at         TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at         TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_events_chapter (chapter_id),
  KEY idx_events_organizer (organizer_user_id),
  KEY idx_events_start_at (start_at),
  FULLTEXT KEY ft_events_search (title, description),
  CONSTRAINT fk_events_chapter FOREIGN KEY (chapter_id) REFERENCES chapters(id) ON DELETE CASCADE,
  CONSTRAINT fk_events_organizer FOREIGN KEY (organizer_user_id) REFERENCES users(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE event_attendees (
  id             CHAR(36)  NOT NULL,
  event_id       CHAR(36)  NOT NULL,
  user_id        CHAR(36)  NOT NULL,
  registered_at  TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uq_event_attendee (event_id, user_id),
  KEY idx_event_attendees_user (user_id),
  CONSTRAINT fk_event_attendees_event FOREIGN KEY (event_id) REFERENCES events(id) ON DELETE CASCADE,
  CONSTRAINT fk_event_attendees_user FOREIGN KEY (user_id) REFERENCES users(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
