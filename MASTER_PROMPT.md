# Master Cursor Prompt: New Local E-commerce Project with Keycloak, RBAC, and Kafka Saga

## How to use this prepared starter

This is the design specification for the separate `ecommerce-local-platform` starter generated from the user-supplied ZIP. The user explicitly authorized reusing that uploaded project to provide a head start. The original application remains untouched.

Work in this repository root. Do not create another nested project, erase this scaffold, or regenerate retained catalog business code. Read START_HERE.md, IMPLEMENTATION_PLAN.md, docs/service-status.md, and docs/verification.md first. Phase 1 is complete. The active request path is React → API gateway → owning domain service. `storefront-backend` and `admin-portal-backend` were removed and must not be recreated unless a later task needs real response composition.

## 1. Your role and project objective

Act as a principal Java/Spring engineer, practical software architect, frontend engineer, and patient technical mentor. Build a functioning local e-commerce application and an explainable microservices portfolio project.

I am an experienced Java backend developer. I previously explored an e-commerce design with catalog, inventory, cart, and order services plus storefront and admin frontends. The user has authorized taking useful code from the supplied ZIP into this separate starter. It has its own source tree, configuration, databases, and infrastructure. Build on the starter while implementing user service and Keycloak administration.

The new direction adds:

- Direct gateway routes to domain services. Portal backends are not in the active topology; add one later only when a screen must compose several services.
- A user service for application profiles and controlled Keycloak administration.
- Fine-grained permissions and composable staff roles.
- Database ownership and business rules inside domain services.
- A durable checkout Saga using Kafka, transactional outbox, and idempotent consumers.
- Redis caching, secure service communication, and progressive observability.

Everything runs locally. Do not add cloud providers, Terraform, managed cloud services, cloud learning modules, Kubernetes, or deployment abstractions for hypothetical environments. Do not generate cloud files or depend on the previous project.

Produce executable source, migrations, configurations, meaningful tests, local scripts, seed data, and documentation. Explain tradeoffs as you implement them.

## 2. Working rules and immediate stopping point

1. Read applicable workspace instructions and verify the intended new-project destination. Work inside the current `ecommerce-local-platform` repository; preserve its existing work and unrelated changes.
2. Continue the prepared project scaffold. Produce `docs/project-foundation.md` describing the new project structure, selected versions, prerequisites, ports, and local infrastructure isolation. Do not modify the original application outside this repository.
3. Create or update `IMPLEMENTATION_PLAN.md`, `docs/architecture.md`, and `docs/progress.md`.
4. Implement one phase at a time. Run relevant verification and repair failures before proceeding.
5. Overall first milestone: Phases 0–3, covering foundation, identity/security, catalog, and inventory administration. The prepared starter already supplies part of Phase 0. Current Cursor assignment: verify Phase 0 and implement Phase 1, then report results and stop for review. Phases 4–7 describe the agreed later roadmap, not the current coding assignment.
6. Continue autonomously through routine, reversible local work within this milestone. Ask only when an unresolved choice changes scope, destroys data, or contradicts these requirements.
7. Never reset a database, delete volumes, overwrite secrets, rewrite migrations already applied, or discard Git changes without explicit authorization.
8. Do not report a test or flow as passed unless executed. Record blocked checks and exact causes.
9. No passwords, tokens, private keys, or real personal data in Git. Seed only fictional local accounts. Supply `.env.example` placeholders and an ignored local-secret generation script.
10. Pin compatible dependencies and container versions; no floating `latest` images. Retain the pinned versions unless a verified compatibility issue requires a documented change.
11. Keep code direct and readable. Avoid generic CRUD frameworks, shared domain models, speculative provider interfaces, or a custom authentication framework.
12. At each phase completion explain the implementation, verification, limitations, and a short interview-ready description of the design.

## 3. Architecture and business ownership

Use these applications and responsibilities:

| Component | Responsibility | Must not own |
|---|---|---|
| `storefront-web` | Public browsing and customer experience | Authorization decisions or authoritative totals |
| `admin-web` | Staff user, role, catalog, inventory, and later order UI | Credentials or authoritative workflow rules |
| `api-gateway` | Routing, path rewrite, JWT validation, coarse route policy, CORS, request limits, correlation | Domain rules, Saga, response composition, or Keycloak administration |
| `user-service` | Profiles, staff onboarding, controlled application role administration, admin principal, audit | Password storage or issuing login tokens |
| `catalog-service` | Categories, products, variants/SKUs, prices, activation rules | Stock or orders |
| `inventory-service` | Stock adjustments, reservations, releases, consumption, stock invariants | Product pricing or checkout coordination |
| `cart-service` | Customer carts, item rules, cart versioning | Final checkout prices or payment |
| `order-service` | Orders, legal transitions, checkout Saga, order history | Direct access to another service database |
| `payment-service` | Later local payment simulator, payment attempts, refunds | Real financial transactions |
| Keycloak | Login, credentials, identity, tokens, role assignments | Application addresses or order data |

