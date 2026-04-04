# GS-ELS-Group3

## Backend Features

- Spring Boot REST APIs for auth sync, calculator projection, and saved calculations.
- Monte Carlo simulation endpoint for portfolio growth probability analysis.
- New `/api/chat` endpoint that uses Gemini 2.5 Flash with MCP tool calling.
- MCP client connects to the FastMCP server for tool execution.

## Backend Database Setup

The backend now uses PostgreSQL with Flyway migrations.

Key files:
- `backend/backend/pom.xml` includes `spring-boot-starter-jdbc`, `flyway-core`, `flyway-database-postgresql`, and `postgresql`.
- `backend/backend/src/main/resources/application.properties` contains the datasource and Flyway configuration.
- `backend/backend/src/main/resources/db/migration/V1__create_users_table.sql` creates the `users` table.
- `backend/backend/src/main/resources/db/migration/V2__create_saved_calculations_table.sql` creates the `saved_calculations` table.
- `backend/backend/src/main/resources/db/migration/V11__add_time_series_to_saved_calculations.sql` adds `time_series` JSONB for yearly values.

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

## Monte Carlo API

```
POST /api/monte-carlo
```

Request:
```json
{
  "ticker": "VFIAX",
  "principal": 10000,
  "timeYears": 10,
  "goalAmount": 25000,
  "nSimulations": 1000
}
```

Notes:
- `goalAmount` and `nSimulations` are optional.
- The response includes the simulated distribution and probability metrics returned by the backend service.

## Saved Calculations API

Endpoints for storing user calculation history:

```
GET    /api/calculations
POST   /api/calculations
PUT    /api/calculations/{id}
DELETE /api/calculations/{id}
PATCH  /api/saved-calculations/{id}
DELETE /api/saved-calculations/{id}
GET    /api/saved-calculations
```

Auth (optional for list and create):

```
Authorization: Bearer <firebase_id_token>
```

`GET /api/calculations` and `POST /api/calculations` can accept a body with `uid` when no auth header is provided:

```json
{
  "uid": "hwOjPjBhpMPHmO9nJ1USmlP03au2"
}
```

Request body for `POST` and `PUT`:

```json
{
  "uid": "hwOjPjBhpMPHmO9nJ1USmlP03au2",
  "name": "Custom label",
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
- `name` is stored with each saved calculation. If omitted on create/update, the ticker is used.
- Responses include `timeSeries` as a map of year -> value from 0..20 (CAPM-based).

`PATCH /api/saved-calculations/{id}` (uid in body):

```json
{
  "uid": "hwOjPjBhpMPHmO9nJ1USmlP03au2",
  "name": "Retirement S&P 500",
  "ticker": "VFIAX",
  "initialInvestment": 12000,
  "years": 12
}
```

If ticker/initialInvestment/years change, the backend recomputes beta/expectedReturn/futureValue.
Time series is recomputed on create and on update when those inputs change.

`GET /api/saved-calculations` (uid in body, optional `name` query for search):

```json
{
  "uid": "hwOjPjBhpMPHmO9nJ1USmlP03au2"
}
```

Example search:

```
GET /api/saved-calculations?name=Retirement
```

`DELETE /api/saved-calculations/{id}` (uid in body):

```json
{
  "uid": "hwOjPjBhpMPHmO9nJ1USmlP03au2"
}
```

## Portfolios API

These endpoints use `uid` from the JSON request body (no Firebase verification).

```
GET    /api/portfolios?page=0&size=20&sort=createdAt,desc
POST   /api/portfolios
GET    /api/portfolios/{name}
PATCH  /api/portfolios/{name}
DELETE /api/portfolios/{name}
GET    /api/portfolios/{name}/items
PUT    /api/portfolios/{name}/items/{calculationId}
DELETE /api/portfolios/{name}/items/{calculationId}
GET    /api/portfolios/{name}/available-calculations
```

Base URL: `http://localhost:8080`

UID body (send with all portfolio endpoints):

```json
{
  "uid": "hwOjPjBhpMPHmO9nJ1USmlP03au2"
}
```

Create portfolio body:

```json
{
  "uid": "hwOjPjBhpMPHmO9nJ1USmlP03au2",
  "name": "Aggressive Growth",
  "description": "High-risk growth bucket"
}
```

Update portfolio body:

```json
{
  "uid": "hwOjPjBhpMPHmO9nJ1USmlP03au2",
  "name": "Retirement 2045 (updated)",
  "description": "Updated description"
}
```

Delete portfolio response:
- `200 OK` if deleted
- `404` if not found

Add calculation to portfolio response:
- `200 OK` if linked (or already linked)

Remove calculation from portfolio response:
- `200 OK` if removed
- `404` with message `Calculation does not exist for this user.` when the calculation is not owned by the uid
- `404` with message `Calculation is not linked to this portfolio.` when the link does not exist

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
