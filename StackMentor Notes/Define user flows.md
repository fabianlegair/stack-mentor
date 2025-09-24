**Account Creation Flow**
Open app → Sign up → Backend validates → Hash + store in DB → Email verification → Log in → Backend validates → Profile setup → Homepage

**Login Flow**
Open app → Sign in →Backend validates (DB lookup + password hash check) → Issue JWT access + refresh token → Store tokens on device → Homepage

**User Search Flow**
Homepage → Search page → User enters query into search bar → Frontend debounces input + sends API request → Backend queries Postgres → Return list of matching users → Frontend displays results

**P2P Messaging Flow**
Homepage → Chat page → Select/Search for recipient or open recent → Establish WebSocket Connection → User sends message → Backend writes to Postgres + pushes message to recipient via WebSocket → Recipient receives + UI updates in real time

**Group Messaging Flow**
Homepage → Chat page → Select/Search for group or open recent or create group → Backend creates group entry in Postgres → Group chat channel established (WebSocket) → User sends message → Backend stores + pushes to all members via WebSocket → Group receives + UI updates in real time

