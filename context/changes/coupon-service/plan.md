# Coupon Discount Service — Implementation Plan

## Overview

Design and implement a REST service that manages discount coupons. Two core operations —
**create a coupon** (no auth) and **register a coupon redemption by a user** — plus a
**look-up by code** to support verification. The service is built to production standards:
deliberate architecture, correct behaviour in a multithreaded environment, a real database as
the source of truth, and a test suite that proves the concurrency and business rules rather
than just exercising the happy path.

The defining constraint is the **max-uses cap under concurrent load** ("kto pierwszy ten
lepszy" — first come, first served). This is enforced in the database with a single atomic
conditional `UPDATE`, backed by a `CHECK` constraint, so no application-level locking or
coordination is required and the service scales horizontally behind a load balancer.

## Current State Analysis

Greenfield. The repository contains only `context/foundation/task.md` (the assignment),
the `context/` scaffold, and editor config. No code, no build file, no CI. Every decision
below is a fresh choice; there are no existing patterns to conform to.

Key constraints lifted from `context/foundation/task.md`:

- Language: **Java or Kotlin**. Build: **Maven or Gradle**. Data **must** be persisted in a
  database. Solution **must** be scalable and correct in a **multithreaded production
  environment**. Free/accessible technologies only.
- Coupon fields: unique **code**, **creation date**, **max uses**, **current uses**,
  **target country**.
- Coupon **code is unique and case-insensitive** (`WIOSNA` == `wiosna`).
- Redemptions are **capped at max uses**, first-come-first-served.
- The coupon's **country restricts redemption to callers in that country**, resolved from the
  **caller's IP** via any free geo-IP service.
- Redemption must return a **clear, distinct outcome** for each failure: code not found,
  usage limit reached, disallowed country, user already redeemed.
- **Optional (in scope here):** one user may redeem a given coupon only **once**; the redeem
  request carries a user identifier plus the coupon code.
- Grading weighs architecture, design patterns, code quality, tests, and production-readiness
  — explicitly *not* a minimal implementation.

## Desired End State

A runnable service (`git clone` → `docker compose up -d db` → `mvn spring-boot:run`, or a
built image) exposing:

| Method | Path | Purpose | Success |
| --- | --- | --- | --- |
| `POST` | `/api/v1/coupons` | Create a coupon | `201 Created` + coupon body + `Location` |
| `GET` | `/api/v1/coupons/{code}` | Look up a coupon (case-insensitive) | `200 OK` + coupon body |
| `POST` | `/api/v1/coupons/{code}/redemptions` | Register a redemption by a user | `200 OK` + redemption result |

Every failure returns `application/problem+json` (RFC 7807) with a stable machine-readable
`code` and a per-case HTTP status (catalogue in Phase 3 / Phase 4). An OpenAPI document is
served at `/v3/api-docs` and Swagger UI at `/swagger-ui.html`.

**Verification of the end state:**

- `mvn verify` passes: unit tests, `@WebMvcTest` slice tests, Testcontainers integration
  tests (real PostgreSQL + real Liquibase migrations), the explicit concurrency test, and the
  JaCoCo coverage gate.
- The concurrency test: firing `K` parallel redemptions at a coupon with `max_uses = N`
  (`K > N`, distinct users) results in **exactly `N`** `200 OK` redemptions and `K - N`
  `USAGE_LIMIT_REACHED`, with `current_uses == N` and exactly `N` rows in `coupon_redemption`.
- Manual `curl` walkthrough in the README reproduces every documented outcome.
- `/actuator/health` reports `UP` (liveness + readiness, readiness gated on the DB).

### Key Discoveries

- **Task source:** `context/foundation/task.md` — the multithreading and scalability language
  (lines 40, 52) is the reason the cap is enforced in the DB, not the application.
- **Atomic cap pattern:** `UPDATE coupon SET current_uses = current_uses + 1 WHERE id = :id
  AND current_uses < max_uses` — rows-affected `1` = success, `0` = limit reached. One
  round-trip, only a brief row lock, no lost updates, no read-modify-write race.
- **One-per-user:** a `UNIQUE (coupon_id, user_id)` constraint on `coupon_redemption` makes
  the "already redeemed" rule a database invariant, not an application check that can race.
- **Case-insensitive code:** store a normalized (lower-cased, trimmed) `code` column with a
  plain `UNIQUE` index; keep the caller's original casing only if needed for display (not
  required — normalized is the canonical form).
- **Geo-IP off the transaction:** the country lookup is a slow, failure-prone network call.
  It runs *before* the redemption transaction opens so a pooled DB connection is never held
  across external I/O.
- **Spring Data JDBC** (not JPA): no first-level cache or dirty-checking to obscure the
  concurrency behaviour under test; custom `@Modifying @Query` for the atomic `UPDATE` is
  first-class.

## What We're NOT Doing

- **No authentication / authorization** — the task explicitly does not require it.
- **No admin UI or frontend of any kind.**
- **No message internationalization** — responses are English-only.
- **No coupon listing / search / update / delete endpoints.** The resource surface is
  create + redeem + get-by-code. No pagination, no bulk operations.
- **No coupon validity window / expiry** — only a creation timestamp is stored, as the task
  specifies. `valid_from` / `valid_to` / time-based expiry is a noted extension point, not
  built.
- **No distributed cache, message broker, or multi-region concerns.** Scalability is
  delivered by a stateless application plus a horizontally scalable PostgreSQL; redemption
  correctness lives in the database. The geo-IP cache is deliberately per-instance.
- **No mutation testing or load-testing harness** (PIT, Gatling, k6) — the explicit
  concurrency test covers the graded behaviour; the rest is out of proportion for this task.

## Implementation Approach

**Architecture — single-module hexagonal (ports & adapters).** One Maven module, three
concentric layers:

- **`domain`** — pure Java, no Spring, no Jakarta Persistence, no HTTP. `Coupon` aggregate,
  value objects (`CouponCode`, `Country`, `UsageLimit`), redemption policy, domain
  exceptions. Ports: `CouponRepository`, `CouponRedemptionRepository`, `GeoIpResolver`,
  `Clock` (via `java.time.Clock`).
