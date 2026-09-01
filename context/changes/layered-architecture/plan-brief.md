# Restructure to a Pragmatic 4-Layer Architecture — Plan Brief

> Full plan: `context/changes/layered-architecture/plan.md`

## What & Why

Move `com.example.coupons` from its hexagonal (ports & adapters) layout to a
pragmatic layered architecture — `api` → `application` → `domain`, with
`infrastructure` implementing the port interfaces Application owns. The directive is
to keep it simple: no unnecessary interfaces, mappers, or layers. This is the
follow-on to `simplify-project`, which deliberately kept the `adapter/*` split out
of scope.

## Starting Point

Post-`simplify-project` hexagonal codebase: one `CouponService`, three ports in
`domain.port`, `adapter.{web,persistence,geoip}`, `config.GeoIpProperties`, an
ArchUnit `ArchitectureTest` enforcing hexagonal layering, and 28 green tests (22
unit + 6 Testcontainers ITs). Java 21 / Spring Boot 3.3. No frame or research doc
preceded this plan.

## Desired End State

Four packages under `com.example.coupons`: `api/{web,dto}`,
`application/{dto,port}` (root holds only `CouponService`), `domain/{model,exception}`,
`infrastructure/{web,persistence,geoip}` — the inbound HTTP glue (exception
handler, correlation-id filter, client-IP resolver) sits in `infrastructure/web`
(Phase 4). The standalone `CouponRowMapper` is gone
(folded into `CouponJdbcRepository`). `ArchitectureTest` enforces the layered model
(inbound = `api` + `infrastructure.web`; sealed outbound = `persistence` + `geoip`).
No behaviour, API-contract, HTTP-status, `problem+json`, OpenAPI, or schema change —
the 28-test suite stays green and is the equivalence proof. `README.md` describes
the layered model. Delivered as working-tree changes; no commits.

## Key Decisions Made

| Decision | Choice | Why | Source |
| --- | --- | --- | --- |
| Port interfaces — where | Move `domain.port` → `application.port` | Directive: "Infrastructure implements ports required by Application"; keeps `CouponService` unit-testable with plain fakes | Plan |
| Port interfaces — keep at all | Keep all three interfaces | Preserves "business logic independent of external systems"; dropping them forces Spring/Mockito for service tests | Plan |
| Redemption repository | Keep `CouponRedemptionRepository` separate from `CouponRepository` | 1:1 with the two tables and two distinct unique-violation translations | Plan |
| Top-level package names | `api` / `application` / `domain` / `infrastructure` | Verbatim from the directive; unambiguous mapping from `adapter.*` | Plan |
| Persistence stack | Inline `CouponRowMapper` into `CouponJdbcRepository`; keep `*CrudRepository` / `*JdbcRepository` / `*Row` | Removes the "unnecessary mapper" file the directive calls out; keeps storage-shape isolation | Plan |
| Application command/result records | Keep all three; moved to `application/dto` (Phase 5) | Keep `CouponService` decoupled from web DTOs and the aggregate; `RedemptionResult` is a real projection; `application/` root left clean | Plan |
| Spring in `application` | Keep `@Service` + programmatic `TransactionTemplate` | Directive scopes "no framework concerns" to `domain` only; the template keeps the geo-IP call outside the transaction | Plan |
| Domain sub-packages | Keep `domain/model` + `domain/exception` | Organizational grouping, not layers; avoids pure churn in 5 test files | Plan |
| `api` internal layout | `api/web` + `api/dto`; inbound HTTP glue moved to `infrastructure/web` (Phase 4) | `api` holds only controllers + DTOs; framework glue is infrastructure | Plan |
| ArchUnit after Phase 4 | Carve-out: inbound = `api` + `infrastructure.web`; sealed = `persistence` + `geoip` | Keeps controllers away from persistence/geoip while letting the web glue live under `infrastructure` | Plan |
| OpenAPI annotations | Moved to `CouponApi` / `CouponRedemptionApi` interfaces | Leaner controllers — routing + delegation only | Plan |
| `infrastructure` internal layout | `infrastructure/persistence` + `infrastructure/geoip`; `GeoIpProperties` moves into `geoip` | Two cohesive sub-packages, one per external system; config lives with its client | Plan |
| `ArchitectureTest` | Full 4-layer enforcement + domain-purity rule | Preserves the automated architecture guardrail the recruitment task values | Plan |
| Test suite | Mirror packages, fix imports, add nothing | Honours the lean-suite bar; suite is the refactor's equivalence check | Plan |
| Documentation | `README.md` only (architecture section + diagram) | User choice; `README2.md` left as-is | Plan |
| Commits | None — working-tree delivery | User choice | Plan |

## Scope

