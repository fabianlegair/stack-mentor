**User Model**

| Column              | Data Type    | Unique | Not Null |
| ------------------- | ------------ | ------ | -------- |
| user_id             | UUID         | ✓      | ✓        |
| username            | VARCHAR(16)  | ✓      | ✓        |
| email               | VARCHAR(100) | ✓      | ✓        |
| phone_number        | VARCHAR(15)  | ✓      |          |
| password_hash       | VARCHAR(255) |        | ✓        |
| date_of_birth       | DATE         |        | ✓        |
| city                | VARCHAR(26)  |        |          |
| state               | VARCHAR(2)   |        |          |
| age                 | INT          |        |          |
| profile_picture_url | VARCHAR(255) |        |          |
| bio                 | TEXT         |        |          |
| mentor_status       | BOOLEAN      |        |          |
| mentee_status       | BOOLEAN      |        |          |
| job_title           | VARCHAR(100) |        |          |
| years_of_experience | INT          |        |          |
| industry            | VARCHAR(100) |        |          |
| skills              | TEXT         |        |          |
| interests           | TEXT         |        |          |
| created_at          | TIMESTAMP    |        |          |


**Message Model**

| Column      | Data Type    | Unique | Not Null |
| ----------- | ------------ | ------ | -------- |
| message_id  | UUID         | ✓      | ✓        |
| sender_id   | UUID         | ✓      | ✓        |
| receiver_id | UUID         | ✓      |          |
| group_id    | UUID         | ✓      |          |
| content     | TEXT         |        |          |
| media_url   | VARCHAR(255) |        |          |
| sent_at     | TIMESTAMP    |        | ✓        |
| read_status | BOOLEAN      |        |          |
| read_at     | TIMESTAMP    |        |          |

**Group Model**

| Column      | Data Type   | Unique | Not Null |
| ----------- | ----------- | ------ | -------- |
| group_id    | UUID        | ✓      | ✓        |
| group_name  | VARCHAR(50) |        | ✓        |
| description | TEXT        |        |          |
| created_by  | UUID        | ✓      | ✓        |
| created_at  | TIMESTAMP   |        | ✓        |
| members     | TEXT        |        |          |
**Group Members Model**

| Column    | Data Type   | Unique | Not Null |
| --------- | ----------- | ------ | -------- |
| group_id  | UUID        | ✓      | ✓        |
| user_id   | UUID        | ✓      | ✓        |
| role      | VARCHAR(15) |        |          |
| joined_at | TIMESTAMP   |        |          |