Authentication uses SPA Authorization Code with PKCE, specified below. The gateway does not hold browser sessions and does not compose downstream responses. A server-session BFF remains a later option, not the current design.

Domain services should be focused, but not CRUD-only. Keep invariants with the data owner:

- Only order service decides whether an order can be cancelled or dispatched.
- Only inventory service decides whether stock can be reserved.
- Only catalog service decides whether a product or price update is valid.
- The gateway forwards a command to the owning service. It does not implement distributed transactions.

Example staff action: admin UI → gateway → order service `dispatch` command. Order service authorizes and validates the transition, persists the change and audit record, and emits an event if needed.

Example identity action: admin UI → gateway → user service → Keycloak Admin REST API. The frontend never calls the Admin REST API itself.

Login is a different flow: browser → Keycloak login page → frontend callback. Passwords do not flow through user service.

## 4. Technology and repository layout

Use Java 21, a compatible supported Spring Boot version selected during project setup, Maven Wrapper, Spring MVC for domain services, Spring Security OAuth2 Resource Server, method security, JPA, Flyway, PostgreSQL, Bean Validation, Actuator, Micrometer, Springdoc OpenAPI, Resilience4j, JUnit, Mockito, and Testcontainers.

Use a compatible Spring Cloud Gateway version for the gateway; document its Spring Boot compatibility. Keep reactive gateway code separate from blocking JPA services. Choose a supported HTTP client and configure its connection, read, and pool-acquisition timeouts.

Use React, TypeScript, Vite, React Router, TanStack Query, form validation, Vitest, React Testing Library, and Playwright; select compatible versions for the new project. Use a maintained OIDC client; do not manually implement the OAuth protocol.

Use Docker Compose, Keycloak, PostgreSQL, Redis, and Kafka in KRaft mode locally. Redis and Kafka enter only at the phases where needed. A local mail catcher may support registration/invitations without sending real email.

Continue this structure in the current `ecommerce-local-platform/` root:

```text
backend/
  pom.xml
  api-gateway/
  user-service/
  catalog-service/
  inventory-service/
  cart-service/
  order-service/
  payment-service/
frontend/
  storefront-web/
  admin-web/
infrastructure/local/
  compose.yaml
  keycloak/
  postgres/
  kafka/
  observability/
scripts/local/
docs/
  adr/
  contracts/
  demos/
  project-foundation.md
  architecture.md
  security-model.md
  permission-matrix.md
  progress.md
IMPLEMENTATION_PLAN.md
README.md
```

Cart and order shells were retained from the supplied ZIP; leave them as shells until their phase. Create other future modules only when their phase starts. A parent build may share dependency versions. Never share JPA entities, repositories, or service business models across modules.

## 5. Identity model: one person can be customer and staff

Default decision: one application realm, `ecommerce-local`, with one Keycloak account per person and separate storefront/admin OAuth clients.

- Disable duplicate emails. Require verified email before privilege-sensitive onboarding. Define normalization and collision behavior explicitly.
- A person may have CUSTOMER and one or more staff roles on the same account.
- The same email can therefore log into both applications. It does not create two identities or automatically grant admin access.
- Separate login entry points and redirect URIs are required. The same realm can reuse an existing SSO session; separate clients do not automatically force separate password prompts.
- A customer-only user may authenticate at the admin client but must receive an access-denied experience and no usable admin API privileges.
- Self-registration grants CUSTOMER only. It must never accept role, staff status, permission, or customer ID fields from the browser.
- Existing customers become staff through an explicitly authorized staff onboarding operation, selecting and verifying the existing Keycloak identity. Do not automatically elevate someone merely because an email string matches.
- Staff can participate as customers through the storefront; customer APIs still restrict them to their own resources.
- Suspension of staff access removes staff privileges without disabling their customer account. Global identity disablement is a different, stronger administrative operation.
- Use `(issuer, subject)` as the external identity key, never email. Email may change.

Document the alternative of separate customer/staff realms: independent identities, sessions, and policies, and the same email may exist in both. Do not implement that alternative for this MVP. Recommend reconsidering it only when independent workforce identity or stronger account separation is a concrete requirement.

## 6. Authentication, token boundaries, and service security

Implement OpenID Connect Authorization Code with PKCE S256, aligned with current OAuth security best practice. Verify the exact OAuth 2.1 specification status when documenting it; do not claim a draft is a finalized standard. Disable implicit and password/direct access grants for application clients.

Clients:

