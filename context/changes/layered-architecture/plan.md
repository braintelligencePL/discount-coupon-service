# Restructure to a Pragmatic 4-Layer Architecture — Implementation Plan

## Overview

Move `com.example.coupons` from its current single-module **hexagonal (ports &
adapters)** layout to a **pragmatic layered architecture**:

```
api  →  application  →  domain
                ▲
        infrastructure   (implements the ports Application owns)
```

- **api** — controllers, request/response DTOs, HTTP plumbing (`ApiExceptionHandler`,
  `ClientIpResolver`, `CorrelationIdFilter`).
- **application** — `CouponService` (use-case orchestration + the transaction
  boundary), its command/result records, and the three port interfaces
  (`CouponRepository`, `CouponRedemptionRepository`, `GeoIpResolver`).
- **domain** — framework-free `Coupon` aggregate, value objects, domain exceptions.
- **infrastructure** — Spring Data JDBC persistence, the `ip-api.com` geo-IP client,
  and the geo-IP configuration.

This is a **pure structural refactor**: package moves, one small wiring change, an
`ArchitectureTest` rewrite, and a README pass. **No behaviour, API-contract, HTTP
status, `problem+json`, OpenAPI, or database-schema change.** The existing 28-test
suite (22 unit + 6 Testcontainers ITs) is the regression check and must stay green
after every phase.

Three phases, ordered so each ends green on `./mvnw verify`:
1. Ports `domain.port` → `application.port`; inline the row mapper.
2. Rename every remaining package to `api/*` + `infrastructure/*`; move
   `GeoIpProperties`; switch `ClientIpResolver` to `@Value`; mirror the test
   packages; **rewrite `ArchitectureTest`** for the 4-layer model; fix the
   `CouponsApplication` javadoc.
3. `README.md` architecture section + ASCII diagram.

## Current State Analysis

Package tree today (post-`simplify-project`):

| Package | Contents |
| --- | --- |
| `com.example.coupons` | `CouponsApplication` (`@SpringBootApplication`, `@ConfigurationPropertiesScan`, `@Bean Clock`) |
| `adapter.web` | `CouponController`, `CouponRedemptionController`, `ApiExceptionHandler`, `ClientIpResolver`, `CorrelationIdFilter` |
| `adapter.web.dto` | `CreateCouponRequest`, `RedeemCouponRequest`, `CouponResponse`, `RedemptionResponse` |
| `adapter.persistence` | `CouponCrudRepository`, `CouponJdbcRepository`, `CouponRow`, `CouponRowMapper`, `CouponRedemptionCrudRepository`, `CouponRedemptionJdbcRepository`, `CouponRedemptionRow`, `PersistenceExceptions` |
| `adapter.geoip` | `IpApiGeoIpResolver`, `GeoIpClientConfig` |
| `application` | `CouponService`, `CreateCouponCommand`, `RedeemCouponCommand`, `RedemptionResult` |
| `domain.model` | `Coupon`, `CouponCode`, `CouponRedemption`, `Country`, `UsageLimit` |
| `domain.exception` | `AlreadyRedeemedException`, `CountryNotAllowedException`, `CountryNotDeterminedException`, `CouponNotFoundException`, `DomainValidationException`, `DuplicateCouponCodeException`, `UsageLimitReachedException` |
| `domain.port` | `CouponRepository`, `CouponRedemptionRepository`, `GeoIpResolver` |
| `config` | `GeoIpProperties` (`@ConfigurationProperties(prefix = "geoip")`) |

Cross-package reference facts (verified by grep):

- **`domain.port`** is imported by `CouponService` (main), `IpApiGeoIpResolver`,
  `CouponJdbcRepository`, `CouponRedemptionJdbcRepository`, plus tests
  `CouponServiceTest` and `CouponRedemptionCountryIT`. The three port files import
  only `domain.model` / `domain.exception` types — moving them is a package-line
  change and nothing else.
- **`CouponRowMapper`** (`toNewRow`, `toDomain`) is referenced only inside
  `CouponJdbcRepository` (lines 22, 33). `CouponRedemptionJdbcRepository` already
  maps its row inline — there is no redemption mapper.
- **`config.GeoIpProperties`** is used by `GeoIpClientConfig` (same
  `infrastructure` destination — becomes a same-package reference) and by
  `ClientIpResolver`, which only reads `properties.allowIpOverride()` — a single
  boolean bound from `geoip.allow-ip-override` (`application.yml` `false`,
  `application-dev.yml` / `application-test.yml` `true`).
- **`ArchitectureTest`** (ArchUnit, 2 `@ArchTest` rules) enforces the hexagonal
  model: `the_domain_is_pure` (domain references no outer package / Spring / JDBC /
  Jackson / Caffeine / Resilience4j) and `respects_hexagonal_layering`
  (`layeredArchitecture()` with Web / Persistence / GeoIp / Config layers mutually
  isolated). Both need updating for the new package names; the second needs a full
  rewrite.
- **`CouponsApplication`** javadoc describes "single-module hexagonal architecture"
  with `domain` / `application` / `adapter` bullet points.
- **Tests unaffected by the move**: `CouponApiIT`, `CouponRedemptionApiIT`,
  `RedemptionConcurrencyIT`, `TestcontainersConfiguration` (root package; import
  only `application.*` / `domain.exception.*`), and the five `domain.model.*Test`
  classes (domain sub-packages are kept).
