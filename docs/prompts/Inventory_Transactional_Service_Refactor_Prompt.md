Refactor inventory-service's StockCommandService to use a separate Spring-managed transactional service instead of TransactionTemplate.

Inspect the actual current code, AGENTS.md and applicable Cursor rules first. This is a structural refactor: preserve existing stock rules, HTTP contracts, error codes, permissions, lock timeout, version checks, idempotency scope/fingerprint, stored response replay and MapStruct mappings. Do not change database migrations, tables or triggers, or implement another phase.

1. Create StockTransactionService with constructor injection.

Move the database write units into public methods:
- writeSetup(...)
- writeAdjustment(...)
- recordSetupConflict(...) for the transaction currently inside recoverSetup().

Annotate those entry methods with @Transactional(propagation = Propagation.REQUIRED). Call them through the injected Spring bean, not same-object self-invocation.

Move the transactional helpers with them: claim(), setLockTimeout(), finishSuccess(), finishProblem(), and the persistence/serialization/mapping dependencies they need. Keep private helpers private; they execute under the public entry method's transaction. Reuse existing value objects and small immutable input records where helpful to avoid unwieldy signatures. Do not introduce generic command frameworks or a shared mapper/domain JAR.

2. Keep StockCommandService as the coordinator.

Keep request validation/normalization/fingerprinting, completedReplay(), catalog permission checks, remote catalog lookup and activation validation, exception classification and recovery coordination here. Remove TransactionTemplate and PlatformTransactionManager from this coordinator.

Replace transactions.execute(...) with stockTransactionService.writeSetup(...) or writeAdjustment(...). In recoverSetup(), replace its template callback with stockTransactionService.recordSetupConflict(...).

Keep the DataIntegrityViolationException and lock-timeout catch blocks around the injected writer calls. They must run after the failed writer transaction has ended. Do not catch a database constraint violation inside the writer and then continue querying/saving in the same aborted transaction.

Avoid circular dependencies. Extract a small service-local helper for shared replay/serialization/problem-building only if useful; do not make the writer call back into the coordinator.

3. Enforce transaction boundaries explicitly.

Annotate public coordinator setup()/adjust() with @Transactional(propagation = Propagation.NEVER) so an accidental transactional caller fails immediately instead of making catalog lookup and recovery join an outer transaction. These methods run without a transaction; repository reads may use their own short transactions.

The injected writer uses REQUIRED and starts the write transaction because the coordinator has none. Do not blanket-switch to REQUIRES_NEW. Inspect class-level annotations and callers to ensure no surrounding transaction remains.

Catalog HTTP lookup must stay outside the stock write transaction. Commit or rollback must finish before returning to the coordinator. Stock, adjustment history and completed idempotency result must remain atomic.

Preserve intentional finishProblem() outcomes: existing stale-version/not-found/invariant failures stored as completed commands are returned results and commit their command record. Do not replace them with thrown exceptions that roll back those records. Database failures still roll back the entire write.

A completed replay must still happen before today's version validation or catalog lookup. Duplicate-key recovery must read the winning command after rollback; setup uniqueness recovery must record its conflict in a fresh writer call. Preserve fingerprint-conflict behavior and exact saved response replay.

4. Verify the refactor with real Spring transaction proxies.

Update existing tests and add focused integration coverage where missing:
- successful setup/adjustment atomically commit stock, history and command result;
- injected persistence failure rolls everything back;
- same-key duplicate recovery happens after rollback and replays the winner once;
- variant/SKU uniqueness recovery records the setup conflict in a new transaction;
- stale-version/business errors remain stored and replayable;
- lock-timeout behavior remains unchanged;
- catalog adapter is called with no active transaction;
- writer entry methods execute with an active transaction;
- coordinator entry points reject an existing transaction.

Use Spring-managed services and PostgreSQL for transaction/concurrency verification; mocks and manually constructed objects cannot verify the proxy boundary. Preserve existing concurrent adjustment and idempotency tests.

Run relevant inventory tests while working, then make check and make backend-verify. Run make inventory-check against the available local stack; record blockers honestly. Keep unrelated frontend/infrastructure unchanged and do not reset data, delete volumes, print secrets or use Python tooling.

Update docs/phase-3.md and progress/verification notes to describe the new boundary, actual commands/results and any blockers. Finish with changed files, preserved behavior and verification evidence. Stop after this refactor; do not push or publish.

