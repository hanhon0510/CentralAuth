# CentralAuth

CentralAuth is a full-stack centralized authentication and identity platform. It provides email/password authentication, email verification, password reset, JWT-based sessions, registered client applications, a central-login authorization-code flow, global logout propagation, admin user/client management, and audit-log visibility.

The repository contains:

- `CentralAuth-be`: Spring Boot 3.5.13 backend on Java 21.
- `CentralAuth-fe`: React 19, TypeScript, Vite, and Ant Design frontend.
- `docker-compose.yml`: local PostgreSQL, Redis, Kafka, and Kafka UI services.

## Main Features

### User Registration And Email Verification

- Users sign up with email, password, and optional display name.
- Emails are normalized to lowercase before persistence.
- Passwords are hashed with `BCryptPasswordEncoder`.
- New users receive `ROLE_USER` and start as `UNVERIFIED`.
- A 6-digit email verification OTP is stored in Redis with a configurable TTL.
- Verification changes the account to `ACTIVE`, enables the user, and marks the email as verified.
- Signup returns an auth payload, but protected routes reject the new user's token until email verification activates the account.
- OTP resend is protected by a Redis cooldown and returns `Retry-After` on throttled requests.
- The current implementation logs OTPs for local development instead of sending real email.

### Sign In And Login Protection

- Sign-in is allowed only for `ACTIVE` accounts.
- Access tokens are JWTs signed with HS256.
- The JWT contains issuer, subject user ID, email, roles, token use, issued-at, and expiry claims.
- Login attempts are protected by Redis-backed rate limiting per email and client IP.
- Repeated failed attempts trigger temporary email/IP locks.
- Login success and failure events can be published to Kafka for auditing.

### Password Reset

- Users can request a password reset by email.
- Reset tokens are opaque random tokens stored in Redis with a configurable TTL.
- Password reset is only completed for active users.
- Resetting a password updates the BCrypt hash and revokes all active refresh tokens for the user.
- The current implementation logs reset tokens for local development instead of sending real email.

### Sessions, Refresh Tokens, And Logout

- Successful signup, sign-in, and central login produce an access token and opaque refresh token.
- Tokens for non-active accounts are rejected by the authentication filter, so signup tokens become useful only after verification activates the account.
- Refresh tokens are stored only as SHA-256 hashes in PostgreSQL.
- `/api/v1/auth/logout` revokes the submitted refresh token and blacklists the current CentralAuth access token in Redis until the token expires.
- `/api/v1/auth/logout-all-devices` revokes all active refresh tokens for the user, blacklists the current CentralAuth access token, and stores a Redis logout cutoff so older CentralAuth access tokens are rejected.
- Logout responses include active registered client logout URIs.
- The frontend loads logout URIs in hidden iframes to propagate browser-based front-channel logout to demo clients.
- There is no refresh-token exchange endpoint in the current API; refresh tokens are currently issued and revoked for session tracking/logout behavior.

### Registered Client Applications

- Admins can create, edit, list, and activate/deactivate client applications.
- Client metadata includes:
  - `clientId`
  - `clientName`
  - redirect URIs
  - allowed origins
  - logout URIs
  - active flag
- Client IDs must match `[A-Za-z0-9._-]{1,120}`.
- Redirect and logout URIs must be HTTP/HTTPS URIs without fragments or user info.
- Allowed origins must be HTTP/HTTPS origins without paths, queries, fragments, user info, or wildcards.
- Duplicate metadata entries are rejected.
- Disabled clients cannot start central-login flows, exchange codes, or use client-scoped tokens against `/api/v1/auth/me`.

### Central Login For Client Apps

CentralAuth implements an OAuth-style central login flow for first-party/demo clients:

1. A client sends the browser to `/signin?client_id=...&redirect_uri=...&state=...`.
2. The frontend asks `/api/v1/auth/central-login/context` to validate the active client and exact redirect URI.
3. The backend stores a short-lived login state in Redis and returns `loginState`.
4. If the user is not signed in, the user signs in through `/api/v1/auth/central-login`.
5. If the user already has a CentralAuth session, the frontend can call `/api/v1/auth/central-login/continue`.
6. The backend consumes the login state, issues a one-time authorization code in Redis, and returns a redirect URL.
7. The client callback validates browser state and exchanges the code at `/api/v1/auth/central-login/token`.
8. The backend returns a client-scoped JWT with `token_use=client_access` and `aud=<clientId>`.

