# DevFlow application redesign checklist

Route source inventory from src/App.jsx:
- [x] / and /login — branded sign-in composition; token handling, endpoint, validation, and navigation preserved.
- [x] /register — distinct registration composition; public registration contract preserved.
- [x] /dashboard — analytics composition carried forward from the dashboard redesign; backed by the existing summary API.
- [x] /issues — operational summary and refined issue table/details/dialog layout; issue handlers preserved.
- [x] /projects — portfolio summary and project-card composition; project actions preserved.
- [x] /sprints — delivery summary and planning layout; sprint actions preserved.
- [x] /team — access-directory hierarchy and role/member management layout preserved.
- [x] /team-workload — workload/status scan layout; shared Avatar fallback used.
- [x] /code-reviews — role-aware review queue cards.
- [x] /comments — discussion and composer surfaces.
- [x] /activity — activity timeline and filters.
- [x] /audit-logs — audit history and permission-denied styling.
- [x] /notifications — inbox layout and per-item state.
- [x] Notification bell — popover/trigger focus states and accessible expanded state.
- [x] /testing and /testing-queue — paired queue columns and refresh affordance.
- [x] /my-tests — assigned testing split by lifecycle stage.
- [x] /testing/:issueId — issue context, test actions, and history in responsive two-column workspace.
- [x] /reports — lifecycle report table and filters.
- [x] /account — profile surface and responsive profile/security forms.
- [x] /settings — preference groups and account-security navigation.
- [x] Shared layout — role-specific nav, responsive drawer, account menu, notification control, and shared avatar styling.
- [x] Unknown route — existing redirect behavior retained; no separate 404 screen existed.

## Verification

- Baseline before redesign pass: npm run lint passed; npm run build passed.
- Final validation: npm run lint and npm run build (see task report).
- Browser visual review: pending; no controllable browser session was available. This checklist records source/build review, not browser QA.
- Automated component tests: none configured in this package; no test dependencies were added.