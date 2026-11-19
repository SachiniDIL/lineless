ALTER TABLE users
    ADD COLUMN email_verified BOOLEAN NOT NULL DEFAULT FALSE;

ALTER TABLE users
    ADD COLUMN email_verified_at TIMESTAMPTZ;

CREATE TABLE email_verification_tokens (
                                           id UUID PRIMARY KEY,

                                           user_id UUID NOT NULL,

                                           token_hash VARCHAR(255) NOT NULL,

                                           expires_at TIMESTAMPTZ NOT NULL,

                                           used_at TIMESTAMPTZ,

                                           created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

                                           CONSTRAINT fk_email_verification_tokens_user
                                               FOREIGN KEY (user_id)
                                                   REFERENCES users(id),

                                           CONSTRAINT uk_email_verification_tokens_token_hash
                                               UNIQUE (token_hash)
);

CREATE INDEX idx_email_verification_tokens_user_id
    ON email_verification_tokens(user_id);