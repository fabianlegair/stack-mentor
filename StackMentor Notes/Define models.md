**User Model**

| Column              | Data Type   | Unique | Not Null |
| ------------------- | ----------- | ------ | -------- |
| user_id             | UUID        | ✓      | ✓        |
| username            | VARCHAR(16) | ✓      | ✓        |
| email               | VARCHAR(24) | ✓      | ✓        |
| phone_number        | VARCHAR(10) | ✓      |          |
| password_hash       | TEXT        |        | ✓        |
| date_of_birth       | DATE        |        | ✓        |
| city                | VARCHAR(26) |        |          |
| state               | VARCHAR(2)  |        |          |
| age                 | INT         |        |          |
| profile_picture     | TEXT        |        |          |
| bio                 | TEXT        |        |          |
| mentor_mentee       | TEXT        |        |          |
| job_title           | TEXT        |        |          |
| years_of_experience | INT         |        |          |
| areas_of_expertise  | TEXT        |        |          |
| seeking_help_in     | TEXT        |        |          |
| created_date        | TIMESTAMP   |        |          |
| updated_date        | TIMESTAMP   |        |          |


**Message Model**

| Column       | Data Type | Unique | Not Null |
| ------------ | --------- | ------ | -------- |
| message_id   | UUID      | ✓      | ✓        |
| sender_id    | UUID      | ✓      | ✓        |
| recipient_id | UUID      | ✓      |          |
| group_id     | UUID      | ✓      |          |
| content      | TEXT      |        |          |
| media_url    | TEXT      |        |          |
| sent_at      | TIMESTAMP |        | ✓        |
| read_at      | TIMESTAMP |        |          |

**Group Model**

| Column      | Data Type   | Unique | Not Null |
| ----------- | ----------- | ------ | -------- |
| group_id    | UUID        | ✓      | ✓        |
| group_name  | VARCHAR(50) |        | ✓        |
| description | TEXT        |        |          |
| created_by  | UUID        | ✓      | ✓        |
| created_at  | TIMESTAMP   |        | ✓        |
**Group Members Model**

| Column    | Data Type   | Unique | Not Null |
| --------- | ----------- | ------ | -------- |
| group_id  | UUID        | ✓      | ✓        |
| user_id   | UUID        | ✓      | ✓        |
| role      | VARCHAR(15) |        |          |
| joined_at | TIMESTAMP   |        |          |