CREATE TABLE claims (
    id SERIAL PRIMARY KEY, 
    claim_id VARCHAR(255) UNIQUE NOT NULL, 
    claim_doc_id VARCHAR(255) UNIQUE , 
    policy_number VARCHAR(50),
    claimant_name VARCHAR(255),
    claim_type VARCHAR(50), 
    claim_amount NUMERIC(15, 2),
    case_id VARCHAR(50),
    status VARCHAR(50),
    created_at TIMESTAMP,
    processed_at TIMESTAMP,
    additional_data JSONB -- 
);