- `storefront-spa`: public client, exact storefront redirects/origins, customer role scope mappings only.
- `admin-spa`: public client, exact admin redirects/origins, approved staff role scope mappings.
- `user-service-admin`: confidential service account used only by user service to administer the application realm within bounded privileges.
- Resource-server client entries for the gateway and individual domain APIs as needed for audiences and client roles. Do not keep clients for removed portal backends.
- Separate machine clients for later synchronous service-owned operations; do not reuse the Keycloak administration identity.

Disable broad full-scope role exposure and configure explicit role scope mappings and audience mappers. A storefront token for a dual-role user must contain no staff permissions. Requesting additional OAuth scopes must not allow a storefront client to acquire staff permissions.

Relay the end-user access token from the gateway to the destination service. Configure the audiences that gateway and that destination actually validate. Do not grant every token every audience to make a route succeed. Narrowing each hop with token exchange is a later option, not an assumed feature.

Every resource server independently validates signature, permitted algorithm, issuer, expiry/not-before, and its expected audience. Each sensitive endpoint also checks permissions and, where appropriate, authorized client identity. Reject ID tokens as API credentials. Do not trust `X-User-Id`, `X-Roles`, or browser-supplied identity headers.

Machine tokens do not represent customers. Do not replace user tokens with a powerful service token and then trust a customer ID in an HTTP header. Async Saga commands are authenticated service operations authorized at ingress; actor identity in the event is audit context, not a new bearer credential.

Access tokens remain in browser memory. Do not put them in localStorage, URLs, or logs. If refresh tokens are used, keep them in memory and configure supported rotation/reuse detection. Reload may require a fresh authorization redirect. Document XSS exposure of SPA tokens. A server-session BFF may be evaluated later as a separate architectural change.

Use narrowly scoped CORS and redirect allowlists. Stateless bearer APIs may disable CSRF only with the rationale documented; cookie-authenticated endpoints would require CSRF protection. Enable staff MFA in the local demo with documented enrollment.

Gateway policy is the first check, not the only check. Services use request rules and `@EnableMethodSecurity`. Internal routes are not externally routed and require machine authority even on the private network. Docker networking alone is not authentication.

Provide a local TLS/mTLS demonstration profile for inter-service HTTP with locally generated, ignored keys and trust material. Clearly identify any plain-HTTP localhost/private-network development baseline as lacking transport encryption; do not describe it as production-secure. Never disable certificate verification to make the demo pass.

Use short-lived access tokens and document that role changes or logout do not instantly invalidate already issued offline-validated JWTs. Explicitly test refresh/re-login behavior. Do not claim immediate revocation unless implementing and testing an additional online revocation check.

## 7. Roles, permissions, and Spring authorities

Use application roles as named permission bundles. Represent permissions as client roles under the owning API client and use composite application roles to bundle them. Seed a finite permission catalog in version-controlled configuration.

| Role | Permission bundle |
|---|---|
| CUSTOMER | `profile.read_own`, `profile.update_own`, `cart.read_own`, `cart.write_own`, `order.create`, `order.read_own`, `order.cancel_own` |
| CATALOG_VIEWER | `admin.access`, `catalog.read` |
| CATALOG_CREATOR | `admin.access`, `catalog.read`, `catalog.create` |
| CATALOG_EDITOR | `admin.access`, `catalog.read`, `catalog.update`, `catalog.activate` |
| INVENTORY_MANAGER | `admin.access`, `catalog.read`, `inventory.read`, `inventory.adjust` |
| ORDER_MANAGER | `admin.access`, `order.read_all`, `order.process`, `order.dispatch`, `order.deliver`, `order.cancel_any` |
| USER_ADMIN | `admin.access`, `user.read`, `user.create`, `user.update`, `user.manage_staff`, `role.read`, `role.assign` |
| PLATFORM_ADMIN | All explicitly defined application staff permissions, including `role.manage` and `user.disable_identity` |

CATALOG_CREATOR does not automatically edit existing products. Assign CREATOR and EDITOR together where both abilities are needed. PLATFORM_ADMIN is an application administrator, not a Keycloak master-realm administrator, and does not bypass order or inventory invariants. PLATFORM_ADMIN does not need implicit customer access; CUSTOMER is assigned separately.

Expose `/api/v1/admin/me` with effective application permissions for UI visibility. The backend remains authoritative. Customer APIs require both the correct own-resource permission and ownership in database queries.

Naming clarification:

- A role named `order.read_all` in a Keycloak client-role claim is not automatically an OAuth scope.
- Spring's standard scope conversion uses `SCOPE_` for actual `scope`/`scp` values.
- In this project explicitly map allowlisted application client roles to `PERM_` authorities, e.g. `PERM_order.read_all`, and bundle roles to `ROLE_` only where needed.
- Example: `@PreAuthorize("hasAuthority('PERM_order.read_all')")`.
- If a real OAuth scope is named `read_order`, its default authority would be `SCOPE_read_order`, not `Scope.read_order`.
- Do not map arbitrary roles from every client or privileged Keycloak management roles into application authorities.

