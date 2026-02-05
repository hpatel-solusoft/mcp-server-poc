-- This creates the table in the H2 memory DB so the Repository doesn't crash
CREATE TABLE IF NOT EXISTS claim (
    id SERIAL PRIMARY KEY,
    claim_id VARCHAR(255),
    claim_doc_id VARCHAR(255),
    policy_number VARCHAR(255),
    claimant_name VARCHAR(255),
    claim_type VARCHAR(255),
    claim_amount DECIMAL(20, 2),
    case_id VARCHAR(255),
    status VARCHAR(255),
    created_at TIMESTAMP,
    processed_at TIMESTAMP,
    additional_info TEXT
);