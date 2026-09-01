# Simplify the coupon-service Implementation Plan

## Overview

The coupon-service works and is well-reviewed, but at 51 main Java files / ~1,659 lines for a
3-endpoint service the file-to-logic ratio is high. This change removes genuine bloat —
doc-only files, a coverage-gate build apparatus, a bespoke value type where a JDK one fits,
three one-method services — **without touching the design choices the recruitment task
explicitly grades**: a framework-free domain, ports, the hexagonal package layout, and the
Docker/Testcontainers setup.

Three phases, ordered by risk: config/dead-weight (no behaviour change) → structural merge →
one port-contract change.

## Current State Analysis

- **Application layer**: `CreateCouponService` (32 LoC), `GetCouponService` (29 LoC),
  `RedeemCouponService` (129 LoC) — three `@Service` classes, two of them one-method
  wrappers. `CouponController` injects the first two; `CouponRedemptionController` injects the
  third plus `ClientIpResolver`.
- **`domain/model/CountryResolution.java`** (28 LoC) — a record wrapping
  `resolved(Country)` | `undetermined(reason)` with `isResolved()`. The `reason` string is
  only ever used in one `log.warn` line inside `IpApiGeoIpResolver`. `GeoIpResolver.resolve`
  returns it; `RedeemCouponService.checkCountryRestriction` consumes it
  (`src/main/java/com/example/coupons/application/RedeemCouponService.java:107-114`).
- **`IpApiGeoIpResolver`** (115 LoC) programmatically wraps the `ip-api.com` call with a
  Resilience4j **circuit breaker** and **bounded retry** (both from injected registries, not
  annotations) plus a **Caffeine cache**. `application.yml:49-65` configures explicit
  `resilience4j.circuitbreaker.instances.geoip` and `resilience4j.retry.instances.geoip`
  blocks.
- **`pom.xml`** carries `jacoco-maven-plugin` with four executions (`prepare-agent`,
  `prepare-agent-integration`, `merge`, `report`) plus a build-breaking `check` that gates
  domain+application line coverage at 80% (`<jacoco.line.coverage>`).
- **`config/ApplicationConfig.java`** (22 LoC) declares one bean: `Clock clock()`.
- **5 × `package-info.java`** — `domain/model`, `domain/port`, `domain/exception`,
  `application`, `adapter/web`, `adapter/web/dto`, `adapter/persistence`, `adapter/geoip`
  (whichever exist) — Javadoc only, no code.
- **`scripts/smoke.sh`** (curl walkthrough) and **`.github/workflows/ci.yml`** exist; the
  README references both, and the `coupon-service` plan's Progress rows 1.4 / 5.3 depend on
  CI.
- **Test suite**: 23 unit + 6 integration. `RedeemCouponServiceTest` (2 tests) constructs
  `RedeemCouponService` directly. `IpApiGeoIpResolverTest` (5) builds its own R4j registries.
  `CouponRedemptionCountryIT` has a nested `StubGeoIpResolver` returning `CountryResolution`.
  `CouponControllerTest` mocks `CreateCouponService` + `GetCouponService`.

### Key Discoveries

- The merged `CouponService` constructor is **identical** to today's `RedeemCouponService`
  constructor (`CouponRepository`, `CouponRedemptionRepository`, `GeoIpResolver`,
  `PlatformTransactionManager`, `Clock`) — `create`/`getByCode` need only a subset of those.
  So `RedeemCouponServiceTest` barely changes.
- Removing the `resilience4j.*` YAML leaves `circuitBreakerRegistry.circuitBreaker("geoip")`
  working on **library defaults** (sliding window 100, `minimumNumberOfCalls` 100), so the
  breaker will not open under normal small-deployment traffic. The per-error fail-closed
  behaviour and the cache still carry the resilience story; the circuit-open branch becomes
  defensive-only. `IpApiGeoIpResolverTest` is unaffected — it builds its own registry with a
  low threshold.
- `ArchitectureTest`'s `respects_hexagonal_layering` rule references a `Config` layer;
  `config/` still contains `GeoIpProperties` after `ApplicationConfig` is deleted, so the
  layer is non-empty and the rule is unaffected. Deleting `package-info.java` files does not
  affect ArchUnit.
