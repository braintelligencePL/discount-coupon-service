<!-- IMPL-REVIEW-REPORT -->
# Implementation Review: Coupon Discount Service — Implementation Plan

- **Plan**: context/changes/coupon-service/plan.md
- **Scope**: Phase 2 of 5 (Domain Model & Coupon Creation)
- **Date**: 2026-08-29
- **Verdict**: APPROVED (all findings triaged — accepted as-is)
- **Findings**: 0 critical, 1 warning, 4 observations

## Verdicts

| Dimension | Verdict |
|-----------|---------|
| Plan Adherence | PASS |
| Scope Discipline | WARNING |
| Safety & Quality | PASS |
| Architecture | PASS |
| Pattern Consistency | PASS |
| Success Criteria | PASS |

## Findings

### F1 — Phase 3 query method pulled forward into Phase 2

- **Severity**: ⚠️ WARNING
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Scope Discipline
- **Location**: src/main/java/com/example/coupons/adapter/persistence/CouponCrudRepository.java:22-35
- **Detail**: `incrementUsageIfBelowLimit(long)` — the atomic conditional `UPDATE` that
  enforces the usage cap — was added in Phase 2. The plan places it in Phase 3
  ("Persistence — atomic UPDATE & redemption table"). It has no caller yet, so it is
  inert dead code for one phase, but it is out of the phase's declared scope.
- **Fix**: Leave it — Phase 3 wires it in the very next step and the method documents the
  repository's intended shape. Alternative: delete it now and re-add in Phase 3. Recommend
  leaving it; it is tested implicitly (compiles, mapped by Spring Data) and removing/re-adding
  is churn with no benefit.
- **Decision**: SKIPPED — accepted as-is; Phase 3 wires it next

### F2 — No catch-all exception handler; unexpected errors bypass RFC 7807

- **Severity**: 🔵 OBSERVATION
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Safety & Quality
- **Location**: src/main/java/com/example/coupons/adapter/web/ApiExceptionHandler.java
- **Detail**: The advice maps known domain/validation exceptions. Anything unmapped (e.g. a
  malformed JSON body → `HttpMessageNotReadableException`, or an unforeseen `RuntimeException`)
  falls through to Spring Boot's default error response — not `application/problem+json`, and
  potentially more verbose than desired. The plan's Phase 5 ("Hardening, docs & observability")
  is the declared home for this.
- **Fix**: Defer to Phase 5, which already owns error-contract hardening. If desired sooner,
  add `@ExceptionHandler({HttpMessageNotReadableException.class})` → 400 `MALFORMED_REQUEST`
  and `@ExceptionHandler(Exception.class)` → 500 `INTERNAL_ERROR`, both as `ProblemDetail`.
- **Decision**: SKIPPED — deferred to Phase 5 (error-contract hardening)

### F3 — Wire DTO couples to a domain constant

- **Severity**: 🔵 OBSERVATION
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Architecture
- **Location**: src/main/java/com/example/coupons/adapter/web/dto/CreateCouponRequest.java:3,21
- **Detail**: `CreateCouponRequest` imports `domain.model.CouponCode` to reuse
  `CouponCode.MAX_LENGTH` in its `@Size(max = …)`. This is an inward (adapter → domain)
  dependency, which hexagonal architecture permits, and it keeps the length limit defined
  once. Noted only because it is a (benign) coupling of the wire contract to a domain detail.
- **Fix**: Leave as-is — single source of truth for the limit outweighs the mild coupling.
- **Decision**: SKIPPED — accepted as-is; single source of truth for the limit

### F4 — `config/ApplicationConfig` not listed in the plan's Changes Required

- **Severity**: 🔵 OBSERVATION
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Scope Discipline
- **Location**: src/main/java/com/example/coupons/config/ApplicationConfig.java
- **Detail**: A `Clock` `@Bean` was added. The plan does not list the file explicitly but
  calls for `Clock` injection in "Critical Implementation Details" and a fixed `Clock` in
  success criterion 2.1, so the bean is implied and necessary.
- **Fix**: None — expected supporting change.
- **Decision**: ACCEPTED — expected supporting change, no action

### F5 — Manual success criteria pre-verified but not yet human-confirmed

- **Severity**: 🔵 OBSERVATION
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Success Criteria
- **Location**: context/changes/coupon-service/plan.md (Progress §, rows 2.7–2.10)
- **Detail**: Rows 2.7–2.10 were exercised against a running instance during implementation
  (create → 201 + Location, case-insensitive GET → 200, duplicate → 409 problem+json,
  OpenAPI documents 201/400/409 and 200/404). They remain `- [ ]` pending the human
  confirmation gate.
- **Fix**: Confirm the manual walkthrough, then tick 2.7–2.10.
- **Decision**: RESOLVED — manual walkthrough confirmed 2026-08-29; rows 2.7–2.10 ticked

## Automated Success Criteria — Verified

`./mvnw clean verify` → **BUILD SUCCESS**

| Row | Command / check | Result |
|-----|-----------------|--------|
| 2.1 | Domain unit tests (`CouponCode` 10, `Country` 11, `UsageLimit` 4, `Coupon` 7) | PASS |
| 2.2 | `@WebMvcTest` slice (`CouponControllerTest` 6) | PASS |
| 2.3 | Testcontainers integration (`CouponPersistenceIT` 3, `CouponApiIT` 4) | PASS |
| 2.4 | Liquibase `0001` applies on a fresh container | PASS (includeAll path bug fixed) |
| 2.5 | ArchUnit (`ArchitectureTest` 4 rules) | PASS |
| 2.6 | JaCoCo gate — domain + application ≥ 80% line | PASS ("All coverage checks have been met") |

Totals: 49 unit + 7 integration tests, 0 failures, 0 errors.

## Notes

- The Liquibase `db.changelog-master.yaml` `includeAll` path was corrected this phase
  (`db/changelog/changes/` → `changes/` with `relativeToChangelogFile: true`). The bug was
  latent in Phase 1 because the directory was empty; migration `0001` exposed it. This is an
  in-scope fix, not drift.
- springdoc `@Operation` / `@ApiResponses` annotations were added to `CouponController` so the
  generated OpenAPI accurately reports 201/400/409 (POST) and 200/404 (GET), satisfying
  success criterion 2.10.
- No git repository exists yet (per the user's choice to defer version control), so
  change-scope detection was done from the known Phase 2 file set rather than a diff.
