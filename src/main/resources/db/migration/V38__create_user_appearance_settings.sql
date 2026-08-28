CREATE TABLE user_appearance_settings (
  user_id CHAR(36) NOT NULL,
  theme_mode ENUM('LIGHT','DARK','SYSTEM') NOT NULL DEFAULT 'SYSTEM',
  theme_preset VARCHAR(30) NOT NULL DEFAULT 'NUKKAD_INDIGO',
  custom_primary_color VARCHAR(7) NULL,
  sidebar_color VARCHAR(7) NULL,
  page_bg_color VARCHAR(7) NULL,
  card_bg_color VARCHAR(7) NULL,
  header_bg_color VARCHAR(7) NULL,
  border_color VARCHAR(7) NULL,
  secondary_surface_color VARCHAR(7) NULL,
  PRIMARY KEY (user_id),
  CONSTRAINT fk_user_appearance_settings_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