User service must support listing role definitions, viewing effective/direct assignments, and assigning/removing approved roles. Add creation/editing of custom application role bundles from the existing permission catalog. A custom bundle cannot invent endpoint behavior, add unknown permissions, reference Keycloak administration roles, or introduce recursive composites.

`admin.access` is a client role on `api-gateway`. It admits a caller to admin routes and to `GET /api/v1/admin/me`. It is not an operation permission. user-service authorizes user and role operations with `user-service` client roles only. The permission summary returned for the admin UI is assembled from each permission's owning client and is separate from those authorities.

Only PLATFORM_ADMIN may create role bundles or grant privileged administration bundles. USER_ADMIN can assign a restricted allowlist of non-administrative staff roles. Enforce this in user service. Guard against self-elevation and removing/disabling the last active platform administrator. Record who changed whose access, before/after values, and result. Bootstrap the first platform administrator through a local setup script, not public registration.

## 8. User-service data and Keycloak integration

Store application profile data in user service. Keycloak remains authoritative for credentials, identity status, verified email, and role assignments.

Recommended tables in `userdb`:

```text
app_user(id, issuer, subject, display_name, email_snapshot, created_at, updated_at, version)
  UNIQUE(issuer, subject)
customer_profile(user_id PK/FK, preferences, created_at, updated_at)
customer_address(id, user_id FK, address_fields, created_at, updated_at)
staff_profile(user_id PK/FK, employee_reference, department, onboarding_status, created_at, updated_at)
identity_operation(id, idempotency_key, request_hash, target_identity, operation_type,
                   status, attempt_count, next_attempt_at, last_error, created_at, updated_at)
access_audit(id, actor_issuer, actor_subject, target_user_id, action, before_state,
             after_state, outcome, correlation_id, created_at)
```

One `app_user` can have both profile rows. This provides separate customer/staff data without duplicate login accounts. Do not create separate customer-password and admin-password tables. Do not store authoritative role assignments in a second application role database. A role cache or projection, if later needed, must be explicitly non-authoritative.

Customer profile creation is idempotent on first authenticated use; the identity comes from the validated JWT. Staff profile creation follows authorized onboarding. Never treat the existence of `staff_profile` as sufficient authorization. Profile email snapshots are not authorization keys; document refresh/reconciliation of identity metadata.

Order service stores an immutable shipping-address snapshot for historical orders. It does not depend on the current address profile to reconstruct old orders.

Keycloak administration:

1. Use its supported Admin REST API through an infrastructure adapter. Never query or update Keycloak's tables.
2. Obtain a client-credentials token server-side using a dedicated least-privilege client. Bootstrap realm/client configuration separately.
3. Verify permission names and fine-grained admin capabilities against the pinned Keycloak version. Document any API-level privilege broader than the application allowlist and its implications. Do not default the runtime account to realm-admin.
4. Keep credential setup/reset in Keycloak-hosted flows. Use a local mail catcher for invite/reset links if required.
5. For operations touching PostgreSQL and Keycloak, persist a durable operation record, execute bounded remote steps outside a long DB transaction, and reconcile outcomes. A Spring `@Transactional` method cannot atomically commit both systems.
6. Use stable operation identifiers and idempotency; distinguish an existing identity from one created by a particular operation. Never delete a pre-existing account as compensation.
7. Return `202 Accepted` with an operation status URL when a remote outcome is pending. Do not show success until confirmed. Record uncertain Keycloak outcomes and reconcile before replaying a create/grant blindly.
8. After a crash between successful role grant and local recording, reconciliation must discover the grant and complete the audit/state record. Document the temporary inconsistency explicitly.
9. Define race handling for simultaneous role assignment, staff suspension, and last-admin protection; serialize sensitive administration workflows as needed.

## 9. Database ownership and API conventions

Use a distinct PostgreSQL database and non-superuser login per data-owning service: `userdb`, `catalogdb`, `inventorydb`, and later `cartdb`, `orderdb`, `paymentdb`. Keycloak uses its own database/login. One PostgreSQL container can host these databases locally. The gateway has no business database.

No cross-database joins, foreign keys, shared entities, or direct SQL against another service. Reference other domains by identifiers and use APIs/events where required. Each service owns migrations and transactions.

Use UUID identifiers, BigDecimal money with defined scale/rounding and ISO currency, UTC instants, constraints, indexes, optimistic versions, bounded pagination, validated sorting, DTOs, and activation instead of deleting referenced products. Do not hold database transactions open across remote HTTP calls.

