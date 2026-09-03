CREATE TABLE uat_sign_offs (
  id           BIGSERIAL PRIMARY KEY,
  cycle_id     BIGINT REFERENCES uat_cycles(id),
  status       VARCHAR(20),
  signed_off_by VARCHAR(100),
  notes        TEXT,
  created_at   TIMESTAMP DEFAULT NOW()
);

CREATE TABLE developers (
  id           BIGSERIAL PRIMARY KEY,
  name         VARCHAR(100) NOT NULL,
  team         VARCHAR(50),
  utilization  INTEGER DEFAULT 0,
  active_tasks INTEGER DEFAULT 0,
  on_leave     BOOLEAN DEFAULT FALSE,
  leave_period VARCHAR(50),
  initials     VARCHAR(5),
  avatar_color VARCHAR(20)
);