**In scope:** package moves for every main + test class; `domain.port` →
`application.port`; `CouponRowMapper` inlined and deleted; `GeoIpProperties` →
`infrastructure.geoip`; `ClientIpResolver` switched to a `@Value`-bound flag;
`ArchitectureTest` rewritten for 4 layers; `CouponsApplication` javadoc; `README.md`
architecture section + ASCII diagram.

**Out of scope:** any behaviour / API / status / `problem+json` / OpenAPI / schema
change; `pom.xml` dependencies; merging the two repositories; flattening the domain
sub-packages; a framework-free `application`; dropping the command/result records;
collapsing the `*Crud`/`*Jdbc`/`*Row` split; `IpApiGeoIpResolver` resilience/cache
logic; `README2.md`, `logback-spring.xml`, Docker, Liquibase; adding/removing tests;
committing.

## Architecture / Approach

`api` (controllers + DTOs) depends on `application`; `application`
(`CouponService` + command/result records + `port` interfaces) depends on `domain`;
`domain` (framework-free aggregate + value objects + exceptions) depends on nothing.
`infrastructure` splits into `web` (inbound HTTP glue — problem+json advice,
correlation-id filter, client-IP resolver) and `persistence` + `geoip` (outbound
adapters implementing the `application.port` interfaces). ArchUnit treats `api` +
`infrastructure.web` as one inbound group and seals `persistence` + `geoip` so no
layer can reach them except through the ports. `CouponsApplication` is the
composition root, outside every layer. Package moves are compiler-checked; each
phase is gated on `./mvnw verify`.

## Phases at a Glance

| Phase | What it delivers | Key risk |
| --- | --- | --- |
| 1. Ports + mapper | 3 ports in `application.port`; `CouponRowMapper` inlined & deleted; imports retargeted | A missed import — caught by the compiler, not a test |
| 2. Rename + ArchUnit | `api/*` + `infrastructure/*` packages; `GeoIpProperties` moved; `ClientIpResolver` on `@Value`; test packages mirrored; `ArchitectureTest` rewritten; `CouponsApplication` javadoc | The `api → infrastructure` edge via `GeoIpProperties` must be removed in the same phase or the new ArchUnit rule fails |
| 3. README | `README.md` architecture section + ASCII diagram describe the layered model | Diagram drifting from the actual package tree |
| 4. `infrastructure/web` | `api/support` trio → `infrastructure/web`; ArchUnit reworked into inbound / sealed-outbound carve-out; README + javadoc + plan text updated | Moving `ClientIpResolver` adds an `api → infrastructure.web` edge — the ArchUnit rework must land in the same pass or `verify` fails |
| 5. `application/dto` | 3 boundary records → `application/dto`; commands trimmed to `CreateCoupon` / `RedeemCoupon` (`RedemptionResult` kept); `application/` root = `CouponService` only | Trivial — compiler-checked move + rename, no runtime surface |

**Prerequisites:** JDK 21 (`JAVA_HOME=$(/usr/libexec/java_home -v 21)`), Docker for
the integration tests.
**Estimated effort:** ~1 session. Phase 2 is the bulk; Phase 4 is a 3-file move
plus the ArchUnit rework, verified by `./mvnw verify` and a boot + `/v3/api-docs`
diff.

## Open Risks & Assumptions

- **`README2.md` is left describing "single-module hexagonal (ports & adapters)"**
  per the doc-scope decision. The two READMEs will disagree on the architecture
  phrasing until someone revisits `README2.md`.
- `ClientIpResolver` moving from a `GeoIpProperties` injection to
  `@Value("${geoip.allow-ip-override:false}")` is assumed behaviour-neutral: same
  key, same `false` default, same three profile YAMLs. Covered indirectly by
  `CouponRedemptionCountryIT` (runs under `application-test.yml` with the flag on).
- Assumes an IDE move-refactor is available; a purely manual move raises the
  missed-reference risk in Phase 2 (mitigated by the `grep` checks in the success
  criteria).
- Assumes ArchUnit `layeredArchitecture().consideringOnlyDependenciesInLayers()`
  ignores `CouponsApplication` (root package, no layer) — intended, since it is the
  composition root and legitimately wires infrastructure beans.

## Success Criteria (Summary)

- `./mvnw verify` (JDK 21) green after every phase; test totals unchanged at 22
  unit + 6 IT.
- `grep -rn "com.example.coupons.adapter\|com.example.coupons.config\|domain.port" src`
  returns nothing; `CouponRowMapper` is gone.
- `ArchitectureTest` enforces `api`/`application`/`domain`/`infrastructure` layering
  and domain purity, green.
- Every documented API outcome (`201` / `200` / `404` / `409` / `403` / `422`, all
  `problem+json`) is unchanged, verified by the untouched ITs and a manual `curl`
  ladder; the app still boots on `dev` with health, info, and Swagger UI working.
