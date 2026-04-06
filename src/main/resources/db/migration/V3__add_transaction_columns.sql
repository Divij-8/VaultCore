ALTER TABLE transactions 
ADD COLUMN idempotency_key varchar(255) unique not null,
ADD COLUMN amount decimal(19, 4) not null;