- `management.endpoints.web.exposure.include: health,info` + the `build-info` goal on
  `spring-boot-maven-plugin` are independent of JaCoCo and stay (observability kept).

## Desired End State

- Application layer is one `CouponService` with `create` / `getByCode` / `redeem`.
- `GeoIpResolver.resolve(String ip)` returns `Optional<Country>` (empty ⇒ fail closed).
  `CountryResolution` is gone.
- `IpApiGeoIpResolver` keeps the circuit breaker + Caffeine cache; the bounded-retry wrapper
  is gone. `application.yml` has no `resilience4j.*` section.
- `pom.xml` has no `jacoco-maven-plugin` and no coverage-gate property; the Surefire/Failsafe
  split, `build-info`, the Testcontainers version pin and the `docker.api.version` property
  remain.
- `config/ApplicationConfig.java` is gone; the `Clock` bean lives on `CouponsApplication`.
- No `package-info.java`, no `scripts/smoke.sh`, no `.github/workflows/ci.yml`.
- README reflects all of the above.
- `./mvnw verify` is green after every phase; the app still boots, serves all three
  endpoints, and geo-IP still fails closed on provider error / undetermined country.

**Verification**: `./mvnw -B verify` (JDK 21) passes at the end of each phase;
`git diff --stat` shows a net reduction of ~12 files; `ArchitectureTest` still passes;
manual `curl` of create / redeem / country-restricted redeem still produces the documented
outcomes.

## What We're NOT Doing

- **Not** collapsing hexagonal to layered — the framework-free domain, the ports, and the
  `adapter/{web,persistence,geoip}` split all stay; `ArchitectureTest` rules are unchanged.
- **Not** touching the persistence `Row` + hand-written `RowMapper` split (kept as-is).
- **Not** removing observability — `CorrelationIdFilter`, `logback-spring.xml` JSON logging,
  `build-info`, and the Actuator liveness/readiness groups all stay.
- **Not** removing springdoc or the `@Operation` / `@ApiResponses` annotations.
- **Not** removing `ClientIpResolver` or the dev `X-Client-IP` override.
- **Not** removing `PersistenceExceptions` (shared by two adapters) or collapsing the 7
  domain exception classes.
- **Not** removing the Maven wrapper, `Dockerfile`, or `docker-compose.yml`.
- **Not** removing the application-layer records (`CreateCouponCommand`,
  `RedeemCouponCommand`, `RedemptionResult`).
- **Not** re-running `/10x-impl-review` on the `coupon-service` change or editing its plan;
  its Progress rows 1.4 / 5.3 (CI) are already un-tickable and out of scope here.

## Implementation Approach

Each phase is independently shippable and ends green on `./mvnw verify`. Phase 1 is
pure subtraction with zero behaviour change and can be verified by "tests still pass".
Phase 2 is a mechanical merge with call-site rewiring. Phase 3 is the only semantic change
(a port return type) and carries a manual geo-IP fail-closed check.

## Critical Implementation Details

- **Retry removal ordering (Phase 3)**: Phase 1 removes the `resilience4j.retry.*` YAML while
  `IpApiGeoIpResolver` still calls `retryRegistry.retry("geoip")` — this is a safe
  intermediate state (retry runs on defaults: 3 attempts / 500 ms). Phase 3 then removes the
  retry code. Do not remove the YAML retry block and leave `@Retry`-style behaviour half-wired.
- **`CouponService` transaction boundary**: the `TransactionTemplate` (isolation
  `READ_COMMITTED`) and the insert-before-increment ordering must move verbatim from
  `RedeemCouponService` — this is the concurrency guarantee the whole project exists to
  demonstrate. `create` and `getByCode` are non-transactional.

---

## Phase 1: Config & dead-weight removal

### Overview

Delete files and build config that carry no behaviour. No production code logic changes.

### Changes Required:

#### 1. Delete doc-only and non-shipping files

**Files**: `src/main/java/com/example/coupons/**/package-info.java` (all of them),
`scripts/smoke.sh`, `scripts/` (dir if now empty), `.github/workflows/ci.yml`,
`.github/workflows/` (dir if now empty).

**Intent**: Remove Javadoc-only package descriptors, the curl walkthrough script, and the CI
workflow.

