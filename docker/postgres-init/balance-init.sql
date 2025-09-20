-- Balance Service Database Initialization Script

-- Create extensions if needed
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

-- Create schema for balance service
CREATE SCHEMA IF NOT EXISTS balance;

-- Grant permissions to balance_user
GRANT ALL PRIVILEGES ON SCHEMA balance TO balance_user;
GRANT ALL PRIVILEGES ON ALL TABLES IN SCHEMA balance TO balance_user;
GRANT ALL PRIVILEGES ON ALL SEQUENCES IN SCHEMA balance TO balance_user;

-- Create basic tables structure (will be updated by Hibernate DDL)
-- This is just to ensure schema exists

-- Account balance table
CREATE TABLE IF NOT EXISTS account_balances (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT UNIQUE NOT NULL,
    available_balance DECIMAL(19, 2) DEFAULT 0.00,
    hold_balance DECIMAL(19, 2) DEFAULT 0.00,
    total_balance DECIMAL(19, 2) DEFAULT 0.00,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Balance hold table for tracking holds
CREATE TABLE IF NOT EXISTS balance_holds (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id BIGINT NOT NULL,
    hold_amount DECIMAL(19, 2) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    transaction_reference VARCHAR(255),
    expiry_time TIMESTAMP,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (user_id) REFERENCES account_balances(user_id)
);

-- Balance transaction history
CREATE TABLE IF NOT EXISTS balance_transactions (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id BIGINT NOT NULL,
    transaction_type VARCHAR(20) NOT NULL,
    amount DECIMAL(19, 2) NOT NULL,
    balance_before DECIMAL(19, 2),
    balance_after DECIMAL(19, 2),
    reference VARCHAR(255),
    description TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (user_id) REFERENCES account_balances(user_id)
);

-- Create indexes for better performance
CREATE INDEX IF NOT EXISTS idx_account_balances_user_id ON account_balances(user_id);
CREATE INDEX IF NOT EXISTS idx_balance_holds_user_id ON balance_holds(user_id);
CREATE INDEX IF NOT EXISTS idx_balance_holds_status ON balance_holds(status);
CREATE INDEX IF NOT EXISTS idx_balance_transactions_user_id ON balance_transactions(user_id);
CREATE INDEX IF NOT EXISTS idx_balance_transactions_created_at ON balance_transactions(created_at);

-- Insert sample balance data for testing
INSERT INTO account_balances (user_id, available_balance, hold_balance, total_balance)
VALUES
    (1, 1000.00, 0.00, 1000.00),
    (2, 500.00, 0.00, 500.00)
ON CONFLICT (user_id) DO NOTHING;

-- Grant all privileges on newly created tables
GRANT ALL PRIVILEGES ON ALL TABLES IN SCHEMA public TO balance_user;
GRANT ALL PRIVILEGES ON ALL SEQUENCES IN SCHEMA public TO balance_user;