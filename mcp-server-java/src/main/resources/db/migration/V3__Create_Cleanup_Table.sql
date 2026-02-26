CREATE TABLE pending_cleanups (
    id SERIAL PRIMARY KEY,
    instance_id VARCHAR(50) NOT NULL,
    repository_id INT,
    failure_reason TEXT,
    status VARCHAR(20) DEFAULT 'PENDING',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    retry_count INT DEFAULT 0
);