**Contract**: `find src -name package-info.java` returns nothing; `scripts/` and
`.github/` are gone.

#### 2. Fold the `Clock` bean into the main class

**File**: `src/main/java/com/example/coupons/CouponsApplication.java` (edit),
`src/main/java/com/example/coupons/config/ApplicationConfig.java` (delete).

**Intent**: Move the single `@Bean Clock clock()` (returning `Clock.systemUTC()`) onto
`CouponsApplication`; delete the now-empty config class.

**Contract**: `CouponsApplication` exposes `@Bean Clock clock()`. `config/` still contains
`GeoIpProperties`. Nothing else references `ApplicationConfig`.

#### 3. Remove JaCoCo from the build

**File**: `pom.xml`.

**Intent**: Delete the entire `jacoco-maven-plugin` `<plugin>` block (all four executions +
the `check` gate) and the `<jacoco.line.coverage>` property. Keep `maven-surefire-plugin`,
`maven-failsafe-plugin`, the `spring-boot-maven-plugin` `build-info` execution, the
`<testcontainers.version>` override, and the `<docker.api.version>` property +
`<systemPropertyVariables>` blocks.

**Contract**: `grep -c jacoco pom.xml` returns `0`. `./mvnw verify` runs both test phases and
no coverage check.

#### 4. Strip custom Resilience4j configuration

**File**: `src/main/resources/application.yml`.

**Intent**: Delete the whole `resilience4j:` section (`application.yml:49-65`). The circuit
breaker and retry registries fall back to library defaults.

**Contract**: `grep resilience4j application.yml` returns nothing. `IpApiGeoIpResolver` still
compiles and its `circuitBreakerRegistry.circuitBreaker("geoip")` /
`retryRegistry.retry("geoip")` calls resolve to default-configured instances.

#### 5. README pass

**File**: `README.md`.

**Intent**: Remove the `scripts/smoke.sh` and CI references from the "Testing" and
"Quick start" sections; drop the "JaCoCo gates line coverage … at 80%" sentence; note that
coverage is no longer gated. Leave the architecture, observability, and Docker sections
intact.

**Contract**: `grep -i "smoke.sh\|jacoco\|/ci.yml\|GitHub Actions" README.md` returns nothing
(or only an unrelated mention).

### Success Criteria:

#### Automated Verification:

- `./mvnw -B verify` passes (JDK 21): `JAVA_HOME=$(/usr/libexec/java_home -v 21) ./mvnw -B verify`
- `find src -name package-info.java` is empty
- `grep -c jacoco pom.xml` is `0`; `grep -c resilience4j src/main/resources/application.yml` is `0`
- `ArchitectureTest` still passes (part of the verify run)
- No file references a deleted symbol: `grep -rn "ApplicationConfig" src` returns nothing

#### Manual Verification:

- `docker compose up -d db` + `./mvnw spring-boot:run -Dspring-boot.run.profiles=dev` starts cleanly
- `GET /actuator/health` still `UP` with `db` component; `/actuator/info` still shows build info
- `GET /swagger-ui.html` still loads
- Logs are still JSON with a `correlationId` (observability untouched)

**Implementation Note**: After automated verification passes, pause for manual confirmation
before Phase 2.

---

## Phase 2: Merge the three application services into `CouponService`

### Overview

Replace `CreateCouponService`, `GetCouponService`, `RedeemCouponService` with one
`CouponService`. Rewire both controllers and the one service unit test.

### Changes Required:

#### 1. New `CouponService`

**File**: `src/main/java/com/example/coupons/application/CouponService.java` (new).

**Intent**: One `@Service` with `Coupon create(CreateCouponCommand)`,
`Coupon getByCode(String rawCode)`, `RedemptionResult redeem(RedeemCouponCommand)`. Bodies
move verbatim from the three existing services — including `RedeemCouponService`'s
`TransactionTemplate` (isolation `READ_COMMITTED`) set up in the constructor, the
`checkCountryRestriction` private method, the `registerRedemption` private method with
insert-before-increment ordering, and the INFO outcome logging.

**Contract**: Constructor `CouponService(CouponRepository, CouponRedemptionRepository,
GeoIpResolver, PlatformTransactionManager, Clock)` — same five parameters as today's
`RedeemCouponService`. `create` and `getByCode` are non-transactional and use only
`couponRepository` (+ `clock` for `create`).

