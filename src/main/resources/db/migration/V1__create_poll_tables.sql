CREATE TABLE polls
(
    id                 BIGSERIAL PRIMARY KEY,
    discord_message_id VARCHAR(64),
    discord_channel_id VARCHAR(64)  NOT NULL,
    question           VARCHAR(300) NOT NULL,
    created_at         TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    expires_at         TIMESTAMPTZ,
    state              VARCHAR(20)  NOT NULL DEFAULT 'NEW'
);

CREATE TABLE poll_options
(
    id           BIGSERIAL PRIMARY KEY,
    poll_id      BIGINT      NOT NULL REFERENCES polls (id) ON DELETE CASCADE,
    option_text  VARCHAR(55) NOT NULL,
    option_index INTEGER     NOT NULL,
    UNIQUE (poll_id, option_index)
);

CREATE TABLE poll_votes
(
    id              BIGSERIAL PRIMARY KEY,
    poll_option_id  BIGINT      NOT NULL REFERENCES poll_options (id) ON DELETE CASCADE,
    discord_user_id VARCHAR(64) NOT NULL,
    voted_at        TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (poll_option_id, discord_user_id)
);

CREATE INDEX idx_poll_votes_user ON poll_votes (discord_user_id);
CREATE INDEX idx_poll_votes_option ON poll_votes (poll_option_id);

-- Spring Modulith event publication table
CREATE TABLE event_publication
(
    id                     UUID        NOT NULL PRIMARY KEY,
    listener_id            TEXT        NOT NULL,
    event_type             TEXT        NOT NULL,
    serialized_event       TEXT        NOT NULL,
    publication_date       TIMESTAMPTZ NOT NULL,
    completion_date        TIMESTAMPTZ,
    last_resubmission_date TIMESTAMPTZ,
    completion_attempts    INTEGER     NOT NULL DEFAULT 0,
    status                 VARCHAR(255)
);