Client-scoped JWTs are intentionally limited. They can read `/api/v1/auth/me` for the current user, but they do not grant admin access.

### Demo Client Applications

When `CENTRALAUTH_DEMO_CLIENTS_ENABLED=true`, the backend bootstraps two local clients:

- `projects-demo`
- `reports-demo`

The frontend exposes demo routes:

- `/demo/projects`
- `/demo/projects/protected`
- `/demo/projects/callback`
- `/demo/projects/logout`
- `/demo/reports`
- `/demo/reports/protected`
- `/demo/reports/callback`
- `/demo/reports/logout`

Each demo client stores its own client token and callback state in `localStorage`, calls the central login flow, validates callback state, exchanges authorization codes, and clears local state during front-channel logout.

### Admin User Management

Users with `ROLE_ADMIN` can:

- List users with optional email and account status filters.
- Limit result count from 1 to 200.
- View user ID, email, display name, enabled flag, email verification flag, account status, roles, and timestamps.
- Change account status to `ACTIVE`, `DISABLED`, `LOCKED`, or `UNVERIFIED`.

Status changes update derived flags:

- `ACTIVE`: enabled and email verified.
- `DISABLED` or `LOCKED`: disabled and preserves current email verification.
- `UNVERIFIED`: disabled and not email verified.

Admin access can be bootstrapped with `CENTRALAUTH_ADMIN_BOOTSTRAP_EMAILS`. The configured users must already exist; the role is assigned on application startup.

### Audit Logs And Events

The application defines audit events for:

- `USER_REGISTERED`
- `USER_VERIFIED`
- `LOGIN_SUCCEEDED`
- `LOGIN_FAILED`
- `USER_LOGGED_OUT`
- `PASSWORD_RESET_REQUESTED`
- `PASSWORD_CHANGED`
- `ADMIN_USER_STATUS_CHANGED`

When Kafka is enabled, authentication/admin events are published to configured topics. The audit consumer can persist consumed events to PostgreSQL with event type, user ID, email, client IP, reason, Kafka topic/key, timestamps, and JSON payload.

Admins can query recent audit logs by event type, user ID, email, and limit.

Kafka is disabled by default so local development and tests can run without a broker.

### Frontend Application

The frontend includes:

- Sign-in and sign-up pages.
- Email verification OTP screen with resend cooldown.
- Forgot-password and reset-password screens.
- Central-login-aware sign-in that can redirect back to registered clients.
- Protected dashboard route.
- Session card with user details, role tags, token preview, sign out, and sign out all devices.
- Admin-only panels for users, client applications, and audit logs.
- English and Vietnamese UI messages.
- `Accept-Language` propagation on API requests.
- Vite proxy from `/api` to `http://localhost:8080`.

### API Response And Error Handling

Most API responses use:

```json
{
  "success": true,
  "message": "Message",
  "data": {},
  "timestamp": "2026-05-27T00:00:00Z"
}
```

Backend errors are centralized with `@RestControllerAdvice`. Validation, duplicate email, invalid credentials, invalid OTP/reset/code/state, throttling, login locks, inactive clients, duplicate clients, and not-found cases return structured error responses.

Localized backend messages are available in English and Vietnamese through `messages.properties` and `messages_vi.properties`.

## Backend API

### Public Endpoints

| Method | Path | Purpose |
| --- | --- | --- |
| `GET` | `/api/v1/health` | Basic application health response. |
| `POST` | `/api/v1/auth/signup` | Create user, issue verification OTP, return auth payload. |
| `POST` | `/api/v1/auth/signin` | Authenticate active user and return JWT, refresh token, and user profile. |
| `POST` | `/api/v1/auth/verify-email` | Verify a pending user with a 6-digit OTP. |
| `POST` | `/api/v1/auth/resend-verification-otp` | Issue a new OTP when resend cooldown allows it. |
| `POST` | `/api/v1/auth/forgot-password` | Issue password reset token for active account if the email exists. |
| `POST` | `/api/v1/auth/reset-password` | Reset password with a valid reset token. |
| `GET` | `/api/v1/auth/central-login/context` | Validate client login request and issue `loginState`. |
| `POST` | `/api/v1/auth/central-login` | Sign in during client login and return client redirect data. |
| `POST` | `/api/v1/auth/central-login/token` | Exchange one-time authorization code for a client-scoped JWT. |

