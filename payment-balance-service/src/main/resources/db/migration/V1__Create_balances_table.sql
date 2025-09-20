CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

CREATE TABLE balances (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    username VARCHAR(255) UNIQUE NOT NULL,
    balance DECIMAL(19,2) NOT NULL,
    reserved_amount DECIMAL(19,2) NOT NULL DEFAULT 0.00,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(255),
    updated_by VARCHAR(255)
);

CREATE INDEX idx_balances_username ON balances(username);
CREATE INDEX idx_balances_balance ON balances(balance);
CREATE INDEX idx_balances_reserved_amount ON balances(reserved_amount);