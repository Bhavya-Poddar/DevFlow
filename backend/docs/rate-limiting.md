# API rate limiting

DevFlow applies a fixed-window IP limit to `POST /api/auth/login`. Exceeded limits return HTTP 429 with
the standard API error fields (`timestamp`, `status`, `errorCode`, `message`,
and `path`) and a `Retry-After` header.

## Defaults

| Profile | Login | Tracked keys |
| --- | --- | --- |
| `dev` | 20 requests / 15 minutes | 100,000 |
| `prod` and default | 10 requests / 15 minutes | 100,000 |

Override with `DEVFLOW_LOGIN_RATE_LIMIT_MAX_REQUESTS`,
`DEVFLOW_LOGIN_RATE_LIMIT_WINDOW` and `DEVFLOW_RATE_LIMIT_MAX_TRACKED_KEYS`.
Windows accept Spring duration values such as `15m` or `60s`.

Login attempts are keyed by peer IP, not account name, so changing emails does
not reset an IP's allowance. Counters contain only endpoint category, peer IP,
window start, and count; credentials, tokens, bodies, and account identifiers
are not stored or logged. `X-Forwarded-For` and `Forwarded` headers are ignored.

## Deployment limitation

This limiter is bounded and process-local. It is useful as a lightweight abuse
control but is not a distributed production rate-limit service: counters are
not shared between application instances and reset when an instance restarts.
With multiple instances, clients may receive a separate allowance per instance.
For a deployment behind a reverse proxy, configure the proxy to enforce its own
shared edge limits. The backend uses `HttpServletRequest.getRemoteAddr()`; if
the proxy is not configured as the direct peer, clients behind it will share
the proxy's bucket. DevFlow intentionally does not trust arbitrary forwarding
headers. Do not enable framework forwarded-header handling unless the network
path is restricted to known proxies that overwrite those headers.