#### 2. Delete the three old services

**Files**: `application/CreateCouponService.java`, `application/GetCouponService.java`,
`application/RedeemCouponService.java` (delete).

**Intent**: Superseded by `CouponService`.

**Contract**: `grep -rn "CreateCouponService\|GetCouponService\|RedeemCouponService" src`
returns nothing.

#### 3. Rewire the controllers

**Files**: `adapter/web/CouponController.java`, `adapter/web/CouponRedemptionController.java`.

**Intent**: `CouponController` injects `CouponService` instead of `CreateCouponService` +
`GetCouponService`; calls `couponService.create(...)` / `couponService.getByCode(...)`.
`CouponRedemptionController` injects `CouponService` instead of `RedeemCouponService` (keeps
`ClientIpResolver`); calls `couponService.redeem(...)`.

**Contract**: Both controllers depend on exactly one application type (`CouponService`) plus,
for the redemption controller, `ClientIpResolver`. No behaviour change in request/response
handling.

#### 4. Retarget the service unit test

**File**: `src/test/java/com/example/coupons/application/RedeemCouponServiceTest.java` →
rename to `CouponServiceTest.java`.

**Intent**: Rename the class, construct `new CouponService(...)` (same five args), keep both
existing tests (`redemption_row_is_inserted_before_the_counter_update`,
`country_check_runs_before_any_database_write`) unchanged in substance.

**Contract**: `CouponServiceTest` has 2 tests, both green. No new test methods needed.

#### 5. `CouponControllerTest` mock

**File**: `src/test/java/com/example/coupons/adapter/web/CouponControllerTest.java`.

**Intent**: Replace `@MockBean CreateCouponService` + `@MockBean GetCouponService` with a
single `@MockBean CouponService`; update the `when(...)` stubs to
`couponService.create(...)` / `couponService.getByCode(...)`.

**Contract**: The 4 existing tests pass unchanged in intent.

### Success Criteria:

#### Automated Verification:

- `./mvnw -B verify` passes (JDK 21)
- `grep -rn "CreateCouponService\|GetCouponService\|RedeemCouponService" src` returns nothing
- `CouponServiceTest` runs 2 tests, `CouponControllerTest` runs 4, all green
- `ArchitectureTest` still passes (application layer accessed only by adapters)

#### Manual Verification:

- `curl -X POST /api/v1/coupons …` → `201`; `curl /api/v1/coupons/{code}` → `200`
- `curl -X POST /api/v1/coupons/{code}/redemptions …` → `200`, then repeat → `409 ALREADY_REDEEMED`
- Exhaust the cap → `409 USAGE_LIMIT_REACHED`; unknown code → `404 COUPON_NOT_FOUND`

**Implementation Note**: Pause for manual confirmation before Phase 3.

---

## Phase 3: Replace `CountryResolution` with `Optional<Country>`

### Overview

Simplify the geo-IP port contract and drop the bounded-retry wrapper.

### Changes Required:

#### 1. `GeoIpResolver` port

**File**: `src/main/java/com/example/coupons/domain/port/GeoIpResolver.java`.

**Intent**: `Optional<Country> resolve(String ip)` — a present value is the resolved caller
country; `Optional.empty()` means undetermined (network error, private IP, open circuit,
unrecognised response). Update the Javadoc to say the method never throws.

**Contract**: `resolve` returns `java.util.Optional<Country>`.

#### 2. Delete `CountryResolution`

**File**: `src/main/java/com/example/coupons/domain/model/CountryResolution.java` (delete).

**Contract**: `grep -rn "CountryResolution" src` returns nothing.

#### 3. `IpApiGeoIpResolver`

**File**: `src/main/java/com/example/coupons/adapter/geoip/IpApiGeoIpResolver.java`.

**Intent**: Return `Optional<Country>` — `Optional.of(Country.of(countryCode))` on success,
`Optional.empty()` for every failure/short-circuit path (keep the existing `log.warn` lines,
now without the reason string threaded through a type). **Remove the retry**: drop the
`RetryRegistry` / `Retry` field and the `Retry.decorateSupplier(...)` wrap; keep the
`CircuitBreaker.decorateSupplier(...)` wrap, the `Caffeine` cache, and the `isNonPublic`
short-circuit. On `CallNotPermittedException` (circuit open) return `Optional.empty()`.

