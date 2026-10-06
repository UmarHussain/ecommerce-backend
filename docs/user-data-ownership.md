# Where user information belongs

| Data | Authoritative owner |
|---|---|
| Passwords/password hashes, MFA, login sessions, credentials | Keycloak only |
| Identity subject, verified email, identity enabled/disabled, role assignments | Keycloak |
| Application user ID and link `(issuer, subject)` | user-service PostgreSQL |
| Customer addresses, preferences, application profile | user-service PostgreSQL |
| Staff employee reference, department, onboarding information | user-service PostgreSQL |
| Identity-operation tracking and application access-change audit | user-service PostgreSQL |
| Order shipping address at purchase time | Immutable snapshot in order-service |

Recommended shape: one `app_user` identity link; optional `customer_profile` and `staff_profile` rows both reference it. A person may have both. No two password tables. Do not use email as the join key because it changes.

Keycloak role assignment is authoritative. A staff_profile row is not proof of permission. If email/display name are copied for application display/search, label their owner and reconciliation policy; do not independently change the identity email in two systems. No authoritative duplicate role-assignment table in userdb.

On first authenticated customer use, idempotently create the profile using the validated token identity. Staff profiles are created by authorized onboarding. Adding seeded Keycloak accounts does not pretend these application records already exist.

PostgreSQL and Keycloak cannot participate in one Spring local transaction. Administrative workflows need durable operation IDs, status, bounded retry/reconciliation, and audit. Never delete a pre-existing identity to undo a failed profile insert.

Phase 1 implemented: idempotent `GET /api/v1/users/me` creates `app_user` + `customer_profile` from the validated JWT `(issuer, subject)`. Staff rows are created only by authorized onboarding. Email/display name on `app_user` are snapshots refreshed from the token, not authorization keys.

Directory-changing admin calls persist an `identity_operation` first. A Keycloak timeout is stored as `UNCERTAIN` and returns `202 Accepted` with an operation URL. A 15s reconciler looks up the identity before retrying create. Pre-existing Keycloak accounts are linked, never deleted.
