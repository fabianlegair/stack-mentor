CREATE TABLE groups (
    group_id UUID PRIMARY KEY,
    group_name VARCHAR(50) NOT NULL,
    description TEXT,
    created_by UUID REFERENCES users(user_id) ON DELETE SET NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    members TEXT -- Comma-separated list of user IDs
);