<!-- IMPL-REVIEW-REPORT -->
# Implementation Review: Coupon Discount Service — Implementation Plan

- **Plan**: context/changes/coupon-service/plan.md
- **Scope**: Full plan — Phases 1–5 of 5
- **Date**: 2026-08-29
- **Verdict**: APPROVED (F1 fixed; F2–F5 noted)
- **Findings**: 0 critical, 1 warning, 4 observations

## Verdicts

| Dimension | Verdict |
|-----------|---------|
| Plan Adherence | PASS |
| Scope Discipline | PASS |
| Safety & Quality | WARNING |
| Architecture | PASS |
| Pattern Consistency | PASS |
| Success Criteria | PASS |

## Findings

### F1 — Framework-level errors bypass the RFC 7807 contract

- **Severity**: ⚠️ WARNING
- **Impact**: 🔎 MEDIUM — real tradeoff; pause to reason through it
- **Dimension**: Safety & Quality
- **Location**: src/main/java/com/example/coupons/adapter/web/ApiExceptionHandler.java
- **Detail**: The advice maps the domain and bean-validation exceptions to
  `application/problem+json` with a stable `code`. But errors raised by Spring MVC itself —
  a malformed JSON body (`HttpMessageNotReadableException`), a wrong `Content-Type`
  (`HttpMediaTypeNotSupportedException`), an unknown route (404), a wrong verb (405), or any
  unforeseen `RuntimeException` — fall through to Spring Boot's default error response:
  `{"timestamp":…,"status":400,"error":"Bad Request","path":…}` with **no `code` field and
  not `problem+json`**. The README advertises a uniform error contract with a code
  catalogue, so this is an observable inconsistency on exactly the surface a reviewer
  probes. (Raised as F2 in the Phase 2 review and deferred to Phase 5, whose Changes
  Required ultimately only covered the README — it was never actioned.)
- **Fix**: In `ApiExceptionHandler`, add `@ExceptionHandler(HttpMessageNotReadableException.class)`
  → 400 `MALFORMED_REQUEST` and a catch-all `@ExceptionHandler(Exception.class)` → 500
  `INTERNAL_ERROR`, both via the existing `problem(...)` helper. For the container's own
  4xx (404/405/415), make the class `extends ResponseEntityExceptionHandler` and override
  `handleExceptionInternal` to re-wrap the body as `ProblemDetail` with a `code`. ~15–25
  lines, no new dependency.
  - Strength: Closes the contract gap with the pattern already in the file; every error
    response then carries a `code`.
  - Tradeoff: `ResponseEntityExceptionHandler` brings a base class with many overridable
    hooks — slightly more surface to understand.
  - Confidence: HIGH — this is the standard Spring 6 approach and the `problem(...)` helper
    already produces the right shape.
  - Blind spot: Haven't checked whether springdoc's own error paths (`/v3/api-docs`) would
    also be reshaped — they should be left alone.
- **Decision**: FIXED — added a ResponseEntityExceptionHandler-based advice; malformed body / wrong method / wrong media type / unknown route now all return problem+json with a `code` (MALFORMED_REQUEST / METHOD_NOT_ALLOWED / UNSUPPORTED_MEDIA_TYPE / NOT_FOUND) plus a catch-all INTERNAL_ERROR. Verified live.

### F2 — Database credentials default to `coupons` / `coupons`

- **Severity**: 🔵 OBSERVATION
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Safety & Quality
- **Location**: src/main/resources/application.yml:8-10, docker-compose.yml:9-11
- **Detail**: `spring.datasource.username/password` default to `coupons` when the env vars
  are unset, and `docker-compose.yml` hard-codes the same. Appropriate for local dev and a
  take-home, and the README states production supplies `DB_USERNAME` / `DB_PASSWORD` via the
  environment — but there is no profile or fail-fast that stops the app booting with the
  default password outside dev.
- **Fix**: Leave as-is for this task; the README already documents the production path. If
  desired, drop the password default (`${DB_PASSWORD}` with no fallback) so a missing value
  fails fast.
- **Decision**: NOTED — left as-is; README documents the production env-var path.

### F3 — Redemption outcome logging catches `RuntimeException` broadly

- **Severity**: 🔵 OBSERVATION
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Safety & Quality (reliability)
- **Location**: src/main/java/com/example/coupons/application/RedeemCouponService.java:70-88
- **Detail**: `redeem(...)` wraps its body in `try { … } catch (RuntimeException ex) { log.info("redemption outcome={}", ex.getClass().getSimpleName()); throw ex; }`.
  For the known domain outcomes this is exactly right. But an unexpected failure (DB
  connection dropped mid-transaction, a bug) is also logged at **INFO** as
  `outcome=SomeException` and rethrown — an infrastructure error rendered as a routine
  business-outcome line, at a level that won't stand out.