- **`application`** — use-case services orchestrating the domain and ports:
  `CreateCouponService`, `RedeemCouponService`, `GetCouponService`. Owns transaction
  boundaries (`@Transactional` on the redeem use case only).
- **`adapter`** — inbound: `web` (controllers, DTOs, `@RestControllerAdvice` mapping domain
  exceptions → `ProblemDetail`). Outbound: `persistence` (Spring Data JDBC repositories,
  atomic `UPDATE` query, domain⇄row mapping), `geoip` (resilience-wrapped HTTP client).

`ArchUnit` tests enforce the dependency direction (domain depends on nothing; adapters never
depend on each other).

**Redemption flow (the core algorithm):**

1. Normalize the path `code` (trim + lower-case).
2. Load the coupon by normalized code. Not found → `COUPON_NOT_FOUND` (404).
3. If the coupon has a target country: resolve the caller's country from IP (outside any
   transaction). Mismatch → `COUNTRY_NOT_ALLOWED` (403). Unresolved (error, private IP,
   circuit open) → `COUNTRY_NOT_DETERMINED` (422) — **fail closed**. Coupon with no country
   restriction skips this step entirely.
4. Open one short transaction at `READ_COMMITTED`:
   a. Insert the `coupon_redemption` row. Unique-violation on `(coupon_id, user_id)` →
      roll back → `ALREADY_REDEEMED` (409).
   b. Execute the atomic conditional `UPDATE` on `coupon.current_uses`. Rows-affected `0` →
      roll back → `USAGE_LIMIT_REACHED` (409).
   c. Commit → `200 OK` with the redemption result (remaining uses, resolved country).

Insert-redemption-**first** ordering is deliberate: a user who has already redeemed always
gets the precise `ALREADY_REDEEMED` message even when the coupon is simultaneously exhausted.

**Client IP resolution.** `server.forward-headers-strategy=framework` plus an explicit
trusted-proxy configuration; the client IP is taken from `X-Forwarded-For` only when the
request arrived through a trusted hop, otherwise the socket remote address. A dev/test-only
`geoip.allow-ip-override` flag (default `false`) lets a request supply an explicit IP so the
country rule can be exercised locally without spoofing infrastructure.

**Geo-IP adapter.** `GeoIpResolver` port returns a resolution result (resolved country, or an
"undetermined" signal). The real adapter calls a free API (`ip-api.com`) with connect/read
timeouts, a Resilience4j circuit breaker + bounded retry, and a short-TTL Caffeine cache
keyed by IP (stays under the ~45 req/min free ceiling). Circuit-open or any failure →
"undetermined" → fail closed. Tests use WireMock for the adapter and a hand-stubbed
`GeoIpResolver` for use-case/integration tests.

## Critical Implementation Details

- **Transaction must not wrap the geo-IP call.** `RedeemCouponService` resolves the country
  *before* the `@Transactional` boundary. If refactoring merges them, a pooled DB connection
  is held across a network call and the pool exhausts under load.
- **The atomic `UPDATE` is the gatekeeper, not the earlier read.** Code must branch on
  rows-affected from the `UPDATE`, never on the `current_uses` value read when the coupon was
  loaded — that value is stale by design.
- **Unique-violation translation.** Spring Data JDBC surfaces a
  `DbActionExecutingException` / `DuplicateKeyException` on the `(coupon_id, user_id)`
  constraint. Catch at the use-case or a dedicated exception translator and map to
  `ALREADY_REDEEMED`; do not pre-check with a `SELECT` (that reintroduces a race).
- **`CHECK (current_uses <= max_uses)` and `CHECK (current_uses >= 0)`** on `coupon` are
  defense-in-depth. If the atomic `UPDATE` is ever bypassed, the DB rejects the write rather
  than silently over-issuing.
- **Case-insensitive uniqueness** is enforced on the normalized `code` column with a plain
  `UNIQUE` index — not a functional `lower(code)` index — so the application and the schema
  agree on exactly one canonical form.
- **Country representation:** ISO 3166-1 alpha-2, upper-cased, validated on input against a
  known set (`java.util.Locale.getISOCountries()`). Geo-IP responses are normalized to the
  same form before comparison.

---

## Phase 1: Project Skeleton & Infrastructure

### Overview

A buildable, bootable Spring Boot 3 / Java 21 application with the hexagonal package
structure, database wiring (PostgreSQL + Liquibase), local infrastructure (`docker-compose`),
container image, observability endpoints, and CI — but no domain logic yet.

### Changes Required

#### 1. Maven project & build

**File**: `pom.xml`

**Intent**: Establish the build with Java 21 and Spring Boot 3.x. Bring in `web`,
`data-jdbc`, `validation`, `actuator`, PostgreSQL driver, Liquibase, springdoc-openapi,
Resilience4j (Spring Boot 3 starter), Caffeine. Test scope: `spring-boot-starter-test`,
`testcontainers` (JUnit 5 + PostgreSQL), WireMock, `archunit-junit5`. Configure the
`maven-surefire`/`failsafe` split (unit vs `*IT`), JaCoCo with a coverage rule, and the
Spring Boot repackage goal.

**Contract**: `mvn verify` runs unit + integration tests + coverage gate. Java release 21.
Single module. JaCoCo rule: line coverage ≥ 0.80 on `domain` and `application` packages
(exclude `adapter`, DTOs, config, the main class).

#### 2. Package structure

**File**: `src/main/java/com/example/coupons/**`

**Intent**: Create the hexagonal skeleton so later phases have a home for each concern.

**Contract**: packages —
`domain.model`, `domain.port`, `domain.exception`;
`application`;
`adapter.web`, `adapter.web.dto`, `adapter.persistence`, `adapter.geoip`;
`config`. `CouponsApplication` (main class) in the root package.

#### 3. Configuration & profiles

**File**: `src/main/resources/application.yml` (+ `application-dev.yml`, `application-test.yml`)

**Intent**: Externalize datasource, Liquibase, forwarded-headers, geo-IP, and Resilience4j
settings. `dev` points at the `docker-compose` database; `test` is a placeholder (Testcontainers
supplies the JDBC URL dynamically). Bind a `@ConfigurationProperties` type for geo-IP
(`geoip.base-url`, `geoip.timeout`, `geoip.cache.ttl`, `geoip.allow-ip-override`,
`geoip.trusted-proxies`).

