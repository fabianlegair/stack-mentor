CREATE TABLE users (
    user_id UUID PRIMARY KEY,
    username VARCHAR(16) UNIQUE NOT NULL,
    email VARCHAR(100) UNIQUE NOT NULL,
    phone_number VARCHAR(15) UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    date_of_birth DATE,
    city VARCHAR(50),
    state VARCHAR(2),
    age INT,
    profile_picture_url VARCHAR(255),
    bio TEXT,
    mentor_status BOOLEAN DEFAULT FALSE,
    mentee_status BOOLEAN DEFAULT FALSE,
    job_title VARCHAR(100),
    years_of_experience INT,
    industry VARCHAR(100),
    skills TEXT,
    interests TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE groups (
                        group_id UUID PRIMARY KEY,
                        group_name VARCHAR(50) NOT NULL,
                        description TEXT,
                        created_by UUID REFERENCES users(user_id) ON DELETE SET NULL,
                        created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                        members TEXT -- Comma-separated list of user IDs
);

CREATE TABLE group_members (
                               group_id UUID REFERENCES groups(group_id) ON DELETE CASCADE,
                               user_id UUID REFERENCES users(user_id) ON DELETE CASCADE,
                               role VARCHAR(20) DEFAULT 'member', -- e.g., 'admin', 'member'
                               joined_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                               PRIMARY KEY (group_id, user_id)
);

CREATE TABLE messages (
                          message_id UUID PRIMARY KEY,
                          sender_id UUID REFERENCES users(user_id) ON DELETE CASCADE,
                          receiver_id UUID REFERENCES users(user_id) ON DELETE CASCADE,
                          group_id UUID REFERENCES groups(group_id) ON DELETE CASCADE,
                          content TEXT NOT NULL,
                          media_url VARCHAR(255),
                          sent_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                          read_status BOOLEAN DEFAULT FALSE,
                          read_at TIMESTAMP
);