- **Fix**: Catch only the known domain exceptions for the outcome log
  (`CouponNotFoundException`, `CountryNotAllowedException`, `CountryNotDeterminedException`,
  `AlreadyRedeemedException`, `UsageLimitReachedException`); let anything else propagate to
  the `@RestControllerAdvice` unlogged here (or log it at ERROR).
- **Decision**: NOTED — left as-is for now.

### F4 — `ClientIpResolver` and `CorrelationIdFilter` are `public`; sibling web classes are package-private

- **Severity**: 🔵 OBSERVATION
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Pattern Consistency
- **Location**: src/main/java/com/example/coupons/adapter/web/{ClientIpResolver,CorrelationIdFilter}.java
- **Detail**: The controllers and `ApiExceptionHandler` in this package are package-private
  (a deliberate convention established in Phase 2). These two `@Component`s are `public`
  without needing to be — `ClientIpResolver` is only injected within the package, and a
  package-private `@Component` filter works fine. Their tests are in the same package.
- **Fix**: Drop `public` from both classes (and `ClientIpResolver.resolve`) to match the
  package convention.
- **Decision**: NOTED — left as-is for now.

### F5 — Success-criteria rows 1.4 and 5.3 remain unchecked (no git repository)

- **Severity**: 🔵 OBSERVATION
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Success Criteria
- **Location**: context/changes/coupon-service/plan.md (Progress §, rows 1.4 and 5.3)
- **Detail**: Both read "CI … green on … push". The GitHub Actions workflow
  (`.github/workflows/ci.yml`) is valid, but there is no git repository (the user chose to
  defer version control), so "green on push" cannot be observed. This is not an
  implementation gap — the task does require the project to end up on a public repo.
- **Fix**: `git init` on `main`, commit the project (`.gitignore` is already in place), push
  to a public remote, confirm the CI run is green, then tick 1.4 / 5.3.
- **Decision**: NOTED — pending git setup; workflow file is valid.

## Automated Success Criteria — Verified

`./mvnw clean verify` → **BUILD SUCCESS**

| | Result |
|---|---|
| Unit tests (`*Test`) | pass, 0 failures |
| Integration tests (`*IT`, Testcontainers PostgreSQL) | pass, 0 failures |
| `RedemptionConcurrencyIT` (the concurrency proof) | pass — 200 parallel distinct-user redemptions → exactly 50 succeed; 100 same-user → exactly 1 |
| ArchUnit (`ArchitectureTest`) | 6 rules pass — domain purity, adapter isolation ×3, no-infra-libs, layered architecture |
| JaCoCo gate (domain + application ≥ 80% line) | met — "All coverage checks have been met" |
| Liquibase `0001` + `0002` | apply cleanly on a fresh container |
| `build-info` at `/actuator/info` | served |
| `scripts/smoke.sh` | 16/16 checks pass against a running instance |

Rows 1.4 and 5.3 (CI green on push) are pending git setup — see F5.

## Notes

- **Deviations from the plan, all deliberate and documented in phase reports:**
  - Testcontainers pinned to 1.20.6 and `docker.api.version=1.41` (surefire/failsafe system
    property) — works around modern Docker Engine rejecting docker-java's default API v1.32.
  - `RedeemCouponService` uses a `TransactionTemplate` (isolation `READ_COMMITTED`) rather
    than `@Transactional` on a method — avoids Spring's self-invocation proxy trap and keeps
    the geo-IP call cleanly outside the transaction.
  - `incrementUsageIfBelowLimit` query landed in Phase 2 (one phase early); wired in Phase 3.
  - No dedicated `ForwardedHeadersConfig` — `server.forward-headers-strategy=framework`
    already registers Spring's `ForwardedHeaderFilter`; a second bean would double-register.
  - Structured logging via `logstash-logback-encoder` (Spring Boot 3.3 predates native
    structured logging); plain console for `dev`/`test`, JSON otherwise.
  - `PersistenceExceptions.isUniqueViolation` extracted (was duplicated across two adapters).
  - The Liquibase `db.changelog-master.yaml` `includeAll` path was corrected in Phase 2
    (`db/changelog/changes/` → `changes/` relative-to-changelog) once a real changeset
    exposed the latent bug.
- **Test-suite size:** the user flagged over-testing after Phase 3; ~16 redundant tests were
  removed (duplicate slice-vs-IT coverage, parameterized-case bloat). The suite now stands
  at 72 tests (62 unit + 10 integration) after a second trim on the user's request, weighted toward the load-bearing ones (concurrency, domain invariants, one
  end-to-end IT per surface, geo-IP adapter).
- No git repository exists (user's explicit choice, three times), so change-scope detection
  was done from the known Phase 1–5 file set rather than a diff. No phase-end commits were
  made; `change.md` is at `status: implemented`.
