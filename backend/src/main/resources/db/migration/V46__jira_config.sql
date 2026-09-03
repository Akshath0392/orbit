CREATE TABLE jira_config (
    id             BIGSERIAL PRIMARY KEY,
    base_url       VARCHAR(500),
    email          VARCHAR(255),
    api_token      VARCHAR(2000),
    webhook_secret VARCHAR(500),
    updated_at     TIMESTAMP,
    updated_by     VARCHAR(100)
);

INSERT INTO jira_config (base_url, email, api_token, webhook_secret)
VALUES ('', '', '', 'dev-secret');
