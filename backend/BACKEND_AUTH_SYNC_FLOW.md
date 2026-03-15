# Backend Auth Sync Flow

## Purpose

This document describes how the backend should handle authentication data coming from the frontend after Firebase login.

The backend should use Firebase only as the identity provider and should use PostgreSQL for application data.

## Current Status

What is already implemented:

- `/api/auth/sync` endpoint exists
- bearer token extraction exists
- Firebase ID token verification logic exists
- verified Firebase user data is returned by the endpoint
- Firebase Admin initialization is lazy and happens only when `/api/auth/sync` is called

What is not implemented yet:

- PostgreSQL user upsert
- database-backed user sync
- protected backend resource ownership using PostgreSQL user records

At the moment, the backend verifies the Firebase user and returns a `"pending"` sync status.

## Main Endpoint

The frontend will call:

```text
POST /api/auth/sync
```

## Request Contract

The frontend sends:

- `Authorization: Bearer <firebase_id_token>`
- JSON body containing:

```json
{
  "uid": "firebase_uid"
}
```

## Current Response Shape

The current backend response includes verified Firebase identity data and a pending sync status.

Conceptually:

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

## Important Rule

The backend must not trust the raw `uid` from the request body by itself.

The trusted identity must come from the verified Firebase ID token.

That means:

1. read the bearer token from the `Authorization` header
2. verify the Firebase token using Firebase Admin SDK
3. extract the `uid` from the verified token
4. use that verified `uid` as the canonical user identifier

The request body `uid` can be used only as an optional consistency check.

## Secure Backend Flow

```text
Frontend authenticates user with Firebase
  ->
Frontend sends bearer token + uid to backend
  ->
Backend verifies Firebase token
  ->
Backend extracts verified uid
  ->
Backend compares request uid with verified uid if desired
  ->
Backend returns success response
```

Current implemented flow ends there.

Future flow will add:

```text
verified user
  ->
PostgreSQL upsert
  ->
user-owned application data
```

## Why Verification Is Required

If the backend trusts only:

```json
{ "uid": "some-user-id" }
```

then any client can fake another user's identity.

The Firebase ID token is required because it proves:

- the user was authenticated by Firebase
- the token belongs to this Firebase project
- the token has not expired
- the `uid` was issued by Firebase

## Backend Responsibilities

When `/api/auth/sync` is called, the backend should:

1. validate that a bearer token exists
2. verify the token with Firebase Admin SDK
3. extract the verified `uid`
4. optionally compare verified `uid` with request body `uid`
5. return a success response with verified user data

Later, PostgreSQL integration should extend this to:

6. create the PostgreSQL user row if missing
7. update the user row if it already exists

## Recommended PostgreSQL Ownership

Firebase Auth should own:

- `uid`
- `email`
- `displayName`
- `photoURL`
- auth provider info
- password hash for email/password accounts

PostgreSQL should own:

- application user row keyed by `uid`
- calculator history
- portfolios
- chat history
- profile preferences
- all application-specific business data

## Firebase Credential Configuration

The backend now reads the Firebase Admin service-account path from:

```properties
firebase.credentials.path=${FIREBASE_CREDENTIALS:/absolute/path/to/service-account.json}
```

This is configured in:

- `backend/backend/src/main/resources/application.properties`

Important:

- the backend does not use the frontend Firebase client config
- the backend needs a private Firebase Admin service account JSON path
- the fallback path is only a placeholder

## Lazy Firebase Initialization

Firebase Admin is not initialized at backend startup anymore.

Current behavior:

- backend starts normally without real Firebase credentials
- Firebase Admin initializes only when `/api/auth/sync` is called
- if credentials are missing or invalid at that moment, `/api/auth/sync` fails

This allows the rest of the backend to run while Firebase Admin credentials are still pending.

## Recommended User Table Shape

Example:

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

## What The Backend Should Read From The Verified Token

After verification, the backend should treat the decoded Firebase token as the source of truth.

Useful fields typically include:

- `uid`
- `email`
- `name`
- `picture`
- provider-related claims

## How Data Should Be Handled

The backend flow should be:

```text
verify token
  ->
extract trusted user identity
  ->
return verified identity for now
```

Later, this should become:

```text
verify token
  ->
extract trusted user identity
  ->
upsert users.uid in PostgreSQL
  ->
associate all future records with that uid
```

So all future application data should link back to the same Firebase `uid`.

## Endpoint Outcome

If verification succeeds:

- backend knows the authenticated user identity
- backend returns verified user information
- backend is ready for later PostgreSQL sync integration

If verification fails:

- backend must reject the request
- backend must not create or update user records later when PostgreSQL sync is added

## Final Rule

Use this model consistently:

- frontend sends Firebase token and `uid`
- backend trusts only the verified token
- backend extracts trusted `uid`
- PostgreSQL will later use Firebase `uid` as the primary key for user ownership
