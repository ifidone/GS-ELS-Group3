# Frontend

## Install And Run

To install dependencies and start the frontend:

```bash
cd frontend
npm install
npm start
```

Once the server is running, open your browser and navigate to `http://localhost:4200/`.

## Additional Frontend Documentation

For the specific frontend authentication, backend communication, and route protection flow, go through these files:

- `frontend/FIREBASE_AUTH_AND_BACKEND_FLOW.md`
- `frontend/ROUTE_PROTECTION_FLOW.md`

## Frontend Folder Structure

The main frontend code lives in `frontend/src/app`.

Important folders and files:

- `src/app/app.ts`
  Root Angular app component
- `src/app/app.routes.ts`
  Main route configuration
- `src/app/core/auth.facade.ts`
  Shared Firebase authentication logic and backend auth sync
- `src/app/core/auth.guards.ts`
  Route guards for protected and public-only routes
- `src/app/pages/auth-page/`
  Public authentication page for email login, signup, and Google sign-in
- `src/app/pages/dashboard-shell/`
  Protected dashboard layout shell with navbar, user info, logout, and child route outlet
- `src/app/pages/dashboard-home/`
  Default page rendered inside the dashboard shell
- `src/app/pages/calculator/`
  Protected calculator feature page

## Route Structure

The frontend uses this route model:

```text
/auth
  public authentication page

/dashboard
  protected layout shell
  default child page: dashboard home

/dashboard/calculator
  protected calculator page inside dashboard shell
```

## Access Flow

Access is controlled by Angular route guards and Firebase Authentication.

The behavior is:

- unauthenticated users trying to access `/dashboard` or its child routes are redirected to `/auth`
- authenticated users visiting `/auth` are redirected to `/dashboard`
- authenticated users can move between protected dashboard pages without logging in again

## Authentication Flow

The frontend uses Firebase Auth for:

- email login
- email signup
- Google login

Once Firebase authenticates the user:

1. the frontend receives the authenticated Firebase user
2. the frontend gets a Firebase ID token
3. the frontend sends a request to the backend auth sync endpoint

Current sync contract:

```text
POST /api/auth/sync
Authorization: Bearer <firebase_token>
body: { uid: "<firebase_uid>" }
```

## Frontend To Backend Communication

The frontend communicates with the backend in two main ways:

### 1. Auth Sync

Handled from:

- `src/app/core/auth.facade.ts`

Purpose:

- sync authenticated Firebase identity with the backend
- let backend use Firebase `uid` as the canonical user identifier

### 2. Feature APIs

Example:

- `src/app/pages/calculator/calculator.ts`

Current calculator API:

```text
POST /api/calculator/project
```

This is a normal feature request and is separate from Firebase authentication logic.

### 3. Mutual Fund List

The calculator loads the full mutual fund list from:

```text
GET /api/funds
```

If the request fails, the UI falls back to a small default list.

### 4. Chat History

The chatbot stores the last 10 messages per user in `localStorage` and sends them with each request:

```json
{
  "message": "Hello",
  "uid": "firebase_uid",
  "history": [
    { "role": "user", "content": "Earlier question" },
    { "role": "assistant", "content": "Earlier reply" }
  ]
}
```

## Separation Of Responsibility

The frontend is structured so that authentication logic is separate from feature pages.

That means:

- `auth-page` handles sign-in and signup UI
- `auth.facade` handles Firebase auth actions and backend sync
- `auth.guards` protect routes
- `dashboard-shell` provides the protected layout
- feature pages like `calculator` do not manage authentication directly

## Dashboard Shell vs Dashboard Home

These two components have different responsibilities:

- `dashboard-shell`
  shared protected layout, navbar, profile display, logout, child route outlet
- `dashboard-home`
  the default content page shown at `/dashboard`

Simple model:

```text
dashboard-shell = frame/layout
dashboard-home = content inside that frame
```

This allows future protected pages like portfolio, profile, and history to reuse the same dashboard shell.