Use `/api/v1/...` externally. Document internal routes separately. Use Problem Details with validation details and correlation ID; pin the applicable standard/library behavior in the implementation. Use 401 for invalid/missing credentials, 403 for insufficient permission, 409 for conflicts, and a consistent 404 policy for inaccessible owned objects.

Suggested external routes:

| Route family | Destination and rule |
|---|---|
| `/api/v1/store/catalog/**` | Gateway → catalog service; anonymous reads only |
| `/api/v1/store/me/**` | Gateway → user service; own profile |
| `/api/v1/store/cart/**` | Gateway → cart service when that phase starts; own cart |
| `/api/v1/store/orders/**` | Gateway → order service when that phase starts; own orders |
| `/api/v1/admin/me` | Gateway → user service; portal entry plus UI permission summary |
| `/api/v1/admin/users/**` | Gateway → user service; specific user permission |
| `/api/v1/admin/roles/**` | Gateway → user service; read/assign/manage permission |
| `/api/v1/admin/catalog/**` | Gateway → catalog service; operation permission |
| `/api/v1/admin/inventory/**` | Gateway → inventory service when that phase starts |
| `/api/v1/admin/orders/**` | Gateway → order service when that phase starts |

Do not use an unrestricted wildcard proxy that accidentally exposes internal or admin endpoints. Public catalog access must not expose inactive products or unpublished prices.

Provide OpenAPI for each HTTP application, examples, documented security requirements, pagination, error responses, and idempotency behavior. Document Kafka schemas separately with event versions and compatibility rules.

## 10. Catalog and inventory: first business slice

Catalog owns Category, Product, ProductVariant/SKU, product descriptions, image URL metadata, prices, currency, and activation. Implement public list/detail/category APIs and protected create/update/activate operations. Include search, filtering, pagination, and batch SKU lookup. Validate uniqueness, positive/allowed prices, currency, and active relationships. Use image URLs initially; defer file uploads.

Inventory owns StockItem, StockAdjustment, Reservation, and ReservationLine. Start with stock setup, listing, and adjustment history; implement reservation behavior in the checkout phase.

- `available = on_hand - reserved`.
- `on_hand >= 0`, `reserved >= 0`, and `reserved <= on_hand` must hold in the database transaction.
- Adjustments use quantity deltas and mandatory reasons, not arbitrary edits to reserved stock.
- An adjustment cannot reduce on-hand below already reserved quantity.
- Validate SKU existence through catalog without querying catalog tables. SKU identity is immutable and deactivation does not delete inventory history.
- Atomically reserve all lines of an order or none; lock/update SKU rows in deterministic order to reduce deadlocks.
- Use database conditional updates or justified row locks, not JVM locks or Redis locks for stock correctness.
- Reservation commands are idempotent by order/operation identity and payload. A duplicate with conflicting quantities is a conflict, not a success.
- Releasing ACTIVE reservations decrements reserved once. Consuming ACTIVE reservations decrements both on-hand and reserved once. Replays must not change quantities again.
- Once consumed, cancellation follows the defined refund/restock policy, not a second reservation release.
- Do not expose exact warehouse quantities publicly unless deliberately required; storefront may receive availability and limited quantities.

Admin UI initially needs user/role screens, catalog CRUD forms, inventory adjustment forms, and readable error states. Defer aggregate dashboards.

## 11. Cart and order business rules — later milestone

Cart service owns persistent customer carts in PostgreSQL. Derive owner from validated identity. Permit positive bounded quantities for active SKUs. Cart prices are display snapshots only. Version carts and prevent checkout completion from deleting items added after checkout started.

Order creation receives an `Idempotency-Key`, cart version/reference, and address selection/snapshot input. Validate address ownership where a saved address is selected. Never accept customer identity, payment success, authoritative prices, or totals from the browser.

Order service fetches a consistent cart snapshot and authoritative catalog prices, calculates totals, and stores immutable order-line/address snapshots before starting the durable Saga. Define price-change handling: reject stale quoted totals with a review-required response rather than silently charging a different amount. Bound any quote validity window.

Enforce uniqueness on `(customer identity, operation, idempotency key)`, with a canonical request hash. Same key/same request returns the original result; same key/different request returns 409. Handle concurrent requests using database guarantees. Document retention and recovery behavior.

Keep order, payment, and fulfilment state distinct:

| Dimension | Example states |
|---|---|
| Order | CREATED, PENDING_STOCK, PENDING_PAYMENT, CONFIRMED, PROCESSING, CANCEL_PENDING, CANCELLED, REJECTED, MANUAL_REVIEW |
| Payment | NOT_STARTED, PENDING, SUCCEEDED, FAILED, REFUND_PENDING, REFUNDED |
| Fulfilment | NOT_STARTED, READY, OUT_FOR_DELIVERY, DELIVERED |

