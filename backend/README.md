# GS-ELS-Group3

## Backend Database Setup

The backend now uses PostgreSQL with Flyway migrations.

Key files:
- `backend/backend/pom.xml` includes `spring-boot-starter-jdbc`, `flyway-core`, `flyway-database-postgresql`, and `postgresql`.
- `backend/backend/src/main/resources/application.properties` contains the datasource and Flyway configuration.
- `backend/backend/src/main/resources/db/migration/V1__create_users_table.sql` creates the `users` table.

Run the backend:

```bash
cd backend/backend
./mvnw spring-boot:run
```

If port 8080 is in use:

```bash
SERVER_PORT=8081 ./mvnw spring-boot:run
```

Notes:
- If your database credentials change, update them in `backend/backend/src/main/resources/application.properties`.
- Flyway creates `flyway_schema_history` and applies migrations on startup.

## Backend Auth Sync Flow

### Purpose

The backend uses Firebase only as the identity provider and PostgreSQL for application data.

### Current Status

Implemented:
- `/api/auth/sync` endpoint
- bearer token extraction
- Firebase ID token verification
- verified Firebase user data returned
- Firebase Admin initializes lazily (only when `/api/auth/sync` is called)

Not implemented yet:
- PostgreSQL user upsert
- database-backed user sync
- protected backend resource ownership using PostgreSQL user records

### Main Endpoint

```
POST /api/auth/sync
```

### Request Contract

- `Authorization: Bearer <firebase_id_token>`
- JSON body:

```json
{
  "uid": "firebase_uid"
}
```

### Current Response Shape

```json
{
  "success": true,
  "uid": "firebase_uid",
  "email": "user@example.com",
  "displayName": "User Name",
  "photoUrl": "https://...",
  "authProvider": "google.com",
  "syncStatus": "pending",
  "message": "Verified Firebase user accepted. PostgreSQL sync is not wired yet.",
  "error": null
}
```

### Secure Backend Flow

- frontend authenticates user with Firebase
- frontend sends bearer token + uid to backend
- backend verifies Firebase token
- backend extracts verified uid
- backend compares request uid with verified uid (optional)
- backend returns success response

### Why Verification Is Required

The backend must not trust the raw `uid` from the request body alone. The verified Firebase ID token is the source of truth.

### Firebase Credential Configuration

The backend reads the Firebase Admin service-account path from:

```properties
firebase.credentials.path=${FIREBASE_CREDENTIALS:/absolute/path/to/service-account.json}
```

Configured in:
- `backend/backend/src/main/resources/application.properties`

### Lazy Firebase Initialization

- backend starts without Firebase Admin credentials
- Firebase Admin initializes only when `/api/auth/sync` is called
- if credentials are missing or invalid, `/api/auth/sync` fails

### Recommended User Table Shape

```sql
users (
  uid text primary key,
  email text,
  display_name text,
  photo_url text,
  auth_provider text,
  created_at timestamp,
  updated_at timestamp
)
```
