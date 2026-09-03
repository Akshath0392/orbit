-- Portfolio → Client becomes 1:N via join table
CREATE TABLE portfolio_clients (
  portfolio_id BIGINT NOT NULL REFERENCES portfolios(id) ON DELETE CASCADE,
  client_id    BIGINT NOT NULL REFERENCES clients(id)    ON DELETE CASCADE,
  PRIMARY KEY (portfolio_id, client_id)
);

-- Migrate existing single-client associations
INSERT INTO portfolio_clients (portfolio_id, client_id)
SELECT id, client_id FROM portfolios WHERE client_id IS NOT NULL;

-- Drop old FK column (no longer needed)
ALTER TABLE portfolios DROP COLUMN IF EXISTS client_id;