Publish an explicit transition table with actor, permission, preconditions, side effects, and compensation. Only order service changes its states. Record transition history and optimistic version. Prefer commands such as `/process`, `/dispatch`, `/deliver`, `/cancel`, not an arbitrary status setter.

Paid orders cannot be manufactured by an admin status edit. Payment success comes from the payment simulator's authenticated event. Customer cancellation is limited to the allowed pre-dispatch window. Order manager cancellation obeys the same domain constraints. Out-for-delivery/delivered orders are not cancellable in this MVP; returns are deferred.

## 12. Durable Saga, Kafka, and local payment simulation

Implement an orchestration Saga owned by order service. Do not put it in a portal backend. Introduce it when checkout is implemented; avoid building competing permanent sync/event modes.

Persist Saga state, step identifiers, deadlines, attempt counters, processed outcomes, and terminal result in orderdb. The coordinator must resume after restart without relying on an in-memory future or original HTTP request.

Normal flow:

1. Persist order, Saga, and ReserveStock command in one orderdb transaction; return an accepted/pending result.
2. Inventory consumes the command and commits the reservation plus StockReserved/StockRejected outbox message in one inventorydb transaction.
3. On StockReserved, order service persists advancement and a RequestPayment command.
4. Payment simulator persists an idempotent attempt and PaymentSucceeded/PaymentFailed event in paymentdb.
5. After payment success, order service requests reservation consumption. Inventory consumes reserved stock once and replies.
6. Only after required success acknowledgements does order service mark the order CONFIRMED. Cart cleanup targets the checked-out snapshot/version and is retryable independently.

Compensation and uncertainty:

- Stock rejection: no payment request; reject order.
- Definitive payment failure: request reservation release; do not claim cleanup completed until acknowledged.
- Successful payment followed by definitive inventory-consumption failure: enter compensation, request refund and appropriate reservation cleanup, and track both acknowledgements.
- Payment timeout is unknown, not proof of failure. Query/reconcile by stable payment operation ID before charging again or finalizing cancellation.
- Cancellation during payment or stock processing enters CANCEL_PENDING. Late payment success must trigger reconciliation/refund, not be ignored.
- Before stock consumption, cancel releases the reservation. After consumption but before dispatch, cancel uses a distinct idempotent restock operation plus refund if paid. Mark CANCELLED only after all required effects are confirmed.
- Failed compensation remains visible and retryable; exhausted automation enters MANUAL_REVIEW with a clear audit trail.
- Define reservation expiry in coordination with Saga deadlines. A sweeper cannot blindly free stock during an uncertain successful payment. Expiry and consumption races must be resolved atomically by reservation state/version.

Use a local simulator, never real cards or payment providers. It must support deterministic success, failure, delay/timeout, late success, duplicate response, and refund behavior. Clearly label every result as simulated. Give charge/refund operations stable idempotency identifiers.

Kafka requirements:

- Document topics, producers, consumer groups, partition keys, schemas, and retention.
- Use orderId as the key for order-specific messages; do not claim global ordering or ordering across topics.
- Use distinct consumer groups per logical subscriber and the same group across replicas of that subscriber.
- Envelope: `eventId`, `eventType`, `eventVersion`, `aggregateId`, `aggregateVersion` when applicable, `sagaId`, `commandId`, `correlationId`, `causationId`, `occurredAt`, `payload`.
- Never place access tokens, credentials, or unnecessary PII in events.
- Each producer commits domain changes and an outbox row in the same local transaction.
- Publisher workers use a claim/lease or appropriate row locking, broker acknowledgements, bounded retries, and recovery for abandoned claims. A crash after publish before marking sent causes a legitimate duplicate.
- Each consumer inserts `(consumer_name, event_id)` into a unique inbox/processed table and commits business changes plus any new outbox message in that same local transaction. A prior `exists()` call alone is not concurrency-safe deduplication.
- Acknowledge Kafka only after the database commit. Account for the crash between DB commit and offset commit.
- Deduplicate business operations as well as event IDs: different event IDs may still refer to the same reservation/refund command.
- Serialize or version-check Saga transitions. Duplicate, late, or out-of-order replies cannot revive a terminal state.
- Separate transient retries from poison messages; add bounded retries, dead-letter handling, and documented replay that preserves deduplication semantics.
- Explain at-least-once delivery and effectively-once business effects. Kafka producer idempotence or transactions do not atomically commit a PostgreSQL transaction.

## 13. Resilience, Redis, and graceful degradation

