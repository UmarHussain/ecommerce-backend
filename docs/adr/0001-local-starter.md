# ADR 0001 — Separate starter with staged implementation

Decision: reuse useful source from the authorized ZIP in this independent project. Keep catalog business code, adapt its authorization, and add minimal executable foundation. Do not carry old Git/IDE state, AWS instructions, or completion claims.

One realm and separate SPA clients allow a person to be customer and staff. Storefront role scope prevents staff authority leakage; real token tests are mandatory. Portal backends are API composition services, not cookie-session BFFs. Domain service invariants stay with owned data.

The application is intentionally not fully implemented by this starter. Cursor first verifies the foundation and builds identity administration. This makes progress inspectable and avoids silently claiming complex Saga/security features are done.
