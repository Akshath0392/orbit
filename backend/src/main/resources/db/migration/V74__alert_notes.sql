-- Mitigation notes on alerts — separate audit trail (per remediation P0.12).
-- Existing alerts.mitigation_note column captures the latest snapshot; this
-- table records every note appended over the alert's lifecycle.

CREATE TABLE IF NOT EXISTS alert_notes (
    id          BIGSERIAL PRIMARY KEY,
    alert_id    BIGINT       NOT NULL REFERENCES alerts(id) ON DELETE CASCADE,
    note        TEXT         NOT NULL,
    created_by  VARCHAR(255),
    created_at  TIMESTAMP    NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_alert_notes_alert
    ON alert_notes (alert_id, created_at DESC);