**Contract**: `server.forward-headers-strategy=framework`. `spring.datasource.*` from env
vars with dev defaults. `spring.liquibase.change-log=classpath:db/changelog/db.changelog-master.yaml`.
Actuator exposes `health,info`; `management.endpoint.health.probes.enabled=true`.

#### 4. Liquibase bootstrap

**File**: `src/main/resources/db/changelog/db.changelog-master.yaml`

**Intent**: Empty master changelog that `includeAll`s a `changes/` directory. Phase 2 and 3
add the actual changesets.

**Contract**: `includeAll path: db/changelog/changes/`. Master file committed now, no
changesets yet.

#### 5. Local infrastructure & image

**File**: `docker-compose.yml`, `Dockerfile`

**Intent**: `docker-compose.yml` runs PostgreSQL 16 with a named volume and healthcheck.
`Dockerfile` is a multi-stage build (Maven build stage → slim JRE 21 runtime stage) producing
a runnable image.

**Contract**: compose service `db` on `5432`, POSTGRES_DB/USER/PASSWORD documented in README.
Image entrypoint `java -jar app.jar`, `EXPOSE 8080`, runs as non-root.

#### 6. Continuous integration

**File**: `.github/workflows/ci.yml`

**Intent**: On push / PR: set up Temurin 21, cache Maven, run `mvn -B verify`. Testcontainers
uses the Docker service available on GitHub-hosted runners. Upload the JaCoCo report as an
artifact.

**Contract**: single job `build`, fails the workflow on any test or coverage-gate failure.

#### 7. ArchUnit scaffold

**File**: `src/test/java/com/example/coupons/ArchitectureTest.java`

**Intent**: Lay down the dependency-direction rules now so violations are caught as code is
added.

**Contract**: rules — `domain` may not access `application`, `adapter`, Spring, or
`jakarta.persistence`; `adapter.web` may not access `adapter.persistence` or `adapter.geoip`
and vice-versa. Test is active from Phase 1.

### Success Criteria

#### Automated Verification

- `mvn -B verify` succeeds with no domain code: `./mvnw verify`
- Application context loads: `CouponsApplicationTests` (`@SpringBootTest` with a Testcontainers
  PostgreSQL) passes
- ArchUnit test runs and passes (vacuously)
- CI workflow is valid and green on the initial push

#### Manual Verification

- `docker compose up -d db` then `mvn spring-boot:run -Dspring-boot.run.profiles=dev` starts
  the app with no errors
- `GET http://localhost:8080/actuator/health` returns `{"status":"UP"}` with `db` component up
- `GET http://localhost:8080/swagger-ui.html` loads
- Building the image (`docker build .`) succeeds and the container starts against compose

**Implementation Note**: After this phase and all automated verification passes, pause for
manual confirmation before proceeding.

---

## Phase 2: Domain Model & Coupon Creation

### Overview

The `Coupon` aggregate and its value objects, the creation use case, the JDBC persistence
adapter, the `coupon` table migration, and the `POST /api/v1/coupons` +
`GET /api/v1/coupons/{code}` endpoints with RFC 7807 error handling.

### Changes Required

#### 1. Domain model

**File**: `src/main/java/com/example/coupons/domain/model/{Coupon,CouponCode,Country,UsageLimit}.java`

**Intent**: Model the coupon as an aggregate with self-validating value objects. `CouponCode`
normalizes (trim + lower-case) and rejects blank/oversized input. `Country` holds an ISO
3166-1 alpha-2 code, upper-cased, validated against `Locale.getISOCountries()`. `UsageLimit`
rejects non-positive values. `Coupon` holds id, `CouponCode`, `Instant createdAt`,
`UsageLimit maxUses`, `int currentUses`, optional `Country`. A static factory
`Coupon.create(code, maxUses, country, clock)` sets `createdAt` from the injected `Clock` and
`currentUses = 0`.

**Contract**: value objects are immutable (Java records with compact-constructor validation).
`Coupon` exposes intent-revealing queries (`hasCountryRestriction()`, `isExhausted()`,
`remainingUses()`) but **no** `incrementUses()` that persists — the counter is advanced by the
atomic repository operation in Phase 3. Invalid input throws `IllegalArgumentException`
subtypes from `domain.exception`.

#### 2. Repository port

**File**: `src/main/java/com/example/coupons/domain/port/CouponRepository.java`

**Intent**: Define the outbound persistence contract the domain needs.

**Contract**: `Coupon save(Coupon)` (insert; translates a code-uniqueness violation to a
domain `DuplicateCouponCodeException`), `Optional<Coupon> findByCode(CouponCode)`.

#### 3. Create-coupon use case

**File**: `src/main/java/com/example/coupons/application/CreateCouponService.java`

**Intent**: Orchestrate creation: build the `Coupon` via the factory, persist it, return it.
Thin — validation lives in the value objects.

**Contract**: `Coupon create(CreateCouponCommand)` where the command carries raw `code`,
`maxUses`, nullable `country`. Propagates `DuplicateCouponCodeException`.

#### 4. Persistence adapter & migration

**File**: `src/main/java/com/example/coupons/adapter/persistence/{CouponJdbcRepository,CouponRow,CouponRowMapper}.java`,
`src/main/resources/db/changelog/changes/0001-create-coupon.yaml`

**Intent**: Spring Data JDBC repository backing `CouponRepository`. Map the `Coupon`
aggregate to/from a `coupon` row. Migration creates the table.

**Contract**: `coupon` columns — `id` (UUID or `bigint generated always as identity`),
`code` varchar UNIQUE NOT NULL (stores the normalized form), `created_at` timestamptz NOT
NULL, `max_uses` int NOT NULL, `current_uses` int NOT NULL DEFAULT 0,
`country` char(2) NULL. Constraints: `CHECK (max_uses > 0)`,
`CHECK (current_uses >= 0)`, `CHECK (current_uses <= max_uses)`. Index: `UNIQUE (code)`.
The adapter catches `DuplicateKeyException` and rethrows `DuplicateCouponCodeException`.

