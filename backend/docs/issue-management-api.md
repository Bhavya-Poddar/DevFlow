# Issue management API

All routes require the existing JWT bearer token. Relationship request fields use the nested ID shape, for example `{"project":{"id":12}}`. The server resolves referenced records and returns an `IssueResponseDTO` containing nested project, developer, tester, reporter, and sprint summaries. Password fields are never part of an issue response.

| Method and route | Purpose | Success response |
| --- | --- | --- |
| `GET /api/issues` | List issues | `200` JSON array of issue response DTOs |
| `GET /api/issues/{id}` | Read one issue | `200` issue response DTO; `404` if missing |
| `POST /api/issues` | Create an issue | `201` issue response DTO |
| `PUT /api/issues/{id}` | Partially update supplied fields | `200` issue response DTO |
| `PUT /api/issues/{id}/tester` | Assign a tester | `200` issue response DTO |
| `DELETE /api/issues/{id}` | Delete an issue | `204` with no response body |

For `PUT /api/issues/{id}`, omitted properties remain unchanged. Explicit `null` can clear nullable relationships where the actor may change them. Developers can update only issues assigned to themselves and cannot change project, sprint, developer, or tester relationships. Admins and project managers can manage issue relationships, subject to role/reference validation and the existing status transition rules. Only admins and project managers may delete issues.

Deleting an issue with comments or test execution history returns `409 Conflict`; those records are retained and must not be silently removed. Existing audit rows are retained by clearing their optional issue foreign-key reference before deleting the issue. A new `ISSUE_DELETED` audit row is written with the issue title and authenticated actor. A delete returning `204` is handled as a successful response by the frontend issue API helper.
