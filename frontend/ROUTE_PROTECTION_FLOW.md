# Frontend Route Protection Flow

## Purpose

This document explains how Angular route protection should work in this frontend, how it relates to Firebase Authentication, and what is currently implemented versus what should be added.

## Short Answer

Angular should protect pages using route guards.

The intended flow is:

```text
User tries to open a page
  ->
Angular router checks auth guard
  ->
Guard checks Firebase auth state
  ->
If authenticated, allow navigation
  ->
If not authenticated, redirect to login page
```

## Current State of This Frontend

Route protection is not currently implemented.

What exists now:

- only one route is defined
- there is no `canActivate`
- there is no auth guard
- there is no dedicated login page route
- authentication is handled inside the calculator page using a modal

Current route file:

- [app.routes.ts](/Users/dipesh/Desktop/GoldmanSachs/GS-ELS-Group3/frontend/src/app/app.routes.ts)

Current behavior is closer to:

```text
User opens app
  ->
Calculator page loads
  ->
Component checks Firebase auth state
  ->
If no user, auth modal is shown
```

This is component-level UI gating, not router-level page protection.

## Difference Between UI Gating and Route Protection

### UI Gating

The component loads first, then decides what to show.

Example:

- page opens
- auth modal appears
- unauthenticated user still reached the route

### Route Protection

The router decides before opening the page.

Example:

- user tries `/dashboard`
- guard runs first
- unauthenticated user is redirected to `/login`
- protected page never opens

## Recommended Architecture

The frontend should separate public routes and protected routes.

### Public Routes

Examples:

- `/login`
- `/signup`
- landing page

### Protected Routes

Examples:

- `/dashboard`
- `/history`
- `/portfolio`
- `/profile`
- `/chat`

Protected routes should use Angular auth guards.

## Recommended Route Flow

```text
Browser navigates to protected route
  ->
Angular router runs auth guard
  ->
Guard checks Firebase current user or auth state
  ->
If user exists:
   allow navigation
Else:
   redirect to /login
```

## What the Guard Should Check

The guard should check whether Firebase has an authenticated user.

Simple rule:

- Firebase user exists -> allow route
- Firebase user is null -> block route and redirect

## Conceptual Example

In the route configuration:

```ts
{
  path: 'dashboard',
  component: DashboardComponent,
  canActivate: [authGuard]
}
```

Then the guard logic is conceptually:

```text
if authenticated:
  return true
else:
  redirect to /login
  return false
```

## What Happens After Login

After successful Firebase authentication:

```text
Firebase user exists
  ->
auth guard passes
  ->
user can access protected pages
```

## What Happens After Logout

After logout:

```text
Firebase user becomes null
  ->
auth guard fails
  ->
user is redirected away from protected pages
```

## Important Security Boundary

Frontend route protection is necessary for navigation and user experience, but it is not enough for backend security.

Use this split:

- Angular auth guard controls page access
- Backend token verification controls API/data access

That means:

- the guard answers: "Can the user open this page?"
- the backend answers: "Can the user access this data?"

## Relationship to Firebase

Firebase Auth should be the source of truth for whether a user is authenticated on the frontend.

So Angular route protection should depend on Firebase auth state.

Recommended high-level flow:

```text
User signs in with Firebase
  ->
Firebase session becomes active
  ->
Angular auth guard sees authenticated user
  ->
Protected routes are allowed
```

## Recommended Future Structure for This Project

Instead of one calculator page with an auth modal, the project should move toward:

```text
/login        -> public
/dashboard    -> protected
/history      -> protected
/portfolio    -> protected
/profile      -> protected
```

And each protected route should use an auth guard.

## Final Rule

For this frontend:

- do not rely on component modals alone for page protection
- use Angular route guards for protected pages
- use Firebase Auth to determine if the user is signed in
- still verify Firebase ID tokens on the backend for all protected APIs