### Authenticated User Endpoints

| Method | Path | Purpose |
| --- | --- | --- |
| `GET` | `/api/v1/auth/me` | Return current user for CentralAuth JWTs and valid client-scoped JWTs. |
| `GET` | `/api/v1/users/me` | Return current user for authenticated CentralAuth sessions. |
| `POST` | `/api/v1/auth/central-login/continue` | Continue a client login with the current CentralAuth session. |
| `POST` | `/api/v1/auth/logout` | Revoke submitted refresh token and blacklist current access token. |
| `POST` | `/api/v1/auth/logout-all-devices` | Revoke all refresh tokens and reject older access tokens for the user. |

### Admin Endpoints

All admin endpoints require `ROLE_ADMIN`.

| Method | Path | Purpose |
| --- | --- | --- |
| `GET` | `/api/v1/admin/users` | List users with optional `email`, `status`, and `limit` filters. |
| `GET` | `/api/v1/admin/users/{id}` | Get one user by ID. |
| `PATCH` | `/api/v1/admin/users/{id}/status` | Change account status. |
| `GET` | `/api/v1/admin/clients` | List registered client applications. |
| `POST` | `/api/v1/admin/clients` | Create a client application. |
| `PUT` | `/api/v1/admin/clients/{clientId}` | Replace client metadata. |
| `PATCH` | `/api/v1/admin/clients/{clientId}/active` | Toggle client active status. |
| `GET` | `/api/v1/audit-logs` | Query recent audit logs with optional `eventType`, `userId`, `email`, and `limit`. |

## Data Stores

### PostgreSQL

Flyway migrations create and update:

- `users`
- `user_roles`
- `refresh_tokens`
- `audit_logs`
- `clients`
- `client_redirect_uris`
- `client_allowed_origins`
- `client_logout_uris`

MyBatis XML mappers handle database access.

### Redis

Redis stores transient security state:

- email verification OTPs
- OTP resend throttle keys
- password reset tokens
- login attempt counters
- temporary login locks
- central-login state
- one-time authorization codes
- access-token blacklist entries
- logout-all-devices cutoff timestamps

### Kafka

Kafka is optional and disabled by default. When enabled, authentication/admin events are published to topics under `centralauth.kafka.topics.*`, and the audit consumer can persist them to `audit_logs`.

## Local Development

### Prerequisites

- Java 21
- Node.js and npm
- Docker Desktop or Docker Engine

### Start Infrastructure

Start PostgreSQL and Redis:

```sh
docker compose up -d postgres redis
```

Start Kafka and Kafka UI only when testing the Kafka audit pipeline:

```sh
docker compose up -d kafka kafka-ui
```

Kafka UI is available at `http://localhost:8081`.

### Configure The Backend

The backend imports an optional `.env` file from the repository root when run from `CentralAuth-be`.

Create `.env` locally if needed:

```properties
DATASOURCE_URL=jdbc:postgresql://localhost:5432/centralauth
DATASOURCE_USERNAME=centralauth
DATASOURCE_PASSWORD=centralauth
CENTRALAUTH_REDIS_HOST=localhost
CENTRALAUTH_REDIS_PORT=6379
CENTRALAUTH_JWT_SECRET=replace-with-a-long-random-secret
CENTRALAUTH_KAFKA_ENABLED=false
```

The `.env` file is ignored by git.

### Run Backend

On Windows:

```sh
cd CentralAuth-be
.\mvnw.cmd spring-boot:run
```

On macOS/Linux:

```sh
cd CentralAuth-be
./mvnw spring-boot:run
```

The backend runs on `http://localhost:8080` by default.

### Run Frontend

```sh
cd CentralAuth-fe
npm install
npm run dev
```

The frontend runs on `http://localhost:5173` by default.

## Configuration Reference