- **Component scan** base is `com.example.coupons`; `@ConfigurationPropertiesScan`
  scans the same tree. Every target package stays under that root, so Spring DI and
  properties binding are unaffected by the moves.

### Key Discoveries

- The refactor introduces exactly **one** dependency edge that the target layering
  forbids: `ClientIpResolver` (destined for `api.support`) currently imports
  `GeoIpProperties` (destined for `infrastructure.geoip`) — an `api → infrastructure`
  edge. Resolved by having `ClientIpResolver` inject
  `@Value("${geoip.allow-ip-override:false}") boolean` instead of the whole record.
  Same property, same default, identical behaviour.
- `CouponService.redeem` resolves the caller country **before** opening the
  `TransactionTemplate` block (`application/CouponService.java:79-84`) and inserts
  the redemption row **before** the conditional counter `UPDATE`
  (`registerRedemption`, lines 112-123). These are the project's core concurrency
  guarantees. This refactor moves the class's package and nothing inside its
  method bodies.
- `@WebMvcTest(CouponController.class)` + `@Import(ApiExceptionHandler.class)` in
  `CouponControllerTest` — both referenced types move (`api.web` / `api.support`),
  so the test moves to `api.web` and gains one import for `ApiExceptionHandler`.
- `IpApiGeoIpResolverTest` builds its own `RestClient` / Caffeine cache /
  `CircuitBreakerRegistry` and never touches `GeoIpProperties`; only its package
  line changes.
- `CouponRedemptionCountryIT.StubGeoIpResolver implements GeoIpResolver` — the one
  import line retargets from `domain.port` to `application.port`; the IT itself
  stays in the root test package.

## Desired End State

Package tree:

```
com.example.coupons
├── CouponsApplication              composition root; @Bean Clock; javadoc = layered
├── api
│   ├── web        CouponController, CouponRedemptionController, CouponApi, CouponRedemptionApi
│   └── dto        CreateCouponRequest, RedeemCouponRequest, CouponResponse, RedemptionResponse
├── application
│   ├── CouponService
│   ├── dto        CreateCoupon, RedeemCoupon, RedemptionResult   (Phase 5)
│   └── port       CouponRepository, CouponRedemptionRepository, GeoIpResolver
├── domain
│   ├── model      Coupon, CouponCode, CouponRedemption, Country, UsageLimit
│   └── exception  (7 exceptions)
└── infrastructure
    ├── web          ApiExceptionHandler, ClientIpResolver, CorrelationIdFilter   (inbound; Phase 4)
    ├── persistence  CouponCrudRepository, CouponJdbcRepository, CouponRow,
    │                CouponRedemptionCrudRepository, CouponRedemptionJdbcRepository,
    │                CouponRedemptionRow, PersistenceExceptions
    └── geoip        IpApiGeoIpResolver, GeoIpClientConfig, GeoIpProperties
```

- `CouponRowMapper.java` is gone; its two mappings are private methods on
  `CouponJdbcRepository`.
- `ClientIpResolver` no longer imports any `infrastructure` / `config` type
  (it binds `geoip.allow-ip-override` via `@Value`).
- The OpenAPI `@Operation` / `@ApiResponses` / `@Tag` annotations live on
  `CouponApi` / `CouponRedemptionApi` interfaces; the controllers `implements` them
  and carry only routing + delegation.