#### 5. Web adapter — create & get

**File**: `src/main/java/com/example/coupons/adapter/web/CouponController.java`,
`adapter/web/dto/{CreateCouponRequest,CouponResponse}.java`

**Intent**: `POST /api/v1/coupons` accepts `{ code, maxUses, country? }`, validates with Bean
Validation, calls `CreateCouponService`, returns `201` with `CouponResponse` and a `Location`
header. `GET /api/v1/coupons/{code}` normalizes the code, calls `GetCouponService`, returns
`200` or `COUPON_NOT_FOUND`.

**Contract**: `CreateCouponRequest` — `@NotBlank code` (max length matches `CouponCode`),
`@Positive maxUses`, optional `country` (`@Pattern` alpha-2). `CouponResponse` —
`code, createdAt, maxUses, currentUses, remainingUses, country`. No entity leakage; DTOs only.

#### 6. Problem Details handler

**File**: `src/main/java/com/example/coupons/adapter/web/ApiExceptionHandler.java`

**Intent**: `@RestControllerAdvice` translating domain exceptions and validation failures into
`ProblemDetail` (`application/problem+json`) with a stable `code` property and `type` URI.

**Contract**: mappings so far —
`DuplicateCouponCodeException` → 409 `DUPLICATE_CODE`;
`CouponNotFoundException` → 404 `COUPON_NOT_FOUND`;
`MethodArgumentNotValidException` / `ConstraintViolationException` → 400 `VALIDATION_ERROR`
(with field details). Each `ProblemDetail` carries `code`, `title`, `detail`, `instance`.

### Success Criteria

#### Automated Verification

- Domain unit tests pass — `CouponCodeTest` (normalization, `WIOSNA` == `wiosna`, blank/oversize
  rejected), `CountryTest` (valid/invalid ISO codes), `UsageLimitTest`, `CouponTest` (factory
  sets `createdAt` from a fixed `Clock`, `currentUses = 0`): `./mvnw test`
- `@WebMvcTest` slice tests pass — create returns `201` + `Location`; duplicate code (service
  stubbed to throw) returns `409` `DUPLICATE_CODE` as `problem+json`; invalid body returns
  `400` `VALIDATION_ERROR`; get-missing returns `404` `COUPON_NOT_FOUND`
- Testcontainers integration test passes — persist a coupon, read it back by upper-cased code,
  assert case-insensitive hit; inserting a second coupon whose code differs only in case fails
  with `DUPLICATE_CODE`
- Liquibase migration applies cleanly on a fresh Testcontainers database
- ArchUnit rules still pass
- Coverage gate met for the code added this phase

#### Manual Verification

- `curl -X POST /api/v1/coupons -d '{"code":"WIOSNA","maxUses":3,"country":"PL"}'` → `201`
  with `createdAt` populated and `currentUses: 0`
- `curl /api/v1/coupons/wiosna` → `200` returning the same coupon (case-insensitive)
- Re-`POST`ing `{"code":"wiosna",...}` → `409` `DUPLICATE_CODE` in `problem+json`
- OpenAPI doc shows both endpoints with the documented schemas

**Implementation Note**: Pause for manual confirmation before proceeding.

---

## Phase 3: Redemption — Core Rules & Concurrency

### Overview

The redemption use case and endpoint, the `coupon_redemption` table with its one-per-user
unique constraint, the atomic conditional counter `UPDATE`, and — critically — the explicit
multithreaded test proving the cap holds. Country restriction is stubbed out here (a
`GeoIpResolver` that always resolves "allowed"); Phase 4 supplies the real one.

### Changes Required

#### 1. Redemption domain

**File**: `src/main/java/com/example/coupons/domain/model/CouponRedemption.java`,
`domain/exception/{UsageLimitReachedException,AlreadyRedeemedException}.java`

**Intent**: `CouponRedemption` value object — `couponId`, `userId`, `redeemedAt`,
`resolvedCountry` (nullable when the coupon has no restriction). Domain exceptions for the two
transactional failure outcomes.

**Contract**: `CouponRedemption.record(couponId, userId, resolvedCountry, clock)` factory.
`userId` is an opaque non-blank string (any identifier per the task).

#### 2. Ports for atomic redemption

**File**: `src/main/java/com/example/coupons/domain/port/CouponRedemptionRepository.java`,
extend `CouponRepository`

**Intent**: Add the two operations the transactional step needs.

**Contract**:
`CouponRepository.incrementUsageIfBelowLimit(couponId) -> int` (rows affected: `1` success,
`0` limit reached);
`CouponRedemptionRepository.insert(CouponRedemption)` (throws a translated
`AlreadyRedeemedException` on the `(coupon_id, user_id)` unique violation).

#### 3. Redeem-coupon use case

**File**: `src/main/java/com/example/coupons/application/RedeemCouponService.java`,
`application/RedeemCouponCommand.java`, `application/RedemptionResult.java`

**Intent**: Implement the redemption flow. Steps 1–3 (normalize, load, country check) run
outside the transaction; step 4 (insert redemption → atomic `UPDATE`) runs inside one
`@Transactional(isolation = READ_COMMITTED)` method. Branch on the `UPDATE` rows-affected, not
the loaded `currentUses`.

**Contract**: `RedemptionResult redeem(RedeemCouponCommand)` where the command carries
normalized `code`, `userId`, and the resolved caller IP. Throws `CouponNotFoundException`
(404), `AlreadyRedeemedException` (409), `UsageLimitReachedException` (409). Ordering:
**insert redemption first**, then the counter `UPDATE`, so a repeat user gets
`ALREADY_REDEEMED` even on an exhausted coupon. `RedemptionResult` carries `code`,
`remainingUses`, `resolvedCountry`, `redeemedAt`.

#### 4. Persistence — atomic UPDATE & redemption table

**File**: `src/main/java/com/example/coupons/adapter/persistence/CouponRedemptionJdbcRepository.java`,
atomic query on `CouponJdbcRepository`,
`src/main/resources/db/changelog/changes/0002-create-coupon-redemption.yaml`

**Intent**: Implement `incrementUsageIfBelowLimit` as a single `@Modifying @Query`. Create the
`coupon_redemption` table with the one-per-user constraint. Translate its unique violation.

