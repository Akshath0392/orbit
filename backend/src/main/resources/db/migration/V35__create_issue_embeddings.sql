-- pgvector extension required for production; skipped if not available
-- CREATE EXTENSION IF NOT EXISTS vector;

CREATE TABLE issue_embeddings (
  id          BIGSERIAL PRIMARY KEY,
  issue_id    BIGINT REFERENCES jira_issues(id) UNIQUE,
  embedding_text TEXT,
  embedded_at TIMESTAMP DEFAULT NOW()
);
