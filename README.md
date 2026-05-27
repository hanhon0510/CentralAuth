# CentralAuth

## Phase 6 Auth Integration

CentralAuth now supports registered client applications with redirect and logout URI metadata. Demo clients are bootstrapped for:

- `projects-demo`
- `reports-demo`

The central login flow validates registered clients and redirect URIs, stores short-lived login state and authorization codes in Redis, issues client-scoped JWTs after code exchange, and allows a current CentralAuth session to continue a second client login without re-entering credentials.

## Global Logout Propagation V2

CentralAuth logout now has three layers:

- `/api/v1/auth/logout` revokes the submitted refresh token and blacklists the current CentralAuth access token in Redis until that JWT expires.
- `/api/v1/auth/logout-all-devices` revokes all active refresh tokens for the authenticated user, blacklists the current CentralAuth access token, and records a Redis logout cutoff so older CentralAuth access tokens for that user are rejected.
- Both logout endpoints return active registered client logout URIs. The frontend loads those URLs in hidden iframes so browser-based demo clients clear local client tokens and callback state.

Client access tokens remain JWTs, but CentralAuth rejects client-scoped tokens on `/api/v1/auth/me` when the token's audience client has been disabled.

Future options:

- **Back-channel logout:** CentralAuth sends server-to-server logout notifications to registered client webhook endpoints.
- **Token revocation or introspection:** Clients validate token status against CentralAuth before accepting local auth state.

## Browser E2E

The frontend includes a Playwright scaffold for the Phase 6 demo flow.

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