**Contract**:
Query — `UPDATE coupon SET current_uses = current_uses + 1 WHERE id = :id AND current_uses <
max_uses`.
`coupon_redemption` columns — `id` PK, `coupon_id` FK → `coupon(id)` NOT NULL, `user_id`
varchar NOT NULL, `redeemed_at` timestamptz NOT NULL, `resolved_country` char(2) NULL.
Constraints — `UNIQUE (coupon_id, user_id)`; index on `coupon_id`. `DuplicateKeyException`
from `insert` → `AlreadyRedeemedException`.

#### 5. Web adapter — redeem

**File**: `adapter/web/CouponRedemptionController.java`,
`adapter/web/dto/{RedeemCouponRequest,RedemptionResponse}.java`, extend `ApiExceptionHandler`

**Intent**: `POST /api/v1/coupons/{code}/redemptions` accepts `{ userId }`, resolves the
caller IP (Phase 4 hardens this; here use `HttpServletRequest#getRemoteAddr` behind the
framework forwarded-headers handling), calls `RedeemCouponService`, returns `200` +
`RedemptionResponse`. Extend the exception handler with the two new outcomes.

**Contract**: handler mappings added — `UsageLimitReachedException` → 409
`USAGE_LIMIT_REACHED`; `AlreadyRedeemedException` → 409 `ALREADY_REDEEMED`.
`RedeemCouponRequest` — `@NotBlank userId`. `RedemptionResponse` —
`code, userId, remainingUses, resolvedCountry, redeemedAt`.

#### 6. Explicit concurrency test

**File**: `src/test/java/com/example/coupons/RedemptionConcurrencyIT.java`

**Intent**: Prove first-come-first-served under parallel load against a real database.

**Contract**: Testcontainers PostgreSQL. Create a coupon with `maxUses = N` (e.g. 50). Submit
`K` (e.g. 200) redemptions with **distinct** `userId`s from a fixed thread pool, all released
together (`CountDownLatch`). Assert: exactly `N` succeed, exactly `K - N` fail with
`USAGE_LIMIT_REACHED`, `coupon.current_uses == N`, and `count(*) from coupon_redemption where
coupon_id = ? == N`. A second variant: `K` redemptions with the **same** `userId` → exactly
`1` success, rest `ALREADY_REDEEMED`, `current_uses == 1`.

### Success Criteria

#### Automated Verification

- Domain + use-case unit tests pass (redeem flow with stubbed ports for each outcome; ordering:
  repeat user on an exhausted coupon → `ALREADY_REDEEMED`): `./mvnw test`
- `@WebMvcTest` slice tests pass — `200` happy path, and `problem+json` for
  `COUPON_NOT_FOUND` (404), `USAGE_LIMIT_REACHED` (409), `ALREADY_REDEEMED` (409),
  `VALIDATION_ERROR` (400)
- `0002` migration applies cleanly on a fresh Testcontainers database
- Full-flow integration test passes — create coupon (`maxUses = 2`), redeem twice (distinct
  users) → both `200`, `remainingUses` `1` then `0`; third redemption → `USAGE_LIMIT_REACHED`;
  re-redeem as user 1 → `ALREADY_REDEEMED`
- **`RedemptionConcurrencyIT` passes**, both variants
- ArchUnit rules pass; coverage gate met

#### Manual Verification

- `curl -X POST /api/v1/coupons/wiosna/redemptions -d '{"userId":"u1"}'` → `200`,
  `remainingUses` decremented
- Repeating with `"userId":"u1"` → `409` `ALREADY_REDEEMED`
- Exhausting the coupon with fresh users, then one more → `409` `USAGE_LIMIT_REACHED`
- `POST` to a non-existent code → `404` `COUPON_NOT_FOUND`
- All error bodies are `application/problem+json` with a `code` field

**Implementation Note**: Pause for manual confirmation before proceeding.

---

## Phase 4: Geo-IP Country Restriction

### Overview

Replace the stub resolver with a real, resilience-wrapped remote geo-IP adapter; harden
client-IP resolution for a proxied deployment; wire the country check into the redemption flow
with fail-closed semantics.

### Changes Required

#### 1. GeoIpResolver port & result type

**File**: `src/main/java/com/example/coupons/domain/port/GeoIpResolver.java`,
`domain/model/CountryResolution.java`

**Intent**: Define the country-lookup contract. The result distinguishes "resolved to country
X" from "undetermined" (so the use case can fail closed without exception-driven control
flow).

**Contract**: `CountryResolution resolve(String ip)`. `CountryResolution` is a sealed type /
record with `resolved(Country)` and `undetermined(reason)` factories and a
`boolean isResolved()`.

#### 2. Remote geo-IP adapter

**File**: `src/main/java/com/example/coupons/adapter/geoip/IpApiGeoIpResolver.java`,
`adapter/geoip/GeoIpClientConfig.java`

**Intent**: Call `ip-api.com` (`GET /json/{ip}?fields=status,countryCode`) via a `RestClient`
with connect/read timeouts. Wrap with a Resilience4j circuit breaker + bounded retry
(`@CircuitBreaker`, `@Retry` or programmatic). ****Cache resolutions in a Caffeine cache keyed by
IP with a short TTL and bounded size. Any HTTP error, non-`success` payload, timeout, or
open circuit → `CountryResolution.undetermined(...)`. Normalize `countryCode` to upper-case
alpha-2.

**Contract**: config properties (`geoip.*`) bound from Phase 1. Circuit-breaker + retry
settings in `application.yml` under `resilience4j.*`. Private/loopback/link-local IPs
short-circuit to `undetermined` without a call. Cache is per-instance by design.

#### 3. Client IP resolution

**File**: `src/main/java/com/example/coupons/adapter/web/ClientIpResolver.java`,
`config/ForwardedHeadersConfig.java`

**Intent**: Centralize "what is the caller's IP". With `forward-headers-strategy=framework`,
Spring already normalizes `X-Forwarded-For` from trusted infrastructure; this component reads
the resolved remote address and, only when `geoip.allow-ip-override=true` (dev/test), honors
an explicit `X-Client-IP` header or `?ip=` query param for local testing.