| Variable | Purpose | Default |
| --- | --- | --- |
| `PORT` | Backend HTTP port. | `8080` |
| `DATASOURCE_URL` | PostgreSQL JDBC URL. | `jdbc:postgresql://localhost:5432/centralAuth` |
| `DATASOURCE_USERNAME` | PostgreSQL username. | `centralauth` |
| `DATASOURCE_PASSWORD` | PostgreSQL password. | `centralauth` |
| `CENTRALAUTH_REDIS_HOST` | Redis host. | `localhost` |
| `CENTRALAUTH_REDIS_PORT` | Redis port. | `6379` |
| `CENTRALAUTH_REDIS_PASSWORD` | Redis password. | empty |
| `CENTRALAUTH_JWT_SECRET` | HS256 signing secret. Use a strong secret outside local dev. | development value |
| `CENTRALAUTH_JWT_ISSUER` | JWT issuer claim. | `central-auth` |
| `CENTRALAUTH_JWT_EXPIRES_IN_SECONDS` | Access-token lifetime. | `3600` |
| `CENTRALAUTH_REFRESH_TOKEN_EXPIRES_IN_SECONDS` | Refresh-token lifetime. | `2592000` |
| `CENTRALAUTH_EMAIL_VERIFICATION_OTP_TTL` | Email OTP lifetime. | `10m` |
| `CENTRALAUTH_EMAIL_VERIFICATION_RESEND_COOLDOWN` | OTP resend cooldown. | `60s` |
| `CENTRALAUTH_PASSWORD_RESET_TOKEN_TTL` | Password reset token lifetime. | `15m` |
| `CENTRALAUTH_LOGIN_PROTECTION_MAX_FAILED_ATTEMPTS` | Failed attempts before temporary lock. | `5` |
| `CENTRALAUTH_LOGIN_PROTECTION_FAILURE_WINDOW` | Failure counting window. | `15m` |
| `CENTRALAUTH_LOGIN_PROTECTION_LOCK_DURATION` | Temporary lock duration. | `15m` |
| `CENTRALAUTH_LOGIN_RATE_LIMIT_MAX_ATTEMPTS` | Max attempts per rate window. | `10` |
| `CENTRALAUTH_LOGIN_RATE_LIMIT_WINDOW` | Login rate-limit window. | `1m` |
| `CENTRALAUTH_ADMIN_BOOTSTRAP_EMAILS` | Comma-separated emails to receive `ROLE_ADMIN` at startup if users exist. | empty |
| `CENTRALAUTH_DEMO_CLIENTS_ENABLED` | Bootstrap local demo clients. | `true` |
| `CENTRALAUTH_KAFKA_ENABLED` | Enable Kafka publisher/consumer beans. | `false` |
| `CENTRALAUTH_KAFKA_AUDIT_ENABLED` | Auto-start audit Kafka listener when Kafka is enabled. | `true` |
| `CENTRALAUTH_KAFKA_BOOTSTRAP_SERVERS` | Kafka bootstrap servers. | `localhost:9092` |

## Testing

### Backend

```sh
cd CentralAuth-be
.\mvnw.cmd test
```

Use `./mvnw test` on macOS/Linux.

Backend tests cover authentication, JWT validation, JWT filter behavior, refresh-token persistence, admin users, admin clients, audit logs, Kafka toggles, Flyway migrations, and demo client bootstrapping.

### Frontend

```sh
cd CentralAuth-fe
npm run lint
npm run test
```

Frontend unit tests cover demo auth helpers and front-channel logout behavior.

### Browser E2E

The frontend includes a Playwright scaffold for the demo client flow.

Prerequisites:

- Backend running on `http://localhost:8080`.
- Frontend running on `http://localhost:5173`.
- Redis available to the backend.
- Demo clients enabled so `projects-demo` and `reports-demo` are registered.
- An active, verified user supplied through `E2E_EMAIL` and `E2E_PASSWORD`.

Run:

```sh
cd CentralAuth-fe
npm run test:e2e
```

Override the frontend URL with `E2E_BASE_URL` when needed.

## Current Limitations

- OTP and password reset delivery is development-oriented and logs secrets instead of sending real email.
- The current API issues and revokes refresh tokens, but does not expose an access-token refresh endpoint.
- Logout propagation is front-channel iframe based. There is no back-channel logout or token introspection endpoint yet.
- Kafka-backed audit logging is disabled by default and must be explicitly enabled for event persistence through Kafka.