Protect synchronous calls with explicit timeouts and appropriate Resilience4j circuit breakers. Use small bounded retries with exponential backoff/jitter only for safe or demonstrably idempotent requests. Honor Retry-After where appropriate. Do not retry permission errors, validation errors, or invalid business transitions. Avoid retries at every layer and document total request latency budgets and decorator order.

Graceful degradation examples:

- Catalog browse may return a bounded stale cached value if the response indicates freshness limitations.
- An unavailable inventory service means availability unknown, not in-stock.
- Missing dashboard data may show partial/unavailable widgets.
- Checkout, stock mutation, access checks, and payment cannot fall back to fabricated success.
- Keycloak outage prevents new login/admin operations; already valid JWT verification may continue using cached signing keys where available, until token expiry. Unknown keys still fail closed.

Redis is introduced after the first business slice for catalog cache-aside, short TTLs with jitter, invalidation after successful writes, negative caching where useful, and a cache-stampede demonstration. Include `@EnableCaching`, `@Cacheable`, `@CacheEvict`, key design, serialization, and proxy/self-invocation behavior if Spring Cache is selected. Explain when direct RedisTemplate is justified.

Cache failure must not corrupt business state. Redis is not authoritative for stock, Saga, orders, or authorization. Document that invalidation can fail and TTL bounds staleness; stronger invalidation may use an outbox event. Cache invalidation should occur after transaction commit, not before it.

For token support, cache user-service's machine access token in server memory until shortly before expiry with a safe single refresh path. Do not put customer tokens into Redis just to satisfy a technology checklist. Redis-backed sessions/token persistence is deferred unless the architecture changes to a server-session BFF, with encryption and expiry then designed explicitly. Local JWT/JWKS validation does not require a Redis token database.

## 14. Local operations and observability

Use a dedicated Compose project name (`ecommerce-local-platform`), project-scoped networks and volumes, and independently generated local credentials. Do not attach to the old project’s databases, containers, networks, or volumes. If host ports are occupied, choose configurable free ports without stopping unrelated services.

Provide documented Compose profiles/scripts so the current milestone does not require future services. The running set is PostgreSQL, Keycloak, gateway, user/catalog/inventory services, and both frontends. Later full profile adds cart/order/payment, Kafka, and Redis. Optional observability tools remain separate.

Provide one command per supported milestone, for example `make dev-identity`, `make dev-admin`, and later `make dev-full`. Each must resolve to checked-in scripts/Compose commands. Also support infrastructure-in-Docker plus Java services from the IDE.

Use health checks, readiness and migrations, named volumes, graceful shutdown, reproducible seed scripts, and bounded JVM/container resources. Publish only required ports on loopback by default. Do not publish internal application or database ports externally. Document any debug profile exceptions.

Handle OIDC issuer routing deliberately: browser and containers must agree on the token issuer. Provide a tested local hostname/reverse-proxy arrangement or a documented internal JWKS URI with exact public issuer validation. Do not fix hostname mismatches by disabling issuer checks.

Baseline observability now: structured logs, safe correlation IDs, W3C trace propagation where supported, health/readiness, metrics, request duration, and sanitized audit logs. Later add local metrics/log/trace collection and dashboards. Measure Saga age, retry count, compensation failures, outbox backlog, consumer lag, and authorization failures. Restrict sensitive actuator endpoints.

## 15. Required verification and demonstrations

Use real infrastructure integration tests where database concurrency, Keycloak token behavior, Kafka delivery, or Redis semantics matter. Avoid tests that only mirror implementation details. Exercise actual OIDC login via browser tests; do not enable password grants to simplify testing.

Current milestone acceptance:

1. Anonymous visitors browse active catalog; protected operations reject them.
2. Customer registration/login creates an idempotent own profile and never staff privileges.
3. Admin creates/onboards staff and assigns an approved role through the gateway and user service.
4. Catalog creator can create but cannot update without editor permission.
5. Inventory manager adjusts stock with a reason but cannot manage user roles.
6. USER_ADMIN cannot assign PLATFORM_ADMIN, invent permissions, or elevate itself.
7. A dual-role account uses the same identity in both apps; its storefront token cannot execute admin calls, including attempts to request extra scopes.
8. Wrong audience, wrong issuer, expired token, ID token, and forged identity headers are rejected.
9. Direct service access cannot bypass permission checks; protected internal operations reject customer tokens.
10. Role changes are reflected after token refresh/re-login according to the documented policy.
11. Keycloak timeout and restart during onboarding recover without duplicate users or false success.
12. Both applications build; backend relevant tests pass; local startup and browser smoke test pass.

Later acceptance:

