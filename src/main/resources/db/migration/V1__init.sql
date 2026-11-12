-- Accounts hold the current balance. The CHECK constraint is a last line of
-- defence: even buggy Java code can never push a balance below zero.
CREATE TABLE accounts (
    id              BIGSERIAL PRIMARY KEY,
    account_number  VARCHAR(20)    NOT NULL UNIQUE,
    owner_name      VARCHAR(100)   NOT NULL,
    currency        VARCHAR(3)     NOT NULL,
    status          VARCHAR(20)    NOT NULL,
    balance         NUMERIC(19, 2) NOT NULL DEFAULT 0 CHECK (balance >= 0),
    version         BIGINT         NOT NULL DEFAULT 0,
    created_at      TIMESTAMPTZ    NOT NULL DEFAULT now()
);

-- Every request (completed or rejected) is recorded here.
CREATE TABLE transactions (
    id                BIGSERIAL PRIMARY KEY,
    type              VARCHAR(20)    NOT NULL,
    status            VARCHAR(20)    NOT NULL,
    amount            NUMERIC(19, 2) NOT NULL CHECK (amount > 0),
    currency          VARCHAR(3)     NOT NULL,
    from_account_id   BIGINT REFERENCES accounts (id),
    to_account_id     BIGINT REFERENCES accounts (id),
    rejection_reason  VARCHAR(255),
    created_at        TIMESTAMPTZ    NOT NULL DEFAULT now()
);

-- Double-entry ledger: money never appears or disappears.
-- A transfer writes one DEBIT (money out) and one CREDIT (money in).
CREATE TABLE ledger_entries (
    id              BIGSERIAL PRIMARY KEY,
    transaction_id  BIGINT         NOT NULL REFERENCES transactions (id),
    account_id      BIGINT         NOT NULL REFERENCES accounts (id),
    direction       VARCHAR(6)     NOT NULL CHECK (direction IN ('DEBIT', 'CREDIT')),
    amount          NUMERIC(19, 2) NOT NULL CHECK (amount > 0),
    created_at      TIMESTAMPTZ    NOT NULL DEFAULT now()
);

CREATE INDEX idx_transactions_from ON transactions (from_account_id);
CREATE INDEX idx_transactions_to   ON transactions (to_account_id);
CREATE INDEX idx_ledger_account    ON ledger_entries (account_id);