**Contract**: `String resolve(HttpServletRequest)`. Override path is unreachable when the flag
is `false` (production default). Document the trusted-proxy expectation in the README.

#### 4. Wire the country check into redemption

**File**: `application/RedeemCouponService.java` (extend), `domain/exception/{CountryNotAllowedException,CountryNotDeterminedException}.java`,
extend `ApiExceptionHandler`

**Intent**: After loading the coupon and before the transaction: if
`coupon.hasCountryRestriction()`, call `GeoIpResolver`. Resolved + matches → proceed, and pass
the resolved country into the redemption record. Resolved + mismatch →
`CountryNotAllowedException`. Undetermined → `CountryNotDeterminedException` (fail closed).
No restriction → skip, `resolvedCountry` stays null.

**Contract**: handler mappings added — `CountryNotAllowedException` → 403
`COUNTRY_NOT_ALLOWED`; `CountryNotDeterminedException` → 422 `COUNTRY_NOT_DETERMINED`. The
geo-IP call remains strictly outside `@Transactional`.

#### 5. Adapter tests with WireMock

**File**: `src/test/java/com/example/coupons/adapter/geoip/IpApiGeoIpResolverTest.java`

**Intent**: Exercise the adapter against a stubbed HTTP endpoint.

**Contract**: cases — happy path (`countryCode: "PL"` → resolved); `status: "fail"` →
undetermined; HTTP 429 / 5xx → undetermined; slow response beyond read timeout → undetermined;
repeated calls for the same IP hit the cache (one HTTP call); circuit opens after the
configured failure rate and subsequent calls short-circuit to undetermined; private IP →
undetermined with no HTTP call.

### Success Criteria

#### Automated Verification

- `IpApiGeoIpResolverTest` passes all cases (WireMock): `./mvnw test`
- `ClientIpResolver` unit tests pass — socket address used by default; `X-Client-IP`
  honored only when the override flag is on
- Use-case unit tests pass — allowed / blocked / undetermined branches with a stubbed
  `GeoIpResolver`; unrestricted coupon skips the resolver (verify no interaction)
- Full-flow integration tests pass (stubbed `GeoIpResolver` bean) — coupon with `country = PL`:
  caller resolved to `PL` → `200`; resolved to `DE` → `403` `COUNTRY_NOT_ALLOWED`;
  undetermined → `422` `COUNTRY_NOT_DETERMINED`; coupon with no country → `200` regardless
- The country check executes before the transaction (asserted via ordering/interaction in a
  use-case test)
- ArchUnit rules pass; coverage gate met

#### Manual Verification

- With `geoip.allow-ip-override=true` locally: redeem a `PL` coupon with
  `-H 'X-Client-IP: <known-PL-IP>'` → `200`; with a known non-PL IP → `403`
  `COUNTRY_NOT_ALLOWED`; with `127.0.0.1` → `422` `COUNTRY_NOT_DETERMINED`
- Redeeming a coupon with no country restriction ignores the IP entirely
- Killing network access to `ip-api.com` (or pointing `geoip.base-url` at a dead port) →
  country-restricted redemptions return `422`, unrestricted ones still `200`; the circuit
  breaker opens (visible in logs)

**Implementation Note**: Pause for manual confirmation before proceeding.

---

## Phase 5: Hardening, Docs & Observability

### Overview

Bring the service to a presentable, defensible state: a thorough README, structured logging
with request correlation, readiness/liveness wiring, enforced architecture and coverage gates,
and a final end-to-end smoke.

### Changes Required

#### 1. README

**File**: `README.md`

**Intent**: The document a reviewer reads first. Cover: what it is; how to run (compose + Maven,
and the image); the API with `curl` examples for **every** outcome; the **error-code
catalogue** (table: `code` → HTTP status → meaning); the **design decisions and their
rationale** (hexagonal architecture, DB-enforced cap with the atomic `UPDATE`, one-per-user as
a unique constraint, geo-IP off the transaction + fail-closed, trusted-proxy IP resolution,
Spring Data JDBC over JPA); **scalability notes** (stateless app, horizontal scaling,
per-instance geo-IP cache); and **known extension points** (validity window, offline geo-IP
DB, distributed cache).

**Contract**: a reviewer can clone, run, and reproduce every documented response from the
README alone.

#### 2. Structured logging & correlation id

**File**: `src/main/resources/logback-spring.xml`,
`src/main/java/com/example/coupons/adapter/web/CorrelationIdFilter.java`