- Two customers cannot access each other's profile, cart, or orders, including guessed IDs and list filters.
- Concurrent reservations cannot oversell; duplicate reserve/release/consume/restock operations change stock once.
- Same checkout idempotency key under concurrency creates one order; conflicting payload is rejected.
- Restart coordinator/publisher/consumer at failure boundaries and prove recovery.
- Payment failure, timeout, late success, duplicate events, cancellation races, and failed compensation yield the documented states.
- Replay after DB commit/before Kafka offset commit has no duplicate business effect.
- Redis outage degrades browsing as documented; it does not allow stale pricing at checkout or fake stock.
- Invalid order transitions and arbitrary paid-state edits are rejected.
- Demonstrate breaker opening, bounded recovery, dead-letter replay, and observable unresolved compensation.

For each demo supply prerequisites, exact commands/UI steps, expected observations, cleanup that preserves unrelated data, and the concept it proves.

## 16. Phased implementation plan

| Phase | Deliverable | Exit gate |
|---|---|---|
| 0 — New-project foundation | Fresh project structure, ADRs, pinned stack, Compose infrastructure, gateway and domain services | New project builds independently; local routing works |
| 1 — Identity and administration | Keycloak clients, user service, profiles, permissions, user/role UI, recovery and audit | Real login and privilege isolation tests pass |
| 2 — Catalog slice | Public browsing and permission-based staff create/edit/activate flows | Customer/public/staff distinctions verified end to end |
| 3 — Inventory administration | Stock setup, safe adjustments, history, catalog integration | Invalid stock changes rejected; role boundaries verified |
| 4 — Cart and caching | Own carts, cart versions, authoritative checkout quote inputs, Redis catalog cache | Ownership and degraded-cache behavior verified |
| 5 — Checkout Saga | Order state machine, Kafka/outbox/inbox, inventory reservations, payment simulator | Success, failure, recovery, concurrency, and compensation demos pass |
| 6 — Fulfilment and admin views | Process/dispatch/deliver/cancel commands, order history, dashboard aggregation | Domain transitions and refunds/restock work correctly |
| 7 — Operational demonstration | Optional centralized telemetry, transport-security profile, fault scripts and portfolio guide | Repeatable end-to-end demonstrations and truthful completion report |

Current instruction: verify the prepared foundation, implement Phase 1, and stop with a reviewable result. Do not quietly expand into all later phases. Use the supplied starter modules; do not access or change the original application outside this repository.

## 17. Documentation and learning deliverables

Create concise documentation for:

- Architecture and service/data ownership, with compact Mermaid diagrams.
- Identity model, same-email decision, client/realm differences, token flow, audience mapping, and permission matrix.
- Profile vs identity ownership and user-service/Keycloak reconciliation.
- Order transition table and later Saga success/compensation sequences.
- Database migration and API/event versioning rules.
- Local startup, URLs, secret generation, seed accounts, host/container networking, tests, and troubleshooting.
- ADRs: one realm, SPA token model, domain-owned rules, database-per-service, order-owned Saga, and Redis's limited role.
- An implementation status table separating implemented, executed/verified, planned, and blocked items.

For each important pattern explain: the problem, chosen implementation, alternative, failure mode, and a two-minute interview answer grounded in code actually present. Cover Spring security annotations, transaction boundaries, cache annotations, retry/circuit-breaker ordering, outbox/inbox, and data ownership. Do not claim portfolio experience for features not yet built.

## 18. First action

Read the handoff documents and verify the scaffold in the user's WSL2 environment. Then implement Phase 1 (identity and administration), preserving the retained catalog code. Do not create a nested project or reinitialize the workspace. Follow the phase gate in IMPLEMENTATION_PLAN.md; proceed beyond it only when requested. Finish with actual verification results and next-phase readiness.

---

## Design reference notes for the implementer

This prompt adapts the supplied `Ecommerce_AWS_Learning_Master_Cursor_Prompt(2).md` and handwritten architecture sketch. It deliberately changes the earlier four-service/two-role approach, removes AWS work from scope, and moves identity ahead of catalog/inventory development. The Saga, role matrix, schemas, and phase plan here are project design decisions, not claims that the reference project already implements them.

Consult official documentation for the pinned versions, especially:

- Keycloak server administration: https://www.keycloak.org/docs/latest/server_admin/
- Keycloak Admin REST API: https://www.keycloak.org/docs-api/latest/rest-api/index.html
- Spring Security JWT Resource Server: https://docs.spring.io/spring-security/reference/servlet/oauth2/resource-server/jwt.html
- OAuth security best current practice, RFC 9700: https://www.rfc-editor.org/rfc/rfc9700.html

These sources were checked when preparing this prompt on 2026-09-30. Verify version-specific configuration during implementation. Keycloak role scope mappings and token audiences must be tested with actual issued tokens; Spring scope conversion and the custom permission conversion described above are distinct mechanisms.
