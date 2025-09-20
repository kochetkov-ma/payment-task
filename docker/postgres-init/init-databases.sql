-- Simplified Database Initialization Script
-- Creates databases, users, and grants permissions only
-- JPA will handle table creation automatically

-- Create databases
CREATE DATABASE payment_db;
CREATE DATABASE balance_db;

-- Create users
CREATE USER payment_user WITH ENCRYPTED PASSWORD 'payment_password';
CREATE USER balance_user WITH ENCRYPTED PASSWORD 'balance_password';

-- Grant privileges to users
GRANT ALL PRIVILEGES ON DATABASE payment_db TO payment_user;
GRANT ALL PRIVILEGES ON DATABASE balance_db TO balance_user;

-- Connect to payment_db and set up permissions
\c payment_db;

-- Create extensions if needed (in payment_db)
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

-- Grant all privileges on public schema to payment_user
GRANT ALL PRIVILEGES ON SCHEMA public TO payment_user;
GRANT ALL PRIVILEGES ON ALL TABLES IN SCHEMA public TO payment_user;
GRANT ALL PRIVILEGES ON ALL SEQUENCES IN SCHEMA public TO payment_user;
GRANT CREATE ON SCHEMA public TO payment_user;

-- Connect to balance_db and set up permissions
\c balance_db;

-- Create extensions if needed (in balance_db)
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

-- Grant all privileges on public schema to balance_user
GRANT ALL PRIVILEGES ON SCHEMA public TO balance_user;
GRANT ALL PRIVILEGES ON ALL TABLES IN SCHEMA public TO balance_user;
GRANT ALL PRIVILEGES ON ALL SEQUENCES IN SCHEMA public TO balance_user;
GRANT CREATE ON SCHEMA public TO balance_user;