# DevFlow API documentation

Springdoc generates the OpenAPI JSON at `/v3/api-docs` and Swagger UI at `/swagger-ui/index.html`.

The documentation is enabled by the `dev` profile. Outside that profile, including `prod`, both endpoints default to disabled. An explicit deployment can set `DEVFLOW_API_DOCS_ENABLED=true`; when documentation is enabled outside development, the routes require an authenticated ADMIN unless `DEVFLOW_API_DOCS_PUBLIC_ACCESS=true` is explicitly configured. Do not enable public production documentation unless it is an intentional deployment decision.

Swagger UI uses the `bearerAuth` HTTP Bearer scheme. Sign in through the documented login endpoint, copy its JWT token, then use **Authorize** and enter the token. Swagger UI supplies the `Bearer` prefix. API endpoint authorization remains enforced by the existing security configuration.

The documentation describes the existing REST surface and generated request/response schemas. It does not document database credentials, JWT signing secrets, webhook secrets, profile-picture file paths, or internal stack traces. The password schema is marked write-only.
