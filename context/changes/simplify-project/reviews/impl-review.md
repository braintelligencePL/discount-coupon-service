<!-- IMPL-REVIEW-REPORT -->
# Implementation Review: Simplify the coupon-service Implementation Plan

- **Plan**: context/changes/simplify-project/plan.md
- **Scope**: Full plan — Phases 1–3 of 3
- **Date**: 2026-08-31
- **Verdict**: APPROVED
- **Findings**: 0 critical, 0 warnings, 4 observations

## Verdicts

| Dimension | Verdict |
|-----------|---------|
| Plan Adherence | PASS |
| Scope Discipline | PASS |
| Safety & Quality | PASS |
| Architecture | PASS |
| Pattern Consistency | PASS |
| Success Criteria | PASS |

## Findings

### F1 — Circuit breaker runs on library defaults; the circuit-open branch is now defensive-only

- **Severity**: 🔵 OBSERVATION
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Safety & Quality
- **Location**: src/main/resources/application.yml (no `resilience4j:` section), src/main/java/com/example/coupons/adapter/geoip/IpApiGeoIpResolver.java:61-69
- **Detail**: Phase 1 removed all `resilience4j.*` config; Phase 3 removed the retry wrapper.
  `circuitBreakerRegistry.circuitBreaker("geoip")` now uses Resilience4j defaults, notably
  `minimumNumberOfCalls = 100` and `slidingWindowSize = 100`. Under any realistic
  small-deployment traffic the breaker will not open — the manual check in Phase 3.6 shows
  no "circuit open" log line after 4 consecutive provider failures (only the per-call
  `geo-IP lookup failed … Connection refused` fail-closed path fires). This exactly matches
  the consequence recorded in the plan's Open Risks, and `IpApiGeoIpResolverTest` still
  proves the circuit-open branch works by constructing its own registry with a low
  threshold. So the country rule still fails closed correctly on every provider error; only
  the "stop hammering a dead provider" bulkhead is effectively inert.
- **Fix**: Accept as the recorded tradeoff, or re-add one line —
  `resilience4j.circuitbreaker.instances.geoip.minimum-number-of-calls: 10` — to make the
  breaker actually engage while keeping the rest on defaults.
- **Decision**: PENDING

### F2 — `spring-boot-starter-aop` may now be an unused dependency

- **Severity**: 🔵 OBSERVATION
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Safety & Quality (dependencies)
- **Location**: pom.xml (dependencies)
- **Detail**: The geo-IP adapter wraps the call with **programmatic** Resilience4j
  (`CircuitBreaker.decorateSupplier(...)`), not `@CircuitBreaker` / `@Retry` annotations
  (`grep` for those annotations in `src` returns nothing). With the retry code gone too, the
  only Resilience4j feature in use is the `CircuitBreakerRegistry` bean. `spring-boot-starter-aop`
  was added in the original build for the annotation-driven path that never materialised.
- **Fix**: Remove `spring-boot-starter-aop` from `pom.xml` and run `./mvnw -B verify`. If the
  context still starts (the `resilience4j-spring-boot3` autoconfiguration registers aspect
  beans that may need AOP on the classpath even when unused), keep the removal; if it fails,
  restore it with a one-line comment explaining why.
- **Decision**: PENDING

### F3 — Plan Progress row 3.3 states the wrong test count for `CouponRedemptionCountryIT`

- **Severity**: 🔵 OBSERVATION
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Plan Adherence
- **Location**: context/changes/simplify-project/plan.md (Progress §, row 3.3)
- **Detail**: Row 3.3 reads "`CouponRedemptionCountryIT` runs 3". The class has 2 test
  methods and runs 2 (green). Plan-authoring typo — the class was never intended to gain a
  third test in this change.
- **Fix**: Correct row 3.3 to "runs 2".
- **Decision**: PENDING

### F4 — Three edits outside the plan's Changes Required lists

- **Severity**: 🔵 OBSERVATION
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Scope Discipline
- **Location**: src/test/java/com/example/coupons/RedemptionConcurrencyIT.java,
  src/main/java/com/example/coupons/application/{CreateCouponCommand,RedeemCouponCommand}.java,
  src/main/java/com/example/coupons/adapter/geoip/IpApiGeoIpResolver.java:87
- **Detail**:
  1. `RedemptionConcurrencyIT` injected `RedeemCouponService` and would not compile after the
     Phase 2 merge — retargeted to `CouponService` (2 tests still pass). Necessary; called
     out in the Phase 2 completion notes.
  2. The two command records had `{@link RedeemCouponService}` / `{@link CreateCouponService}`
     Javadoc links that would dangle after the merge — repointed to `{@link CouponService}`.
  3. `IpApiGeoIpResolver.callProvider` gained a `log.warn("geo-IP returned an unrecognised
     country code: …")` — the removed `CountryResolution.undetermined(reason)` used to carry
     that diagnostic; without a reason-carrying type the log line preserves it.
- **Fix**: None — all three are forced consequences of planned changes or trivial diagnostics.
- **Decision**: PENDING

## Automated Success Criteria — Verified

`./mvnw -B verify` (JDK 21) → **BUILD SUCCESS** — 23 unit + 6 integration tests, 0 failures.

| Phase | Criteria | Result |
|-------|----------|--------|
| 1 | `mvn verify`; no `package-info.java`; no `jacoco` in pom; no `resilience4j` in yml; no `ApplicationConfig` refs; ArchUnit green | PASS (all `grep -c` → 0) |
| 2 | `mvn verify`; no `Create/Get/RedeemCouponService` refs; `CouponServiceTest` 2, `CouponControllerTest` 4; ArchUnit green | PASS |
| 3 | `mvn verify`; no `CountryResolution` refs; `IpApiGeoIpResolverTest` 5, `CouponRedemptionCountryIT` 2, `CouponServiceTest` 2; ArchUnit green | PASS |

Manual rows 1.6–1.8, 2.5–2.7, 3.5–3.7 were exercised against a running instance during
implementation (app boots, JSON logs with `correlationId`, full create/redeem/country
walkthrough, geo-IP-unreachable fail-closed) and confirmed by the user at each phase gate.

## Notes

- **Outcome**: 51 → 40 main Java files, ~1,659 → ~1,512 LoC. One `CouponService` replaces
  three services; `Optional<Country>` replaces the `CountryResolution` record; no JaCoCo
  plugin, no `package-info.java`, no `smoke.sh` / CI workflow, no custom `resilience4j.*`
  config, no `ApplicationConfig` class.
- **Protected as planned**: framework-free domain + ports (ArchUnit 2 rules green throughout),
  hexagonal packages, observability (correlation id, JSON logs, build-info, probes),
  springdoc + annotations, `ClientIpResolver` + dev override, `PersistenceExceptions`, the 7
  exception classes, the persistence `Row`/`RowMapper` split, the Maven wrapper, Docker files,
  and the application-layer command/result records.
- **API contract unchanged**: same status codes, `problem+json` shapes, and OpenAPI document
  before and after; verified by the untouched `CouponApiIT` / `CouponRedemptionApiIT` /
  `RedemptionConcurrencyIT` and by manual `curl`.
- No git repository exists (the project was never initialised), so scope detection used the
  known Phase 1–3 file set rather than a diff, and no phase produced a commit. `change.md` is
  at `status: implemented`.