**Intent**: JSON log output (via `logstash-logback-encoder` or Spring's structured logging).
A servlet filter reads/generates `X-Correlation-Id`, puts it in the MDC, and echoes it on the
response. Redemption outcomes are logged at INFO with `couponCode`, `userId`, `outcome`,
`resolvedCountry` — no PII beyond the caller-supplied `userId`.

**Contract**: every request log line carries `correlationId`. Default profile logs JSON; `dev`
may log human-readable.

#### 3. Observability

**File**: `src/main/resources/application.yml` (extend), `config/` as needed

**Intent**: Enable liveness/readiness probes; readiness reflects DB connectivity. Expose
`info` with build info (`spring-boot-maven-plugin` `build-info` goal).

**Contract**: `/actuator/health/liveness` and `/actuator/health/readiness` respond
independently; readiness `DOWN` when the DB is unreachable.

#### 4. Enforce the gates

**File**: `pom.xml` (JaCoCo rule → `verify`), `ArchitectureTest.java` (finalize rules)

**Intent**: Make the coverage threshold and architecture rules build-breaking, not advisory.
Finalize ArchUnit: domain purity, adapter isolation, controllers depend only on `application`,
no `adapter` type leaks through a controller signature.

**Contract**: `mvn verify` fails if line coverage on `domain` + `application` < 0.80 or any
ArchUnit rule is violated.

#### 5. End-to-end smoke script

**File**: `scripts/smoke.sh`

**Intent**: A shell script that, against a running instance, walks the full happy path and
every error outcome with `curl`, asserting status codes. Referenced from the README.

**Contract**: exits non-zero on the first unexpected status. Not part of `mvn verify`
(requires a running app + DB).

### Success Criteria

#### Automated Verification

- `mvn -B verify` passes end to end: unit, slice, all integration tests, `RedemptionConcurrencyIT`,
  coverage gate, ArchUnit
- `build-info` is generated and served at `/actuator/info`
- CI is green on the final branch

#### Manual Verification

- Following the README from a clean clone yields a running service and every documented
  response
- `scripts/smoke.sh` passes against a locally running instance
- Log lines are JSON and carry a `correlationId`; a redemption emits one INFO line with the
  outcome
- `/actuator/health/readiness` flips to `DOWN` when `docker compose stop db`, back to `UP`
  after `start`
- Swagger UI documents all three endpoints and the `problem+json` error shape

**Implementation Note**: Final phase — confirm the full manual walkthrough before calling the
task done.

---

## Testing Strategy

### Unit Tests (no Spring context)

- Value objects: `CouponCode` normalization and case-insensitive equality (`WIOSNA` ==
  `wiosna`), blank/oversize rejection; `Country` ISO validation; `UsageLimit` positivity.
- `Coupon` factory: `createdAt` from an injected fixed `Clock`, `currentUses = 0`, query
  methods (`hasCountryRestriction`, `isExhausted`, `remainingUses`).
- `RedeemCouponService` with stubbed ports: each outcome branch; insert-first ordering
  (repeat user on an exhausted coupon → `ALREADY_REDEEMED`); country check runs before the
  transactional step; unrestricted coupon never calls `GeoIpResolver`.
- `CountryResolution` semantics; `ClientIpResolver` override-flag behaviour.

### Integration Tests (Testcontainers PostgreSQL + real Liquibase)

- Migrations apply cleanly on a fresh database.
- Persistence: case-insensitive code uniqueness and lookup; `coupon_redemption` unique
  constraint; the atomic `UPDATE` returns `0` at the cap.
- Full redemption flow across all outcomes (`GeoIpResolver` stubbed as a test bean).
- **`RedemptionConcurrencyIT`** — the load-bearing test:
  - `maxUses = 50`, `200` distinct users, released together → exactly `50` `200`s, `150`
    `USAGE_LIMIT_REACHED`, `current_uses == 50`, `50` redemption rows.
  - Same user `200` times → exactly `1` success, `199` `ALREADY_REDEEMED`, `current_uses == 1`.

### Web Slice Tests (`@WebMvcTest`)

- Every endpoint: success shape + status.
- Every error → `application/problem+json` with the correct `code` and HTTP status:
  `VALIDATION_ERROR` 400, `COUPON_NOT_FOUND` 404, `DUPLICATE_CODE` 409,
  `USAGE_LIMIT_REACHED` 409, `ALREADY_REDEEMED` 409, `COUNTRY_NOT_ALLOWED` 403,
  `COUNTRY_NOT_DETERMINED` 422.

### Adapter Tests (WireMock)

- `IpApiGeoIpResolver`: resolved, provider-fail, 429/5xx, timeout, cache hit, circuit-open,
  private-IP short-circuit.

### Architecture Tests (ArchUnit)

- Domain purity (no Spring / Jakarta Persistence / HTTP), adapter isolation, controller
  dependency direction. Build-breaking from Phase 5.

### Manual Testing Steps

1. `docker compose up -d db` → `mvn spring-boot:run -Dspring-boot.run.profiles=dev`.
2. Create `{"code":"WIOSNA","maxUses":2,"country":"PL"}` → `201`.
3. `GET /api/v1/coupons/wiosna` → `200` (case-insensitive).
4. Redeem as `u1` with a PL IP override → `200`, `remainingUses: 1`.
5. Redeem as `u1` again → `409` `ALREADY_REDEEMED`.
6. Redeem as `u2`, `u3` → `200` then `409` `USAGE_LIMIT_REACHED`.
7. Redeem a fresh coupon with a non-PL IP → `403` `COUNTRY_NOT_ALLOWED`; with `127.0.0.1`
   → `422` `COUNTRY_NOT_DETERMINED`.
8. `POST` to an unknown code → `404` `COUPON_NOT_FOUND`.
9. `docker compose stop db` → `/actuator/health/readiness` is `DOWN`.

## Performance Considerations

- Redemption is one `SELECT` (load) + at most one geo-IP call (cached, off-transaction) +
  one short transaction of two writes. No application locks; contention is a single-row lock
  held for the duration of the atomic `UPDATE` only.
- The geo-IP Caffeine cache (short TTL, bounded size) keeps the service under the free
  provider's rate ceiling; the circuit breaker prevents a slow provider from consuming
  request threads.
- Stateless application: scale horizontally by adding instances behind the load balancer;
  PostgreSQL is the single coordination point and can be scaled with connection pooling /
  read replicas if ever needed (not in scope).
- HikariCP pool sized modestly; transactions are short so pool pressure stays low.

## Migration Notes

Not applicable — greenfield, no existing data. Liquibase changesets are additive and applied
automatically on startup. Rollback for the recruitment context is "drop schema and re-run".

## References

- Task source: `context/foundation/task.md`
- Plan brief: `context/changes/coupon-service/plan-brief.md`
- Atomic conditional-update pattern for counters: enforced at
  `adapter/persistence/CouponJdbcRepository` (Phase 3)
- Concurrency proof: `src/test/java/com/example/coupons/RedemptionConcurrencyIT.java` (Phase 3)

## Progress

> Convention: `- [ ]` pending, `- [x]` done. Append ` — <commit sha>` when a step lands. Do not rename step titles. See `references/progress-format.md`.

### Phase 1: Project Skeleton & Infrastructure

#### Automated

- [x] 1.1 `mvn -B verify` succeeds with no domain code
- [x] 1.2 Application context loads (`@SpringBootTest` + Testcontainers PostgreSQL)
- [x] 1.3 ArchUnit test runs and passes (vacuously)
- [ ] 1.4 CI workflow is valid and green on the initial push

#### Manual

- [x] 1.5 `docker compose up -d db` + `mvn spring-boot:run` starts the app cleanly
- [x] 1.6 `/actuator/health` returns `UP` with the `db` component up
- [x] 1.7 `/swagger-ui.html` loads
- [x] 1.8 `docker build .` succeeds and the container starts against compose

### Phase 2: Domain Model & Coupon Creation

#### Automated

- [x] 2.1 Domain unit tests pass (`CouponCode`, `Country`, `UsageLimit`, `Coupon` factory)
- [x] 2.2 `@WebMvcTest` slice tests pass (create 201 + Location; duplicate 409; invalid 400; get-missing 404)
- [x] 2.3 Testcontainers integration test passes (case-insensitive persist/lookup; case-only-diff duplicate → `DUPLICATE_CODE`)
- [x] 2.4 Liquibase `0001` migration applies cleanly on a fresh database
- [x] 2.5 ArchUnit rules still pass
- [x] 2.6 Coverage gate met for code added this phase

#### Manual

- [x] 2.7 `POST /api/v1/coupons` for `WIOSNA` → `201` with `createdAt` and `currentUses: 0`
- [x] 2.8 `GET /api/v1/coupons/wiosna` → `200` (case-insensitive)
- [x] 2.9 Re-`POST` `{"code":"wiosna"}` → `409` `DUPLICATE_CODE` as `problem+json`
- [x] 2.10 OpenAPI doc shows both endpoints with documented schemas

### Phase 3: Redemption — Core Rules & Concurrency

#### Automated

- [x] 3.1 Domain + use-case unit tests pass (each outcome; insert-first ordering)
- [x] 3.2 `@WebMvcTest` slice tests pass (200 happy path; `problem+json` for 404/409/409/400)
- [x] 3.3 `0002` migration applies cleanly on a fresh database
- [x] 3.4 Full-flow integration test passes (create `maxUses=2`; two redeems; third → limit; repeat user → already)
- [x] 3.5 `RedemptionConcurrencyIT` passes — distinct-users variant (exactly `N` succeed, no counter drift)
- [x] 3.6 `RedemptionConcurrencyIT` passes — same-user variant (exactly `1` succeeds)
- [x] 3.7 ArchUnit rules pass; coverage gate met

#### Manual

- [x] 3.8 `POST /coupons/wiosna/redemptions {"userId":"u1"}` → `200`, `remainingUses` decremented
- [x] 3.9 Repeat as `u1` → `409` `ALREADY_REDEEMED`
- [x] 3.10 Exhaust with fresh users, then one more → `409` `USAGE_LIMIT_REACHED`
- [x] 3.11 `POST` to a non-existent code → `404` `COUPON_NOT_FOUND`
- [x] 3.12 All error bodies are `application/problem+json` with a `code` field

### Phase 4: Geo-IP Country Restriction

#### Automated

- [x] 4.1 `IpApiGeoIpResolverTest` passes all WireMock cases
- [x] 4.2 `ClientIpResolver` unit tests pass (socket default; override only when flag on)
- [x] 4.3 Use-case unit tests pass (allowed/blocked/undetermined; unrestricted skips resolver)
- [x] 4.4 Full-flow integration tests pass (PL→200, DE→403, undetermined→422, no-country→200)
- [x] 4.5 Country check asserted to run before the transaction
- [x] 4.6 ArchUnit rules pass; coverage gate met

#### Manual

- [x] 4.7 Override-on: PL IP → `200`; non-PL IP → `403` `COUNTRY_NOT_ALLOWED`; `127.0.0.1` → `422` `COUNTRY_NOT_DETERMINED`
- [x] 4.8 Coupon with no country restriction ignores the IP
- [x] 4.9 Geo-IP provider unreachable → restricted redemptions `422`, unrestricted `200`, circuit opens (logs)

### Phase 5: Hardening, Docs & Observability

#### Automated

- [x] 5.1 `mvn -B verify` passes end to end (all tests + `RedemptionConcurrencyIT` + coverage + ArchUnit)
- [x] 5.2 `build-info` generated and served at `/actuator/info`
- [ ] 5.3 CI green on the final branch

#### Manual

- [x] 5.4 README from a clean clone yields a running service and every documented response
- [x] 5.5 `scripts/smoke.sh` passes against a local instance
- [x] 5.6 Logs are JSON with `correlationId`; a redemption emits one INFO outcome line
- [x] 5.7 `/actuator/health/readiness` flips `DOWN`/`UP` with `docker compose stop/start db`
- [x] 5.8 Swagger UI documents all three endpoints and the `problem+json` error shape

### Amendment (2026-09-01): country is mandatory

The plan above shipped `country` as optional (`Coupon.country` nullable, `hasCountryRestriction()`
guarding the geo-IP check, "no-country" coupons redeeming unconditionally). That reading of the
task spec — `country` listed as a plain coupon field, not called out as optional the way the
one-redemption-per-user rule is — was reconsidered and reversed: **every coupon now requires a
country**. Concretely, superseding the passages above:

- `CreateCouponRequest.country` is `@NotBlank` (was optional `@Pattern`-only); creating a coupon
  without one now returns `400 VALIDATION_ERROR`.
- `Coupon`'s compact constructor rejects a `null` country; `Coupon.create` always resolves
  `Country.of(rawCountry)`. `hasCountryRestriction()` is removed — no coupon is unrestricted.
- `CouponService.checkCountryRestriction` unconditionally resolves and checks the caller's
  country; the early-return "no restriction → skip" branch is gone. Every successful redemption
  now carries a non-null `resolvedCountry`.
- `CouponRedemption.resolvedCountry` is required (was nullable for unrestricted coupons); the
  `coupon.country` / `coupon_redemption.resolved_country` DB columns are `NOT NULL`.
- Tests that relied on an unrestricted coupon (`CouponRedemptionCountryIT`'s
  `an_unrestricted_coupon_ignores_geo_ip_entirely`, the no-country paths in `CouponServiceTest`,
  `CouponTest`, `CouponRedemptionTest`) were updated or replaced accordingly; `RedemptionConcurrencyIT`
  and `CouponRedemptionApiIT` now stub `GeoIpResolver` (`support/StubGeoIpConfiguration`) since
  every redemption resolves a country.

Net effect on the checklists above: 4.3's "unrestricted skips resolver" and 4.4/4.8/4.9's
"no-country → 200" / "unrestricted → 200" cases no longer apply — there is no unrestricted case.
