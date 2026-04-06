-- Add version column to accounts table for optimistic locking
ALTER TABLE accounts ADD COLUMN version BIGINT DEFAULT 0;
