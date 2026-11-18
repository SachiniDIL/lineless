-- Remove the unique constraint from users.name
ALTER TABLE users
DROP CONSTRAINT IF EXISTS users_name_key;

-- Create user identities table
CREATE TABLE user_identities (
                                 id UUID PRIMARY KEY,
                                 user_id UUID NOT NULL,
                                 provider VARCHAR(20) NOT NULL,
                                 provider_subject VARCHAR(255) NOT NULL,
                                 created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

                                 CONSTRAINT fk_user_identities_user
                                     FOREIGN KEY (user_id) REFERENCES users(id),

                                 CONSTRAINT uk_user_identities_provider_subject
                                     UNIQUE (provider, provider_subject),

                                 CONSTRAINT uk_user_identities_user_provider
                                     UNIQUE (user_id, provider)
);

CREATE INDEX idx_user_identities_user_id
    ON user_identities(user_id);