- `ArchitectureTest` enforces (after Phase 4's carve-out): the **Inbound** group
  (`api` + `infrastructure.web`) is accessed by nobody; the sealed **Infrastructure**
  group (`infrastructure.persistence` + `infrastructure.geoip`) is accessed by
  nobody; `application` is accessed only by Inbound + Infrastructure; `domain` by
  all — plus the domain-purity rule (domain references no `api` / `application` /
  `infrastructure` package and no Spring / `jakarta.persistence` / Jackson /
  Caffeine / Resilience4j).
- Test source packages mirror the new main packages 1:1; **no test is added or
  removed**; test method bodies are unchanged in substance.
- `README.md`'s "Architecture" section and ASCII diagram describe the layered model.
- `./mvnw verify` (JDK 21) is green after every phase; the app boots on the `dev`
  profile and serves create / get / redeem with every documented status code and
  `problem+json` body unchanged.

**Verification**: `JAVA_HOME=$(/usr/libexec/java_home -v 21) ./mvnw -B verify` passes
at the end of each phase. `git diff --stat` shows only package/import lines plus the
`ArchitectureTest`, `ClientIpResolver`, `CouponJdbcRepository`, `CouponsApplication`,
and `README.md` edits. `grep -rn "com.example.coupons.adapter\|com.example.coupons.config\|domain.port" src`
returns nothing.

## What We're NOT Doing

- **No** behaviour, request/response shape, HTTP status, `problem+json`, OpenAPI, or
  logging change.
- **No** database schema, migration, or query change.
- **No** `pom.xml` dependency change — ArchUnit, Resilience4j, Caffeine, springdoc,
  Testcontainers all stay exactly as they are.
- **Not** merging the two repository ports — `CouponRepository` and
  `CouponRedemptionRepository` stay separate.
- **Not** flattening the `domain/model` + `domain/exception` sub-packages.
- **Not** making `application` framework-free — `@Service`, the injected
  `PlatformTransactionManager`, and the programmatic `TransactionTemplate` stay.
- **Not** dropping the application command/result records.
- **Not** collapsing the persistence `*CrudRepository` / `*JdbcRepository` / `*Row`
  split — only the standalone `CouponRowMapper` file is removed.
- **Not** changing `IpApiGeoIpResolver`'s resilience / cache / fail-closed logic.
- **Not** touching `README2.md` (left describing "hexagonal" — see Open Risks),
  `logback-spring.xml`, `Dockerfile`, `docker-compose.yml`, or the Liquibase
  changelogs.
- **Not** adding or removing any test.
- **Not** committing — changes are left in the working tree; verification is
  `./mvnw verify` per phase.

## Implementation Approach

Package moves are mechanical and compiler-checked: a missed import fails the build,
not a test. So each phase leans on `./mvnw verify` (which compiles main + test and
runs both Surefire and Failsafe) as its gate.

Phase 1 keeps the *old* `ArchitectureTest` green — moving the ports into
`application.port` does not create any dependency the hexagonal rules forbid
(`adapter.persistence` → `application.port` is `Persistence → Application`, already
allowed). Phase 2 does the bulk rename and rewrites `ArchitectureTest` in the same
phase, so the new 4-layer rules and the renamed packages land together and the
phase still ends green. Phase 3 is documentation only.

Prefer an IDE "Move / Rename package" refactor for the bulk of Phase 2 so imports
update in one pass; then run `grep` for the old package roots to catch anything the
IDE missed (string references in javadoc, `@Import`, etc.).

## Critical Implementation Details

- **The `ClientIpResolver` wiring change must land in the same phase as the
  `GeoIpProperties` move (Phase 2).** If `GeoIpProperties` moves to
  `infrastructure.geoip` while `ClientIpResolver` still imports it, the rewritten
  `ArchitectureTest` fails on an `api → infrastructure` edge. Switching
  `ClientIpResolver` to `@Value("${geoip.allow-ip-override:false}")` removes the
  edge; the property key, the `false` default, and the three profile YAMLs are
  untouched.
- **`CouponService` method bodies are moved verbatim.** The country resolution
  happening before the `transactionTemplate.execute(...)` block, and the redemption
  `insert` happening before `incrementUsageIfBelowLimit`, are the concurrency
  contract `RedemptionConcurrencyIT` exists to prove. This refactor changes the
  class's `package` line and its port imports — nothing else.
- **`ArchitectureTest` layered rule** uses
  `layeredArchitecture().consideringOnlyDependenciesInLayers()` with all four layers
  (`..api..`, `..application..`, `..domain..`, `..infrastructure..`) populated in
  this same phase. `CouponsApplication` sits in the root package, outside every
  layer — `consideringOnlyDependenciesInLayers()` ignores it, which is intended (it
  is the composition root and legitimately wires infrastructure beans).

---

## Phase 1: Move the ports into `application`; inline the row mapper

### Overview

Relocate the three port interfaces from `domain.port` to a new `application.port`
package, and fold `CouponRowMapper` into `CouponJdbcRepository`. The hexagonal
`ArchitectureTest` stays green.

### Changes Required:

#### 1. Move the three port interfaces

**Files**: `domain/port/CouponRepository.java`,
`domain/port/CouponRedemptionRepository.java`, `domain/port/GeoIpResolver.java` →
`application/port/` (delete the `domain/port/` package).

**Intent**: Application owns the contracts Infrastructure implements. The interface
bodies are unchanged — they already reference only `domain.model` /
`domain.exception` types.

**Contract**: New package `com.example.coupons.application.port`. `domain` no longer
contains a `port` package. `Application → Domain` is the only dependency direction
these interfaces introduce.

#### 2. Retarget port imports

**Files**: `application/CouponService.java`,
`adapter/geoip/IpApiGeoIpResolver.java`,
`adapter/persistence/CouponJdbcRepository.java`,
`adapter/persistence/CouponRedemptionJdbcRepository.java`,
`src/test/java/com/example/coupons/application/CouponServiceTest.java`,
`src/test/java/com/example/coupons/CouponRedemptionCountryIT.java`.

**Intent**: Update the `import com.example.coupons.domain.port.*` lines to
`com.example.coupons.application.port.*`.

**Contract**: `grep -rn "domain.port" src` returns nothing. No other line in these
files changes.

#### 3. Inline `CouponRowMapper` into `CouponJdbcRepository`

**Files**: `adapter/persistence/CouponJdbcRepository.java` (edit),
`adapter/persistence/CouponRowMapper.java` (delete).

**Intent**: Move `toNewRow(Coupon)` and `toDomain(CouponRow)` in as `private static`
methods on `CouponJdbcRepository`; update the two call sites (`save`, `findByCode`).
Row↔domain translation and the `DuplicateKeyException` → `DuplicateCouponCodeException`
handling now live in one class.

**Contract**: `grep -rn "CouponRowMapper" src` returns nothing. `CouponJdbcRepository`
still `implements CouponRepository` with identical method behaviour;
`CouponRedemptionJdbcRepository` is untouched (its mapping was already inline).

### Success Criteria:

#### Automated Verification:

- `JAVA_HOME=$(/usr/libexec/java_home -v 21) ./mvnw -B verify` passes
- `grep -rn "domain.port\|CouponRowMapper" src` returns nothing
- `ArchitectureTest` (still the hexagonal rules) passes as part of the verify run
- All 28 tests run and pass (22 Surefire + 6 Failsafe)

#### Manual Verification:

- `docker compose up -d db` + `./mvnw spring-boot:run -Dspring-boot.run.profiles=dev`
  boots cleanly
- `POST /api/v1/coupons` → `201`; `GET /api/v1/coupons/{code}` → `200`;
  `POST /api/v1/coupons/{code}/redemptions` → `200`

**Implementation Note**: After automated verification passes, pause for manual
confirmation before Phase 2.

---

## Phase 2: Rename packages to `api/*` + `infrastructure/*`; rewrite `ArchitectureTest`

### Overview

The bulk move. `adapter.web` → `api.{web,support}` + `api.dto`;
`adapter.persistence` → `infrastructure.persistence`; `adapter.geoip` +
`config.GeoIpProperties` → `infrastructure.geoip`. Switch `ClientIpResolver` to
`@Value`. Mirror the test packages. Rewrite `ArchitectureTest` for the 4-layer
model. Fix the `CouponsApplication` javadoc.

### Changes Required:

#### 1. Move the inbound (web) classes

**Files**: `adapter/web/CouponController.java`,
`adapter/web/CouponRedemptionController.java` → `api/web/`;
`adapter/web/ApiExceptionHandler.java`, `adapter/web/ClientIpResolver.java`,
`adapter/web/CorrelationIdFilter.java` → `api/support/`;
`adapter/web/dto/*.java` (4 files) → `api/dto/`.

**Intent**: Split the old `adapter.web` package into controller / support /
DTO sub-packages under `api`.

**Contract**: New packages `com.example.coupons.api.web`,
`com.example.coupons.api.support`, `com.example.coupons.api.dto`. Controllers import
DTOs from `api.dto` and `ApiExceptionHandler` stays picked up by
`@RestControllerAdvice` component scan. No mapping / annotation / route change.

#### 2. Switch `ClientIpResolver` to a `@Value`-bound flag

**File**: `api/support/ClientIpResolver.java`.

**Intent**: Replace the `GeoIpProperties` constructor parameter with
`@Value("${geoip.allow-ip-override:false}") boolean allowOverride`. Drop the
`GeoIpProperties` import. Keep the field, the `OVERRIDE_HEADER` / `OVERRIDE_PARAM`
constants, the `resolve(HttpServletRequest)` logic, and the class javadoc as-is.

**Contract**: `ClientIpResolver` imports no `com.example.coupons.infrastructure` or
`com.example.coupons.config` type. Behaviour identical: override honoured only when
`geoip.allow-ip-override=true`.

#### 3. Move the persistence classes

**Files**: `adapter/persistence/*.java` (7 files after Phase 1) →
`infrastructure/persistence/`.

**Intent**: Straight package move. Implementations of `application.port`
repositories; `PersistenceExceptions` stays package-private alongside them.

**Contract**: New package `com.example.coupons.infrastructure.persistence`.
`CouponJdbcRepository` / `CouponRedemptionJdbcRepository` still `implements` the
`application.port` interfaces. `@Repository` beans still discovered by scan.

#### 4. Move the geo-IP classes and `GeoIpProperties`

**Files**: `adapter/geoip/IpApiGeoIpResolver.java`,
`adapter/geoip/GeoIpClientConfig.java` → `infrastructure/geoip/`;
`config/GeoIpProperties.java` → `infrastructure/geoip/` (delete the `config`
package).

**Intent**: The geo-IP client, its `@Configuration`, and its
`@ConfigurationProperties` record live together. `GeoIpClientConfig`'s reference to
`GeoIpProperties` becomes same-package.

**Contract**: New package `com.example.coupons.infrastructure.geoip`. `config`
package no longer exists. `@ConfigurationPropertiesScan` (base
`com.example.coupons`) still binds `geoip.*` to `GeoIpProperties`. `grep -rn
"com.example.coupons.config" src` returns nothing.

#### 5. Fix the `CouponsApplication` javadoc

**File**: `CouponsApplication.java`.

**Intent**: Rewrite the class-level javadoc's "single-module hexagonal architecture"
paragraph and bullet list to describe the layered model (`api` → `application` →
`domain`; `infrastructure` implements Application's ports). No code change — the
`@Bean Clock` and annotations stay.

**Contract**: Javadoc names the four layers; no mention of `adapter` or "hexagonal".

#### 6. Mirror the test packages

**Files**:
`src/test/java/com/example/coupons/adapter/web/CouponControllerTest.java` →
`api/web/` (add `import com.example.coupons.api.support.ApiExceptionHandler;`);
`src/test/java/com/example/coupons/adapter/geoip/IpApiGeoIpResolverTest.java` →
`infrastructure/geoip/`.

**Intent**: Test source packages track the classes under test. `CouponControllerTest`
keeps `@WebMvcTest(CouponController.class)` + `@Import(ApiExceptionHandler.class)`
and its four tests unchanged in substance; `IpApiGeoIpResolverTest` keeps its five
tests and self-built collaborators. `CouponServiceTest` stays in `application` (its
port imports were already retargeted in Phase 1). Root-package ITs
(`CouponApiIT`, `CouponRedemptionApiIT`, `RedemptionConcurrencyIT`,
`CouponRedemptionCountryIT`, `TestcontainersConfiguration`) and the five
`domain/model/*Test` classes are untouched.

**Contract**: `find src/test -name '*.java'` shows test packages 1:1 with main
packages (except the intentionally root-level ITs and `architecture`). Test count
unchanged: 22 unit + 6 IT.

#### 7. Rewrite `ArchitectureTest` for the 4-layer model

**File**: `src/test/java/com/example/coupons/architecture/ArchitectureTest.java`.

**Intent**: Replace both rules. Rule 1 (`the_domain_is_pure`): domain must not
depend on `..api..`, `..application..`, `..infrastructure..`, or Spring /
`jakarta.persistence` / Jackson / Caffeine / Resilience4j. Rule 2
(`respects_layering`): `layeredArchitecture().consideringOnlyDependenciesInLayers()`
with layers `Api = ..api..`, `Application = ..application..`, `Domain = ..domain..`,
`Infrastructure = ..infrastructure..`; `Api` may not be accessed by any layer;
`Infrastructure` may not be accessed by any layer; `Application` may only be
accessed by `Api` and `Infrastructure`; `Domain` may only be accessed by `Api`,
`Application`, `Infrastructure`. Update the class javadoc.

**Contract**: Two `@ArchTest` rules, both green. An `api → infrastructure` or
`infrastructure → api` reference, or a Spring import in `domain`, fails the build.

### Success Criteria:

#### Automated Verification:

- `JAVA_HOME=$(/usr/libexec/java_home -v 21) ./mvnw -B verify` passes
- `grep -rn "com.example.coupons.adapter\|com.example.coupons.config" src` returns nothing
- `grep -rn "hexagon\|adapter" src/main/java/com/example/coupons/CouponsApplication.java` returns nothing
- `ArchitectureTest` runs its 2 rewritten rules, both green
- Test totals unchanged: Surefire 22, Failsafe 6, all passing

#### Manual Verification:

- App boots on the `dev` profile against the compose DB; `/actuator/health` `UP`
  with `db`; `/actuator/info` shows build info; `/swagger-ui.html` loads and lists
  both tags
- Full redemption ladder returns documented codes: `201` create, `200` get, `200`
  redeem, `409 ALREADY_REDEEMED` on repeat, `409 USAGE_LIMIT_REACHED` at cap,
  `404 COUPON_NOT_FOUND` unknown code, `400 VALIDATION_ERROR` blank `userId`
- Country-restricted coupon with `geoip.allow-ip-override=true`: PL IP → `200`,
  non-PL IP → `403 COUNTRY_NOT_ALLOWED`, `127.0.0.1` → `422 COUNTRY_NOT_DETERMINED`
- Logs are still JSON with a `correlationId`; responses still echo `X-Correlation-Id`

**Implementation Note**: After automated verification passes, pause for manual
confirmation before Phase 3.

---

## Phase 3: README architecture pass

### Overview

Bring `README.md` in line with the layered structure. Documentation only.

### Changes Required:

#### 1. `README.md` — Architecture section and diagram

**File**: `README.md`.

**Intent**: Rewrite the `### Architecture` block (the ASCII diagram at lines ~204-216
and the ArchUnit paragraph after it) to describe `api` → `application` → `domain`
with `infrastructure` implementing Application's ports. Update the one-line
`- **Architecture:**` bullet near the top from "single-module hexagonal (ports &
adapters)" to the layered phrasing. Adjust the two inline "Nothing below the web
adapter changes" mentions in the `userId` section to "Nothing in `application` or
below changes". Leave every API / config / Docker / observability section as-is.

**Contract**: `grep -in "hexagon\|ports & adapters" README.md` returns nothing. The
diagram lists `api` / `application` / `domain` / `infrastructure`. The ArchUnit
paragraph describes the 4-layer enforcement.

### Success Criteria:

#### Automated Verification:

- `grep -in "hexagon\|ports & adapters\|adapter " README.md` returns nothing
  (outside any unrelated word match)
- `JAVA_HOME=$(/usr/libexec/java_home -v 21) ./mvnw -B verify` still passes (no code
  touched, run as a final guard)

#### Manual Verification:

- `README.md` reads coherently end to end; the diagram matches the actual package
  tree; the quick-start and API sections still work verbatim against a running app

**Implementation Note**: After automated verification passes, pause for manual
confirmation before Phase 4.

---

## Phase 4: Move inbound HTTP glue to `infrastructure/web`

### Overview

Relocate the three inbound-HTTP support classes from `api/support` into a new
`infrastructure/web` package, so `infrastructure` holds `web/` (inbound) alongside
`persistence/` and `geoip/` (outbound). **Supersedes the `api/support` placement
from Phase 2.** Because one controller injects `ClientIpResolver`, this creates an
`api → infrastructure.web` edge, so the `ArchitectureTest` layering rule is
reworked into an inbound / sealed-outbound carve-out that keeps the guardrail on
`persistence` + `geoip` intact. No behaviour, routing, `problem+json`, OpenAPI, or
logging change.

### Changes Required:

#### 1. Move the three support classes

**Files**: `api/support/ApiExceptionHandler.java`,
`api/support/ClientIpResolver.java`, `api/support/CorrelationIdFilter.java` →
`infrastructure/web/` (delete the emptied `api/support/` package).

**Intent**: Straight package move — `ApiExceptionHandler` (the
`@RestControllerAdvice` → `ProblemDetail` mapper), `CorrelationIdFilter` (servlet
MDC filter), and `ClientIpResolver` (`HttpServletRequest` → caller IP) are inbound
framework glue. Bodies unchanged.

**Contract**: New package `com.example.coupons.infrastructure.web`.
`ApiExceptionHandler` stays `public`. `@RestControllerAdvice`, `@Component`,
`@Order` beans are still discovered by component scan (base `com.example.coupons`).
`grep -rn "com.example.coupons.api.support" src` returns nothing;
`find src/main -path '*api/support*'` is empty.

#### 2. Retarget the production import

**File**: `api/web/CouponRedemptionController.java`.

**Intent**: `import com.example.coupons.api.support.ClientIpResolver;` →
`com.example.coupons.infrastructure.web.ClientIpResolver;`. Nothing else changes.

**Contract**: The controller compiles against the moved type; the injected bean is
the same.

#### 3. Retarget the test import

**File**: `src/test/java/com/example/coupons/api/web/CouponControllerTest.java`.

**Intent**: `@Import` import line → `infrastructure.web.ApiExceptionHandler`. Test
stays in `api.web` (it is a `@WebMvcTest(CouponController.class)` slice); the
cross-package `@Import` is test-only and ArchUnit ignores tests.

**Contract**: The 4 error-path assertions still pass — the advice is loaded via the
explicit `@Import`.

#### 4. Rework the `respects_layering` ArchUnit rule (carve-out)

**File**: `src/test/java/com/example/coupons/architecture/ArchitectureTest.java`.

**Intent**: Replace the four flat layers with an inbound / sealed-outbound model.
`the_domain_is_pure` is unchanged (it already forbids `..infrastructure..`, which
still covers `infrastructure.web`). Update the class javadoc.

**Contract**:

```
layeredArchitecture().consideringOnlyDependenciesInLayers()
    .layer("Domain").definedBy("..domain..")
    .layer("Application").definedBy("..application..")
    .layer("Inbound").definedBy("..api..", "..infrastructure.web..")
    .layer("Infrastructure").definedBy("..infrastructure.persistence..", "..infrastructure.geoip..")
    .whereLayer("Inbound").mayNotBeAccessedByAnyLayer()
    .whereLayer("Infrastructure").mayNotBeAccessedByAnyLayer()
    .whereLayer("Application").mayOnlyBeAccessedByLayers("Inbound", "Infrastructure")
    .whereLayer("Domain").mayOnlyBeAccessedByLayers("Inbound", "Application", "Infrastructure")
```

The four package identifiers are disjoint (no class in two layers); `CouponsApplication`
sits in the root package, outside every layer, and is ignored by
`consideringOnlyDependenciesInLayers()`.

#### 5. README architecture diagram

**File**: `README.md`.

**Intent**: Update the `### Architecture` diagram written in Phase 3 to split
`infrastructure` into `web` (inbound: exception handler, correlation-id filter,
client-IP resolver) and `persistence` + `geoip` (outbound). Note the ArchUnit
carve-out in the ArchUnit paragraph.

**Contract**: The diagram lists `infrastructure/web`, `infrastructure/persistence`,
`infrastructure/geoip`; the ArchUnit paragraph says the sealed part is
persistence + geoip.

#### 6. `CouponsApplication` javadoc

**File**: `src/main/java/com/example/coupons/CouponsApplication.java`.

**Intent**: The `infrastructure` bullet gains "and the inbound HTTP glue
(problem+json mapping, correlation-id filter, client-IP resolution)". One line.

**Contract**: Javadoc still names the four layers; no "hexagonal" / "adapter".

#### 7. Plan self-update

**Files**: `context/changes/layered-architecture/plan.md`,
`context/changes/layered-architecture/plan-brief.md`.

**Intent**: The Desired End State tree and brief no longer say `api/support`
(already updated in this edit); the brief's Architecture / Scope paragraphs reflect
`infrastructure/web`.

**Contract**: `grep -rn "api/support" context/changes/layered-architecture/` returns
nothing outside Phase 2's historical narrative and this Phase 4 block.

### Success Criteria:

#### Automated Verification:

- `JAVA_HOME=$(/usr/libexec/java_home -v 21) ./mvnw -B verify` passes
- `grep -rn "com.example.coupons.api.support" src` returns nothing;
  `find src/main -path '*api/support*'` is empty
- `ArchitectureTest` runs its 2 rules (rule 2 = the carve-out), both green
- Test totals unchanged: 28 (23 Surefire + 5 Failsafe), all passing

#### Manual Verification:

- App boots on `dev`; `/actuator/health` `UP` with `db`; `/swagger-ui.html` loads
- `GET /v3/api-docs` is byte-identical to before (tags `Coupons` / `Redemptions`,
  all `@Operation` summaries and `@ApiResponse` descriptions)
- Redemption ladder + country rule unchanged: 201 / 200 / 200 / 409 / 409 / 404 /
  400, and PL → 200, non-PL → 403, loopback → 422
- Logs are JSON with a `correlationId`; the response still echoes `X-Correlation-Id`
  (the filter is active from its new package)

**Implementation Note**: After automated verification passes, pause for manual
confirmation before Phase 5.

---

## Phase 5: Group the application boundary records under `application/dto` (+ trim names)

### Overview

Move the three application-boundary records from the `application` package root
into `application/dto`, so `application` holds just `CouponService` alongside its
`dto/` (use-case input/output) and `port/` (outbound contracts) sub-packages —
symmetric with `api/dto`. While moving, drop the redundant `*Command` suffix and
`Coupon` noun from the two inputs. Pure package + type rename; no behaviour or API
change (`CouponService` signatures change type names only).

### Changes Required:

#### 1. Move the three records

**Files**: `application/{CreateCouponCommand,RedeemCouponCommand,RedemptionResult}.java`
→ `application/dto/` (package `com.example.coupons.application.dto`).

**Intent**: Give the application-boundary DTOs their own home. Record bodies
unchanged.

**Contract**: New package `com.example.coupons.application.dto`. The `application`
root no longer contains these records.

#### 2. Retarget imports

**Files**: `application/CouponService.java` (add 3 imports — previously
same-package), `api/web/CouponController.java`,
`api/web/CouponRedemptionController.java`, `api/dto/RedemptionResponse.java`,
`src/test/java/com/example/coupons/RedemptionConcurrencyIT.java`,
`src/test/java/com/example/coupons/application/CouponServiceTest.java`.

**Intent**: Point each `com.example.coupons.application.{CreateCoupon…,RedeemCoupon…,
RedemptionResult}` import at `…application.dto.…` (renamed types per change #3).

**Contract**: `api.dto.RedemptionResponse` → `application.dto.RedemptionResult` is
an Inbound → Application edge (allowed). `ArchitectureTest` is unchanged —
`application/dto` is inside `..application..`.

#### 3. Trim the command names

**Files**: `application/dto/CreateCouponCommand.java` → `CreateCoupon.java`,
`application/dto/RedeemCouponCommand.java` → `RedeemCoupon.java` (+ same-name token
updates in `CouponService`, both controllers, `RedemptionConcurrencyIT`,
`CouponServiceTest`).

**Intent**: The `*Command` suffix and the `Coupon` noun are redundant now that the
package is `application.dto`. Drop both: `CreateCouponCommand` → `CreateCoupon`,
`RedeemCouponCommand` → `RedeemCoupon`. `RedemptionResult` is left as-is (only
`redeem()` returns it, so its name is already unambiguous).

**Contract**: `grep -rn "CreateCouponCommand\|RedeemCouponCommand" src` returns
nothing. `CouponService.create(CreateCoupon)` / `redeem(RedeemCoupon)`.

### Success Criteria:

#### Automated Verification:

- `JAVA_HOME=$(/usr/libexec/java_home -v 21) ./mvnw -B verify` passes
- `application/` root holds only `CouponService.java` + `dto/` + `port/`; `grep -rn
  "CreateCouponCommand\|RedeemCouponCommand" src` returns nothing
- `application/dto/` holds `CreateCoupon.java`, `RedeemCoupon.java`, `RedemptionResult.java`
- `ArchitectureTest` 2 rules green (unchanged)
- Test totals unchanged: 28 (23 Surefire + 5 Failsafe)

#### Manual Verification:

- None beyond the automated run — this is a compiler-checked package move with no
  runtime surface. (The Phase 4 boot walkthrough already covers the endpoints.)

**Implementation Note**: Final phase — one pass, no commit.

---

## Testing Strategy

No new tests. The existing suite is the equivalence proof for a refactor that
changes no behaviour.

### Unit Tests (Surefire, 22):

- `CouponServiceTest` (2) — insert-before-increment ordering; country check before
  any DB write. Port imports retargeted (Phase 1); package unchanged.
- `IpApiGeoIpResolverTest` (5) — resolved / provider-fail / cached / private-IP /
  circuit-open. Package moved to `infrastructure.geoip` (Phase 2); body unchanged.
- `CouponControllerTest` (4) — `@WebMvcTest` slice. Package moved to `api.web`
  (Phase 2); one added import.
- `domain/model/*Test` (`CouponCodeTest`, `CountryTest`, `UsageLimitTest`,
  `CouponTest`, `CouponRedemptionTest`) — untouched.
- `ArchitectureTest` (2 rules) — rewritten in Phase 2; must be green at every phase
  boundary (hexagonal rules through Phase 1, layered rules from Phase 2).

### Integration Tests (Failsafe, Testcontainers, 6):

- `CouponApiIT` (1), `CouponRedemptionApiIT` (1), `RedemptionConcurrencyIT` (2) —
  untouched; exercise the stack end to end.
- `CouponRedemptionCountryIT` (2) — one port import retargeted (Phase 1); stub
  resolver and assertions unchanged.

### Manual Testing Steps:

1. After each phase: `JAVA_HOME=$(/usr/libexec/java_home -v 21) ./mvnw -B verify` →
   BUILD SUCCESS.
2. After Phase 1 and Phase 2: boot on `dev`, run the create → get → redeem ladder
   (`README.md` "End-to-end walkthrough"), confirm every status code and
   `problem+json` `code` matches, confirm country restriction returns `200` /
   `403` / `422` for PL / non-PL / loopback IPs.
3. After Phase 2: `/actuator/health`, `/actuator/info`, `/swagger-ui.html` all
   respond; logs are JSON with `correlationId`.
4. After Phase 3: read `README.md` top to bottom against the running app.

## Performance Considerations

None. No runtime path, query, cache, or resilience setting changes.

## Migration Notes

No data or schema change. No API contract change — status codes, `problem+json`
shapes, and the generated OpenAPI document are identical before and after. Rollback
is `git checkout -- .` (nothing is committed by this plan) or `git revert` if the
user chooses to commit the phases.

## References

- Prior simplification (kept hexagonal deliberately):
  `context/changes/simplify-project/plan-brief.md`
- Original build: `context/changes/coupon-service/plan.md`
- Current `ArchitectureTest`: `src/test/java/com/example/coupons/architecture/ArchitectureTest.java`
- Transaction / ordering contract: `src/main/java/com/example/coupons/application/CouponService.java:79-123`

## Progress

> Convention: `- [ ]` pending, `- [x]` done. Append ` — <commit sha>` when a step
> lands (this plan does not commit; leave the sha off unless the user commits).
> Do not rename step titles. See `references/progress-format.md`.

### Phase 1: Move the ports into `application`; inline the row mapper

#### Automated

- [x] 1.1 `./mvnw -B verify` passes (JDK 21)
- [x] 1.2 `grep -rn "domain.port\|CouponRowMapper" src` returns nothing
- [x] 1.3 `ArchitectureTest` (hexagonal rules) still passes
- [x] 1.4 All 28 tests run and pass (22 Surefire + 6 Failsafe)

#### Manual

- [ ] 1.5 App boots on the `dev` profile against the compose DB
- [ ] 1.6 `POST /api/v1/coupons` → 201; `GET /api/v1/coupons/{code}` → 200; `POST .../redemptions` → 200

### Phase 2: Rename packages to `api/*` + `infrastructure/*`; rewrite `ArchitectureTest`

#### Automated

- [x] 2.1 `./mvnw -B verify` passes (JDK 21)
- [x] 2.2 `grep -rn "com.example.coupons.adapter\|com.example.coupons.config" src` returns nothing
- [x] 2.3 `CouponsApplication` javadoc contains no "hexagonal" / "adapter"
- [x] 2.4 `ArchitectureTest` runs its 2 rewritten (4-layer) rules, both green
- [x] 2.5 Test totals unchanged: Surefire 22, Failsafe 6, all passing

#### Manual

- [ ] 2.6 App boots on `dev`; `/actuator/health` UP with `db`; `/actuator/info` shows build info; `/swagger-ui.html` loads
- [ ] 2.7 Redemption ladder returns 201 / 200 / 200 / 409 ALREADY_REDEEMED / 409 USAGE_LIMIT_REACHED / 404 COUPON_NOT_FOUND / 400 VALIDATION_ERROR
- [ ] 2.8 Country-restricted coupon: PL IP → 200, non-PL IP → 403 COUNTRY_NOT_ALLOWED, 127.0.0.1 → 422 COUNTRY_NOT_DETERMINED
- [ ] 2.9 Logs are JSON with a `correlationId`; responses echo `X-Correlation-Id`

### Phase 3: README architecture pass

#### Automated

- [x] 3.1 `grep -in "hexagon\|ports & adapters" README.md` returns nothing
- [x] 3.2 `./mvnw -B verify` still passes (final guard; no code touched)

#### Manual

- [ ] 3.3 `README.md` diagram matches the actual package tree; quick-start and API sections work verbatim against a running app

### Phase 4: Move inbound HTTP glue to `infrastructure/web`

#### Automated

- [x] 4.1 `./mvnw -B verify` passes (JDK 21)
- [x] 4.2 `grep -rn "com.example.coupons.api.support" src` returns nothing; `find src/main -path '*api/support*'` is empty
- [x] 4.3 `ArchitectureTest` runs its 2 rules (rule 2 = inbound / sealed-outbound carve-out), both green
- [x] 4.4 Test totals unchanged: 28 (23 Surefire + 5 Failsafe), all passing

#### Manual

- [ ] 4.5 App boots on `dev`; `/actuator/health` UP with `db`; `/swagger-ui.html` loads
- [ ] 4.6 `GET /v3/api-docs` byte-identical (tags, `@Operation` summaries, `@ApiResponse` descriptions)
- [ ] 4.7 Redemption ladder + country rule unchanged: 201 / 200 / 200 / 409 / 409 / 404 / 400, PL → 200, non-PL → 403, loopback → 422
- [ ] 4.8 Logs JSON with `correlationId`; response echoes `X-Correlation-Id` (filter active from `infrastructure/web`)

### Phase 5: Group the application boundary records under `application/dto`

#### Automated

- [x] 5.1 `./mvnw -B verify` passes (JDK 21)
- [x] 5.2 `application/` root holds only `CouponService.java` + `dto/` + `port/`; `application/dto/` = `CreateCoupon.java`, `RedeemCoupon.java`, `RedemptionResult.java`; `grep -rn "CreateCouponCommand\|RedeemCouponCommand" src` returns nothing
- [x] 5.3 `ArchitectureTest` 2 rules green (unchanged)
- [x] 5.4 Test totals unchanged: 28 (23 Surefire + 5 Failsafe)
