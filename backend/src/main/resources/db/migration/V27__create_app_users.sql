CREATE TABLE app_users (
  id           BIGSERIAL PRIMARY KEY,
  name         VARCHAR(100) NOT NULL,
  email        VARCHAR(150) NOT NULL UNIQUE,
  password     VARCHAR(255) NOT NULL,
  role         VARCHAR(30) NOT NULL,
  initials     VARCHAR(5),
  avatar_color VARCHAR(20),
  active       BOOLEAN DEFAULT TRUE,
  created_at   TIMESTAMP DEFAULT NOW()
);

CREATE INDEX idx_users_email ON app_users(email);