**Contract**: Constructor no longer takes a `RetryRegistry`. `resolve` never throws. The
`geoip.*` config (`GeoIpProperties`) is unchanged.

#### 4. `CouponService.checkCountryRestriction`

**File**: `src/main/java/com/example/coupons/application/CouponService.java`.

**Intent**: `Optional<Country> resolved = geoIpResolver.resolve(callerIp); if (resolved.isEmpty())
throw new CountryNotDeterminedException(...); Country caller = resolved.get(); if
(!caller.equals(coupon.country())) throw new CountryNotAllowedException(...); return caller;`

**Contract**: Behaviour identical — restricted + undetermined ⇒ `COUNTRY_NOT_DETERMINED`
(422), restricted + mismatch ⇒ `COUNTRY_NOT_ALLOWED` (403), unrestricted ⇒ resolver not
called.

#### 5. Tests

**Files**: `src/test/java/com/example/coupons/adapter/geoip/IpApiGeoIpResolverTest.java`,
`src/test/java/com/example/coupons/CouponRedemptionCountryIT.java`,
`src/test/java/com/example/coupons/application/CouponServiceTest.java`.

**Intent**:
- `IpApiGeoIpResolverTest` — assertions move from `.isResolved()` / `.country().value()` /
  `undetermined` to `.isPresent()` / `.get().value()` / `Optional.empty()`. Remove the
  `RetryRegistry` / `RetryConfig` from the test's setup and from the resolver constructor
  call. Keep all 5 tests (resolved, provider-fail, cached, private-IP, circuit-open).
- `CouponRedemptionCountryIT.StubGeoIpResolver` — `resolve` returns `Optional<Country>`;
  the per-test `next` field becomes `Optional<Country>` (`Optional.of(Country.of("PL"))`,
  `Optional.empty()`). All 3 IT assertions unchanged.
- `CouponServiceTest.country_check_runs_before_any_database_write` — the `geoIpResolver`
  stub returns `Optional.of(Country.of("DE"))`.

**Contract**: `grep -rn "CountryResolution" src/test` returns nothing; the geo-IP unit test
still has 5 tests; the country IT still has 3.

### Success Criteria:

#### Automated Verification:

- `./mvnw -B verify` passes (JDK 21)
- `grep -rn "CountryResolution" src` returns nothing
- `IpApiGeoIpResolverTest` runs 5, `CouponRedemptionCountryIT` runs 3, `CouponServiceTest` runs 2 — all green
- `ArchitectureTest` still passes (`domain` still free of infra libraries)

#### Manual Verification:

- PL-restricted coupon, `geoip.allow-ip-override=true`: `X-Client-IP` = a PL IP → `200`;
  a non-PL IP → `403 COUNTRY_NOT_ALLOWED`; `127.0.0.1` → `422 COUNTRY_NOT_DETERMINED`
- Point `--geoip.base-url` at a dead port: country-restricted redemptions → `422`
  (fail closed), unrestricted redemptions → `200`
- README's geo-IP paragraph now describes an `Optional<Country>` contract

**Implementation Note**: Final phase — confirm the full manual walkthrough before closing out.

---

## Testing Strategy

### Unit Tests:

- `CouponServiceTest` (2) — insert-before-increment ordering; country check before any write.
- `IpApiGeoIpResolverTest` (5) — resolved / provider-fail / cached / private-IP / circuit-open,
  now against `Optional<Country>` and with no retry layer.
- `CouponControllerTest` (4) — unchanged in intent; single `CouponService` mock.
- Domain unit tests (`CouponCodeTest`, `CountryTest`, `UsageLimitTest`, `CouponTest`,
  `CouponRedemptionTest`) — untouched.
- `ArchitectureTest` (2 rules) — untouched; must stay green through all three phases.

### Integration Tests:

- `RedemptionConcurrencyIT` (2), `CouponRedemptionApiIT` (1), `CouponApiIT` (1) — untouched;
  they exercise `CouponService` end-to-end after Phase 2.
- `CouponRedemptionCountryIT` (2) — stub resolver switched to `Optional<Country>` in Phase 3.

