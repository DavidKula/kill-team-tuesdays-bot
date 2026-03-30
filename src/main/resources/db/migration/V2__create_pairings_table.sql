CREATE TABLE pairings
(
    id                      BIGSERIAL PRIMARY KEY,
    poll_id                 BIGINT      NOT NULL REFERENCES polls (id) ON DELETE CASCADE,
    player1_discord_user_id VARCHAR(64) NOT NULL,
    player2_discord_user_id VARCHAR(64),
    created_at              TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_pairings_poll_id ON pairings (poll_id);
