# CentralAuth

## Global Logout Propagation V1

CentralAuth logout now has two layers:

- `/api/v1/auth/logout` revokes the submitted refresh token and blacklists the current CentralAuth access token in Redis until that JWT expires.
- `/api/v1/auth/logout-all-devices` revokes all active refresh tokens for the authenticated user, blacklists the current CentralAuth access token, and records a Redis logout cutoff so older CentralAuth access tokens for that user are rejected.

This v1 behavior immediately invalidates CentralAuth's own authenticated session state. Connected client applications still own their local session state. A client should clear its locally stored client token when the user clicks logout in that client, and it should also clear local auth state when a CentralAuth-backed check returns `401`.

Client access tokens are still JWTs and may remain usable until expiration unless the client implements an additional propagation mechanism. Future options are:

- **Front-channel logout:** CentralAuth redirects or loads registered client logout URLs so browser-based clients can clear local storage/cookies.
- **Back-channel logout:** CentralAuth sends server-to-server logout notifications to registered client webhook endpoints.
- **Token revocation or introspection:** Clients validate token status against CentralAuth before accepting local auth state.