### Manual Testing Steps:

1. After each phase: `JAVA_HOME=$(/usr/libexec/java_home -v 21) ./mvnw -B verify` → BUILD SUCCESS.
2. After Phase 1: app boots on the `dev` profile; `/actuator/health`, `/actuator/info`,
   `/swagger-ui.html` all respond; logs are JSON with `correlationId`.
3. After Phase 2: full create → get → redeem-ladder `curl` walkthrough produces the
   documented status codes and `problem+json` bodies.
4. After Phase 3: country-restricted redeem returns `200` / `403` / `422` for
   PL / non-PL / loopback IPs; with the geo-IP provider unreachable, restricted redemptions
   fail closed (`422`) and unrestricted ones succeed (`200`).

## Performance Considerations

Removing the retry layer means a redemption made while the geo-IP circuit is open no longer
spends ~1 s in retry backoff before failing closed — it fails immediately. This is a small
improvement on the failure path. With `resilience4j.*` config removed, the circuit breaker
runs on defaults (`minimumNumberOfCalls` 100), so under low traffic it effectively never
opens; the per-call fail-closed behaviour and the cache are what protect the redemption path.

## Migration Notes

No data or schema changes. No API contract changes (status codes, `problem+json` shapes, and
the OpenAPI document are identical before and after). Rollback is `git revert` of the phase
commit(s).

## References

- Original build: `context/changes/coupon-service/plan.md`
- Full review that this trims against: `context/changes/coupon-service/reviews/impl-review.md`
- Simplification decisions: `context/changes/simplify-project/plan-brief.md`

## Progress

> Convention: `- [ ]` pending, `- [x]` done. Append ` — <commit sha>` when a step lands. Do not rename step titles. See `references/progress-format.md`.

### Phase 1: Config & dead-weight removal

#### Automated

- [x] 1.1 `./mvnw -B verify` passes (JDK 21)
- [x] 1.2 `find src -name package-info.java` is empty
- [x] 1.3 `grep -c jacoco pom.xml` is 0 and `grep -c resilience4j src/main/resources/application.yml` is 0
- [x] 1.4 `ArchitectureTest` still passes
- [x] 1.5 `grep -rn "ApplicationConfig" src` returns nothing

#### Manual

- [x] 1.6 App starts on the `dev` profile against the compose DB
- [x] 1.7 `/actuator/health` UP with `db`; `/actuator/info` shows build info; `/swagger-ui.html` loads
- [x] 1.8 Logs are still JSON with a `correlationId`

### Phase 2: Merge the three application services into `CouponService`

#### Automated

- [x] 2.1 `./mvnw -B verify` passes (JDK 21)
- [x] 2.2 `grep -rn "CreateCouponService\|GetCouponService\|RedeemCouponService" src` returns nothing
- [x] 2.3 `CouponServiceTest` runs 2 tests, `CouponControllerTest` runs 4, all green
- [x] 2.4 `ArchitectureTest` still passes

#### Manual

- [x] 2.5 `POST /api/v1/coupons` → 201; `GET /api/v1/coupons/{code}` → 200
- [x] 2.6 Redeem twice as one user → 200 then `409 ALREADY_REDEEMED`
- [x] 2.7 Exhaust the cap → `409 USAGE_LIMIT_REACHED`; unknown code → `404 COUPON_NOT_FOUND`

### Phase 3: Replace `CountryResolution` with `Optional<Country>`

#### Automated

- [x] 3.1 `./mvnw -B verify` passes (JDK 21)
- [x] 3.2 `grep -rn "CountryResolution" src` returns nothing
- [x] 3.3 `IpApiGeoIpResolverTest` runs 5, `CouponRedemptionCountryIT` runs 3, `CouponServiceTest` runs 2 — all green
- [x] 3.4 `ArchitectureTest` still passes

#### Manual

- [x] 3.5 PL-restricted coupon: PL IP → 200, non-PL IP → `403 COUNTRY_NOT_ALLOWED`, `127.0.0.1` → `422 COUNTRY_NOT_DETERMINED`
- [x] 3.6 Geo-IP provider unreachable → restricted redemptions `422`, unrestricted `200`
- [x] 3.7 README geo-IP paragraph describes the `Optional<Country>` contract
