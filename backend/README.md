# GS-ELS-Group3

## Backend Features

- Spring Boot REST APIs for auth sync, calculator projection, and saved calculations.
- New `/api/chat` endpoint that uses Gemini 2.5 Flash with MCP tool calling.
- MCP client connects to the FastMCP server for tool execution.

## Backend Database Setup

The backend now uses PostgreSQL with Flyway migrations.

Key files:
- `backend/backend/pom.xml` includes `spring-boot-starter-jdbc`, `flyway-core`, `flyway-database-postgresql`, and `postgresql`.
- `backend/backend/src/main/resources/application.properties` contains the datasource and Flyway configuration.
- `backend/backend/src/main/resources/db/migration/V1__create_users_table.sql` creates the `users` table.
- `backend/backend/src/main/resources/db/migration/V2__create_saved_calculations_table.sql` creates the `saved_calculations` table.

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

## Chat API (Gemini + MCP)

```
POST /api/chat
```

Request:
```json
{
  "message": "Show my saved calculations",
  "uid": "firebase_uid"
}
```

Response:
```json
{
  "reply": "..."
}
```

Configuration (in `backend/backend/src/main/resources/application.properties`):
- `spring.ai.google.genai.api-key` for Gemini API key (fallback env `ApiKey`).
- `spring.ai.google.genai.chat.options.model` for the model name.
- `spring.ai.mcp.client.streamable-http.connections.mcp.url` for MCP server base URL (fallback env `MCP_SERVER_ORIGIN`).
- `spring.ai.mcp.client.streamable-http.connections.mcp.endpoint` for MCP endpoint path (fallback env `MCP_SERVER_ENDPOINT`).

## Saved Calculations API

Authenticated endpoints for storing user calculation history:

```
GET    /api/calculations
POST   /api/calculations
PUT    /api/calculations/{id}
DELETE /api/calculations/{id}
```

All saved-calculation requests require:

```
Authorization: Bearer <firebase_id_token>
```

Request body for `POST` and `PUT`:

```json
{
  "ticker": "VFIAX",
  "initialInvestment": 10000,
  "years": 10,
  "beta": 1.2,
  "expectedReturn": 0.08,
  "futureValue": 21500
}
```

Notes:
- Ownership is enforced via verified Firebase `uid`.
- `users` is insert-if-missing on save to keep referential integrity intact.

## Calculator Projection API

```
POST /api/calculator/project
```

Request:

```json
{
  "ticker": "VFIAX",
  "initialInvestment": 10000,
  "years": 10
}
```

## Mutual Funds API

```
GET /api/funds
GET /api/funds/{ticker}
```

Seed data is loaded via Flyway migration `V4__create_mutual_funds_table.sql`.

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
- first-time user insert into PostgreSQL (`users` table)

Not implemented yet:
- user updates on subsequent logins (only insert-if-missing is implemented)
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
  "syncStatus": "created",
  "message": "Verified Firebase user created in PostgreSQL.",
  "error": null
}
```

### Secure Backend Flow

- frontend authenticates user with Firebase
- frontend sends bearer token + uid to backend
- backend verifies Firebase token
- backend extracts verified uid
- backend compares request uid with verified uid (optional)
- backend inserts the user row if missing
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

## Tests

Unit tests (default):

```bash
cd backend/backend
./mvnw test
```

Integration tests (explicit gate, real DB):

```bash
RUN_INTEGRATION=true DB_URL=... DB_USERNAME=... DB_PASSWORD=... \
./mvnw -Dspring.profiles.active=integration test
```

Integration profile config: `backend/backend/src/test/resources/application-integration.properties`
