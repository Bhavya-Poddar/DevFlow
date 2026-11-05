# Frontend API and origin configuration

The React application reads `VITE_API_BASE_URL` at build time. It is the backend origin (scheme, host, and optional port), without an `/api` or `/ws` suffix. All frontend REST calls, avatar URLs, and SockJS connections use this value. If it is unset, the existing local default `http://localhost:8080` is used.

The backend reads `DEVFLOW_CORS_ALLOWED_ORIGINS` as a comma-separated list of exact browser origins. REST CORS and the `/ws` SockJS handshake share this list. If unset, the local default is `http://localhost:5173`. Origins must be complete `http://` or `https://` origins without paths or wildcards. Credentials remain enabled; wildcard origins are rejected. REST permits GET, POST, PUT, DELETE, and OPTIONS with Authorization, Content-Type, and Accept headers.

## Local development

The defaults work without extra settings:

```text
VITE_API_BASE_URL=http://localhost:8080
DEVFLOW_CORS_ALLOWED_ORIGINS=http://localhost:5173
```

Set the Vite variable in the frontend's local `.env.local` before running or building Vite. Set the backend variable in the Spring Boot environment if overriding its default. Restart the relevant process after changing either value.

## Production deployment

Set these values in the frontend build environment and backend deployment environment. Use your actual HTTPS origins in the deployment platform's secret/configuration store; do not commit deployment-specific hostnames:

```text
VITE_API_BASE_URL=https://<api-domain>
DEVFLOW_CORS_ALLOWED_ORIGINS=https://<frontend-domain>
```

For multiple approved frontend origins, separate exact origins with commas, for example `https://<primary-frontend-domain>,https://<admin-frontend-domain>`. Do not include credentials, URL paths, or a wildcard. Vite embeds `VITE_API_BASE_URL` into the frontend bundle, so rebuild and redeploy the frontend after changing it. HTTPS API URLs are passed to SockJS as HTTPS; SockJS negotiates a secure WebSocket (`wss`) transport when available. The backend still authenticates STOMP CONNECT using the existing JWT interceptor.

These settings configure application behavior only. A deployment must still route the configured API origin to the intended backend and allow WebSocket upgrades at its reverse proxy or ingress. No live deployment or browser has been verified by these source-level changes.
