# CentralAuth

CentralAuth is a full-stack centralized authentication and identity platform. It provides email/password authentication, email verification, password reset, JWT access tokens, refresh-token rotation, registered client applications, a central-login authorization-code flow, front-channel logout propagation, admin user/client management, structured auth logs, and optional Kafka-backed audit logs.

## Contents

- [Repository Layout](#repository-layout)
- [Architecture](#architecture)
- [Local Setup](#local-setup)
- [Configuration](#configuration)
- [Authentication Flows](#authentication-flows)
- [API Reference](#api-reference)
- [Redis Usage](#redis-usage)
- [Kafka And Audit Logs](#kafka-and-audit-logs)
- [Structured Logging](#structured-logging)
- [Frontend Notes](#frontend-notes)
- [Testing](#testing)
- [Current Limitations](#current-limitations)

## Repository Layout

```text
.
|-- CentralAuth-be/       Spring Boot 3.5 backend, Java 21, MyBatis, Flyway
|-- CentralAuth-fe/       React 19, TypeScript, Vite, Ant Design
|-- docker-compose.yml    PostgreSQL, Redis, Kafka, Kafka UI
|-- docs/                 Project notes and generated documentation
`-- README.md
```

## Architecture

### Runtime Components

```text
Browser
  |
  | Vite dev proxy
  v
React frontend
  |
  | REST JSON, Bearer JWT
  v
Spring Boot backend
  |-- PostgreSQL: users, roles, clients, refresh token hashes, audit logs
  |-- Redis: OTPs, reset tokens, login throttles, login state, auth codes, token revocation
  `-- Kafka: optional auth/admin event transport for audit persistence
```

### Backend Responsibilities

- `auth`: signup, signin, verification, password reset, logout, central-login flow, structured auth logs.
- `security`: stateless Spring Security, JWT validation, token-use checks, token revocation.
- `client`: registered client validation, redirect/logout URI validation, demo client bootstrap.
- `admin`: user status management, client application management, admin bootstrap.
- `event`: Spring application events and optional Kafka publisher.
- `audit`: optional Kafka consumer and audit-log query API.
- `common`: response wrapper, global exception handling, localization, client IP resolution.

### Frontend Responsibilities

- Auth routes: `/signin`, `/signup`, `/verify-email`, `/forgot-password`, `/reset-password`.
- Protected routes: `/dashboard`, `/profile`.
- Demo client routes: `/demo/projects/*`, `/demo/reports/*`.
- Auth state: `AuthSessionContext` stores access and refresh tokens in `localStorage`, restores or refreshes the current user through `/api/v1/auth/me` and `/api/v1/auth/refresh`, schedules proactive refresh, exposes operation state, and clears invalid sessions.
- Protected routing: unauthenticated users are redirected to `/signin` with return-location state.
- Logout propagation: CentralAuth loads registered client logout URIs in hidden iframes so demo clients can clear browser-side session state.

## Local Setup

### Prerequisites

- Java 21
- Node.js and npm
- Docker Desktop or Docker Engine

### Start Infrastructure

Start the services needed for normal auth development:

```sh
docker compose up -d postgres redis
```

Start Kafka only when testing the event/audit pipeline:

```sh
docker compose up -d kafka kafka-ui
```

Local service ports:


| Service    | URL                     |
| ---------- | ----------------------- |
| Backend    | `http://localhost:8080` |
| Frontend   | `http://localhost:5173` |
| PostgreSQL | `localhost:5432`        |
| Redis      | `localhost:6379`        |
| Kafka      | `localhost:9092`        |
| Kafka UI   | `http://localhost:8081` |


### Run Backend

Windows:

```sh
cd CentralAuth-be
.\mvnw.cmd spring-boot:run
```

macOS/Linux:

```sh
cd CentralAuth-be
./mvnw spring-boot:run
```

### Run Frontend

```sh
cd CentralAuth-fe
npm install
npm run dev
```

Vite proxies `/api` to `http://localhost:8080`.

## Authentication Flows

### Signup And Email Verification

1. `POST /api/v1/auth/signup` creates a user with normalized lowercase email.
2. Passwords are hashed with `BCryptPasswordEncoder`.
3. The user receives `ROLE_USER` and starts with `accountStatus=UNVERIFIED`.
4. A six-digit OTP is stored in Redis under `email-verification:<email>`.
5. `POST /api/v1/auth/verify-email` validates the OTP, consumes it, and activates the account.
6. `POST /api/v1/auth/resend-verification-otp` issues a new OTP when the Redis cooldown allows it.

Signup returns tokens immediately, but protected backend routes still reject the user until email verification activates the account.

For local development, there is no email delivery adapter yet. Inspect Redis when you need a generated OTP:

```sh
docker exec centralauth-redis redis-cli GET email-verification:user@example.com
```

### Signin And Login Protection

1. `POST /api/v1/auth/signin` authenticates active users only.
2. Login attempts are counted in Redis per email and per client IP.
3. Too many attempts in the rate window returns `429 Too Many Requests`.
4. Too many failures creates temporary email/IP lock keys in Redis.
5. Successful signin returns:
  - CentralAuth JWT access token
  - opaque refresh token
  - current user profile

JWTs include issuer, subject user ID, email, roles, token use, issued-at, and expiry. Client-scoped JWTs also include the client audience.

### Refresh Tokens

Refresh tokens are one-time use. A successful refresh revokes the presented refresh token and returns a replacement refresh token with a new access token. Reuse of a revoked refresh token for a known user invalidates that user's remaining active refresh tokens.

### Password Reset

1. `POST /api/v1/auth/forgot-password` accepts an email and silently succeeds.
2. If the account exists and is active, a random reset token is stored in Redis as `password-reset:<token>`.
3. `POST /api/v1/auth/reset-password` consumes the token, updates the BCrypt password hash, and revokes all active refresh tokens.

For local development, inspect Redis when you need a generated reset token:

```sh
docker exec centralauth-redis redis-cli --scan --pattern "password-reset:*"
```

### Central Login For Client Apps

CentralAuth implements an OAuth-style first-party client login flow:

1. A client sends the browser to `/signin?client_id=...&redirect_uri=...&state=...`.
2. The frontend calls `GET /api/v1/auth/central-login/context`.
3. The backend validates the active client and exact redirect URI, then stores a short-lived `loginState` in Redis.
4. If the user is not signed in, the frontend posts credentials to `POST /api/v1/auth/central-login`.
5. If the user already has a CentralAuth session, the frontend posts to `POST /api/v1/auth/central-login/continue`.
6. The backend consumes `loginState`, stores a one-time authorization code in Redis, and returns a redirect URL.
7. The client callback validates browser state and exchanges the code through `POST /api/v1/auth/central-login/token`.
8. The backend returns a client-scoped JWT with `token_use=client_access` and `aud=<clientId>`.

Client-scoped JWTs can call `/api/v1/auth/me`; they do not grant admin access.

### Logout

- `POST /api/v1/auth/logout` revokes one refresh token and blacklists the current CentralAuth access token until it expires.
- `POST /api/v1/auth/logout-all-devices` revokes all active refresh tokens, blacklists the current token, and stores a Redis cutoff so older CentralAuth access tokens are rejected.
- Logout responses include active registered client logout URIs.
- The frontend loads those logout URIs in hidden iframes to propagate front-channel logout to demo clients.

## API Reference

All API responses use the common wrapper unless noted:

```json
{
  "success": true,
  "message": "Message",
  "data": {},
  "timestamp": "2026-06-06T00:00:00Z"
}
```

Backend errors are centralized through `@RestControllerAdvice` and return the same wrapper with `success=false`.

### Public Endpoints


| Method | Path                                   | Body / Query                                                                   | Response Data                                                  | Purpose                                       |
| ------ | -------------------------------------- | ------------------------------------------------------------------------------ | -------------------------------------------------------------- | --------------------------------------------- |
| `GET`  | `/api/v1/health`                       | none                                                                           | health payload                                                 | Basic health response.                        |
| `POST` | `/api/v1/auth/signup`                  | `email`, `password`, optional `displayName`                                    | `token`, `refreshToken`, `user`                                | Create an unverified user and issue OTP.      |
| `POST` | `/api/v1/auth/signin`                  | `email`, `password`                                                            | `token`, `refreshToken`, `user`                                | Sign in an active user.                       |
| `POST` | `/api/v1/auth/refresh`                 | `refreshToken`                                                                 | `token`, `refreshToken`, `user`                                | Rotate a valid refresh token and issue a new access token. |
| `POST` | `/api/v1/auth/verify-email`            | `email`, `otp`                                                                 | `null`                                                         | Verify pending user email.                    |
| `POST` | `/api/v1/auth/resend-verification-otp` | `email`                                                                        | `resendCooldownSeconds`                                        | Resend verification OTP.                      |
| `POST` | `/api/v1/auth/forgot-password`         | `email`                                                                        | `null`                                                         | Request password reset.                       |
| `POST` | `/api/v1/auth/reset-password`          | `token`, `newPassword`                                                         | `null`                                                         | Reset password with opaque token.             |
| `GET`  | `/api/v1/auth/central-login/context`   | `client_id`, `redirect_uri`, optional `state`                                  | `clientId`, `clientName`, `redirectUri`, `state`, `loginState` | Validate client login request.                |
| `POST` | `/api/v1/auth/central-login`           | `email`, `password`, `clientId`, `redirectUri`, optional `state`, `loginState` | redirect data plus `auth`                                      | Sign in during central login.                 |
| `POST` | `/api/v1/auth/central-login/token`     | `code`, `clientId`, `redirectUri`                                              | `token`, `user`                                                | Exchange one-time code for client-scoped JWT. |


### Authenticated User Endpoints

These endpoints require `Authorization: Bearer <token>`.


| Method | Path                                  | Body                                                      | Response Data                                 | Purpose                                                        |
| ------ | ------------------------------------- | --------------------------------------------------------- | --------------------------------------------- | -------------------------------------------------------------- |
| `GET`  | `/api/v1/auth/me`                     | none                                                      | `id`, `email`, `displayName`, `emailVerified` | Return current user for CentralAuth or client-scoped JWTs.     |
| `GET`  | `/api/v1/users/me`                    | none                                                      | `id`, `email`, `displayName`, `emailVerified` | Return current user for authenticated CentralAuth sessions.    |
| `POST` | `/api/v1/auth/central-login/continue` | `clientId`, `redirectUri`, optional `state`, `loginState` | redirect data                                 | Continue client login from current CentralAuth session.        |
| `POST` | `/api/v1/auth/logout`                 | `refreshToken`                                            | `logoutUris`                                  | Revoke current refresh token and current access token.         |
| `POST` | `/api/v1/auth/logout-all-devices`     | none                                                      | `logoutUris`                                  | Revoke all refresh tokens and older CentralAuth access tokens. |


### Admin Endpoints

Admin endpoints require a CentralAuth JWT with `ROLE_ADMIN`.


| Method  | Path                                      | Body / Query                                                                                | Purpose                                                                |
| ------- | ----------------------------------------- | ------------------------------------------------------------------------------------------- | ---------------------------------------------------------------------- |
| `GET`   | `/api/v1/admin/users`                     | optional `email`, `status`, `limit`                                                         | List users.                                                            |
| `GET`   | `/api/v1/admin/users/{id}`                | none                                                                                        | Get one user.                                                          |
| `PATCH` | `/api/v1/admin/users/{id}/status`         | `status`                                                                                    | Change user status to `ACTIVE`, `DISABLED`, `LOCKED`, or `UNVERIFIED`. |
| `GET`   | `/api/v1/admin/clients`                   | none                                                                                        | List client applications.                                              |
| `POST`  | `/api/v1/admin/clients`                   | `clientId`, `clientName`, `redirectUris`, `allowedOrigins`, `logoutUris`, optional `active` | Create client application.                                             |
| `PUT`   | `/api/v1/admin/clients/{clientId}`        | `clientName`, `redirectUris`, `allowedOrigins`, `logoutUris`, `active`                      | Replace client metadata.                                               |
| `PATCH` | `/api/v1/admin/clients/{clientId}/active` | `active`                                                                                    | Toggle client active flag.                                             |
| `GET`   | `/api/v1/audit-logs`                      | optional `eventType`, `userId`, `email`, `limit`                                            | Query recent audit logs.                                               |


## Redis Usage

Redis stores only transient security state. PostgreSQL remains the durable store for users, roles, client metadata, refresh-token hashes, and audit logs.


| Key Pattern                         | Producer                       | Purpose                                        |
| ----------------------------------- | ------------------------------ | ---------------------------------------------- |
| `email-verification:<email>`        | `EmailVerificationService`     | Six-digit verification OTP with TTL.           |
| `email-verification-resend:<email>` | `EmailVerificationService`     | OTP resend cooldown marker.                    |
| `password-reset:<token>`            | `PasswordResetService`         | Opaque password-reset token mapped to user ID. |
| `login-rate:email:<email>`          | `LoginAttemptService`          | Email login rate counter.                      |
| `login-rate:ip:<ip>`                | `LoginAttemptService`          | IP login rate counter.                         |
| `login-failure:email:<email>`       | `LoginAttemptService`          | Failed login counter by email.                 |
| `login-failure:ip:<ip>`             | `LoginAttemptService`          | Failed login counter by IP.                    |
| `login-lock:email:<email>`          | `LoginAttemptService`          | Temporary email lock marker.                   |
| `login-lock:ip:<ip>`                | `LoginAttemptService`          | Temporary IP lock marker.                      |
| `auth_state:<loginState>`           | `CentralLoginStateService`     | Central-login request context.                 |
| `auth_code:<code>`                  | `AuthorizationCodeService`     | One-time code exchange context.                |
| `jwt:blacklist:<hash>`              | `AccessTokenRevocationService` | Revoked access token until JWT expiry.         |
| `jwt:user-logout-after:<userId>`    | `AccessTokenRevocationService` | Logout-all-devices cutoff timestamp.           |


Useful local Redis commands:

```sh
docker exec centralauth-redis redis-cli --scan --pattern "email-verification:*"
docker exec centralauth-redis redis-cli --scan --pattern "password-reset:*"
docker exec centralauth-redis redis-cli TTL email-verification:user@example.com
```

## Kafka And Audit Logs

Kafka is optional and disabled by default so local development and tests can run without a broker.

Enable it with:

```properties
CENTRALAUTH_KAFKA_ENABLED=true
CENTRALAUTH_KAFKA_AUDIT_ENABLED=true
CENTRALAUTH_KAFKA_BOOTSTRAP_SERVERS=localhost:9092
```

### Published Events

When `CENTRALAUTH_KAFKA_ENABLED=true`, `AuthEventKafkaPublisher` listens to Spring application events and publishes them to Kafka.


| Event                         | Topic Property                                       | Default Topic                        | Kafka Key |
| ----------------------------- | ---------------------------------------------------- | ------------------------------------ | --------- |
| `UserRegisteredEvent`         | `centralauth.kafka.topics.user-registered`           | `auth.user.registered`               | user ID   |
| `UserVerifiedEvent`           | `centralauth.kafka.topics.user-verified`             | `auth.user.verified`                 | user ID   |
| `LoginSucceededEvent`         | `centralauth.kafka.topics.login-succeeded`           | `auth.user.login.succeeded`          | user ID   |
| `LoginFailedEvent`            | `centralauth.kafka.topics.login-failed`              | `auth.user.login.failed`             | email     |
| `UserLoggedOutEvent`          | `centralauth.kafka.topics.logout`                    | `auth.user.logout`                   | user ID   |
| `PasswordResetRequestedEvent` | `centralauth.kafka.topics.password-reset-requested`  | `auth.user.password.reset.requested` | user ID   |
| `PasswordChangedEvent`        | `centralauth.kafka.topics.password-changed`          | `auth.user.password.changed`         | user ID   |
| `AdminUserStatusChangedEvent` | `centralauth.kafka.topics.admin-user-status-changed` | `auth.admin.user.status.changed`     | user ID   |


Most events publish after the surrounding transaction commits. Login failures are published immediately because they may occur before a successful transaction exists.

### Audit Persistence

When Kafka and the audit listener are enabled, `AuditLogConsumer` consumes all auth/admin topics and `AuditLogService` persists:

- normalized event type
- user ID where available
- email where available
- client IP for login events
- reason for failed login or admin status transition
- event occurrence time
- Kafka topic and key
- serialized JSON payload

Admins query persisted audit records through `GET /api/v1/audit-logs`.

## Structured Logging

Auth actions also emit runtime structured logs through `StructuredAuthLogger` using SLF4J key-value logging. These logs are for observability and are separate from durable audit persistence.

Logged fields use stable names:

- `event`
- `outcome`
- `userId`
- `email`
- `clientIp`
- `clientId`
- `reason`
- `allDevices`

Logged auth events include registration, email verification, OTP resend, password reset request/change, login success/failure, logout, central-login code issuance, and client-token issuance.

Sensitive values are not logged:

- passwords
- OTP values
- password reset tokens
- JWTs
- refresh tokens
- authorization codes
- central-login state values

Example local log shape:

```text
event="auth.login" outcome="failure" email="user@example.com" clientIp="203.0.113.12" reason="INVALID_CREDENTIALS" auth action
```

## Frontend Notes

### Auth State

The frontend stores:

- access token under `centralauth.token`
- refresh token under `centralauth.refreshToken`
- demo client tokens and callback state under client-specific `centralauth.demo.*` keys

On app load, `AuthSessionContext` restores the current user with `/api/v1/auth/me`. If the access token is stale, it refreshes the session through `/api/v1/auth/refresh`, stores the returned token pair, and retries restore. It also schedules proactive refresh before JWT expiry. If restore or refresh fails, it clears stored auth state and exposes a localized session-expired message.

### Routes


| Route                      | Purpose                                            |
| -------------------------- | -------------------------------------------------- |
| `/signin`                  | Sign in, including central-login query parameters. |
| `/signup`                  | Create account.                                    |
| `/verify-email`            | Submit verification OTP and resend OTP.            |
| `/forgot-password`         | Request password reset.                            |
| `/reset-password`          | Submit reset token and new password.               |
| `/dashboard`               | Protected user dashboard.                          |
| `/profile`                 | Protected current-user profile page.               |
| `/demo/projects`           | Projects demo client public page.                  |
| `/demo/projects/protected` | Projects demo protected page.                      |
| `/demo/projects/callback`  | Projects demo central-login callback.              |
| `/demo/projects/logout`    | Projects demo front-channel logout endpoint.       |
| `/demo/reports`            | Reports demo client public page.                   |
| `/demo/reports/protected`  | Reports demo protected page.                       |
| `/demo/reports/callback`   | Reports demo central-login callback.               |
| `/demo/reports/logout`     | Reports demo front-channel logout endpoint.        |


### Demo Clients

When `CENTRALAUTH_DEMO_CLIENTS_ENABLED=true`, the backend registers:

- `projects-demo`
- `reports-demo`

Each demo client is registered for local origins `localhost` and `127.0.0.1` on ports `5173`, `5174`, and `5175`, with matching callback and logout URIs.

## Testing

### Backend

Windows:

```sh
cd CentralAuth-be
.\mvnw.cmd test
```

macOS/Linux:

```sh
cd CentralAuth-be
./mvnw test
```

Backend tests cover auth flows, JWT validation, JWT filter behavior, refresh-token persistence, admin users, admin clients, audit logs, Kafka toggles, structured auth logging, Flyway migrations, and demo client bootstrapping.

### Frontend

```sh
cd CentralAuth-fe
npm run lint
npm run test
```

Frontend tests cover auth session state and demo client helpers.

### Manual Refresh Session Check

Start infrastructure and the backend:

```sh
docker compose up -d postgres redis
cd CentralAuth-be
.\mvnw.cmd spring-boot:run
```

For a faster proactive-refresh check, start or restart the backend with `CENTRALAUTH_JWT_EXPIRES_IN_SECONDS=120`.

In another terminal, start the frontend:

```sh
cd CentralAuth-fe
npm run dev
```

Manual checks:

1. Sign in with an active verified user.
2. Confirm `localStorage` contains `centralauth.token` and `centralauth.refreshToken`.
3. Confirm the backend is running with `CENTRALAUTH_JWT_EXPIRES_IN_SECONDS=120`.
4. Wait for proactive refresh.
5. Confirm both stored token values changed without redirecting to `/signin`.
6. Use logout and confirm the rotated refresh token no longer refreshes.

### Browser E2E

The Playwright demo flow expects:

- backend running on `http://localhost:8080`
- frontend running on `http://localhost:5173`
- Redis available to the backend
- demo clients enabled
- an active verified user in `E2E_EMAIL` and `E2E_PASSWORD`

Run:

```sh
cd CentralAuth-fe
npm run test:e2e
```

Override the frontend URL with `E2E_BASE_URL` when needed.

## Current Limitations

- Email verification and password reset tokens are generated and stored, but there is no email delivery integration yet.
- Refresh tokens are implemented for browser session continuity, but they are still stored in `localStorage`. Moving CentralAuth refresh tokens to secure HttpOnly cookies remains future hardening work.
- Logout propagation is front-channel iframe based. There is no back-channel logout or token introspection endpoint yet.
- Kafka-backed audit persistence is disabled by default and must be explicitly enabled.
