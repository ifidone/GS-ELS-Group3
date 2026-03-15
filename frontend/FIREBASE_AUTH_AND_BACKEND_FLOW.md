# Frontend Firebase Auth and Backend Data Flow

## Purpose

This document explains how the frontend should use Firebase Authentication, how it should communicate with the backend, and where user-related data should be stored.

The intended design is:

```text
Email signup/login / Google login / future social login
  ->
Firebase Auth
  ->
one Firebase uid per Firebase user account
  ->
frontend sends verified token
  ->
backend verifies token
  ->
backend uses Firebase uid as universal user key
```

## Core Rule

Firebase is used for authentication only.

PostgreSQL is used for application data.

That means:

- Firebase Auth stores the identity of the user.
- The backend stores business data in PostgreSQL.
- The frontend should not use Firestore for app data in the target design.

## What Firebase Stores

For email/password and Google sign-in, Firebase Auth stores identity data in the cloud.

Typical Firebase Auth data includes:

- `uid`
- `email`
- `displayName`
- `photoURL`
- auth provider information
- password hash for email/password accounts

Important:

- Firebase does not store plaintext passwords.
- The password is managed by Firebase Auth, not by this frontend and not by the backend.
- The browser may keep local auth session state and tokens so the user stays signed in, but the source of truth is Firebase Auth in the cloud.

## What PostgreSQL Should Store

All non-authentication application data should live in PostgreSQL through the backend.

Examples:

- user row keyed by Firebase `uid`
- calculator history
- portfolios
- chat history
- preferences
- any future app-specific records

Recommended `users` table shape:

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

## Why Frontend Should Not Send Only `uid`

Sending only a raw `uid` is not enough.

Example of the insecure version:

```json
{
  "uid": "some-other-users-uid"
}
```

If the backend trusts that directly, anyone can impersonate another user by sending a fake `uid`.

The backend must only trust a `uid` that comes from a verified Firebase token.

## Correct Frontend to Backend Authentication Flow

The secure flow is:

```text
User logs in with Firebase
  ->
Firebase authenticates user
  ->
Frontend gets Firebase ID token
  ->
Frontend sends token to backend
  ->
Backend verifies token
  ->
Backend extracts uid from verified token
  ->
Backend checks PostgreSQL
  ->
If user exists, update as needed
  ->
If user does not exist, create user row
```

## What Is a Firebase ID Token

A Firebase ID token is proof that Firebase authenticated the user.

The backend uses that token to verify:

- the token is real
- it was issued by Firebase
- it belongs to the correct Firebase project
- it is not expired

After verification, the backend can safely read the Firebase `uid`.

## What the Frontend Should Send

The frontend should send the Firebase ID token to the backend, usually in the `Authorization` header.

Example:

```http
Authorization: Bearer <firebase_id_token>
```

The frontend should not send only:

```json
{ "uid": "abc123" }
```

## Backend Responsibility

After receiving the Firebase ID token, the backend should:

1. Verify the token using Firebase Admin SDK.
2. Extract the trusted Firebase `uid`.
3. Use that `uid` as the PostgreSQL primary key.
4. Create the user row if it does not exist.
5. Update existing user metadata if needed.
6. Store and fetch all application data using that `uid`.

## Frontend Flow by Login Method

All login methods should converge to the same backend flow.

### Email Signup

```text
User signs up with email + password
  ->
Firebase creates account
  ->
Firebase returns authenticated user
  ->
Frontend gets ID token
  ->
Frontend sends token to backend
  ->
Backend verifies token and extracts uid
```

### Email Login

```text
User logs in with email + password
  ->
Firebase authenticates account
  ->
Frontend gets ID token
  ->
Frontend sends token to backend
  ->
Backend verifies token and extracts uid
```

### Google Login

```text
User signs in with Google
  ->
Firebase authenticates through Google provider
  ->
Frontend gets ID token
  ->
Frontend sends token to backend
  ->
Backend verifies token and extracts uid
```

### Future Social Login

The same pattern should apply to future providers:

- Google
- Apple
- GitHub
- Facebook
- any other supported provider

The backend should still rely on the verified Firebase token and extracted `uid`, not provider-specific user identifiers.

## Current Project Context

In the current frontend codebase:

- Firebase Auth is already configured.
- Firestore is currently used for user profile metadata and projection history.

Target architecture change:

- keep Firebase Auth
- remove Firestore usage for app data
- move application data persistence to backend + PostgreSQL

## Final Rule of Ownership

Use this split consistently:

- Firebase Auth owns authentication identity.
- Backend owns authorization, persistence, and business data.
- PostgreSQL is the system of record for application data.
- Firebase `uid` is the universal user identifier across all login methods.
