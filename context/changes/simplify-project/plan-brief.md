# Simplify the coupon-service — Plan Brief

> Full plan: `context/changes/simplify-project/plan.md`

## What & Why

The coupon-service is functionally complete and well-reviewed, but it carries 51 main Java
files / ~1,659 lines for a 3-endpoint service. This change strips genuine bloat — doc-only
files, a coverage-gate build apparatus, a bespoke value type, three one-method services —
while keeping every design choice the recruitment task explicitly grades.

## Starting Point

Hexagonal architecture, Java 21 / Spring Boot 3.3, 23 unit + 6 integration tests, all green.
Three `@Service` classes (two are one-method wrappers), a `CountryResolution` domain record
used only for a log string, a `jacoco-maven-plugin` with a merged-exec 80% coverage gate, 5
`package-info.java` files, `scripts/smoke.sh`, and a CI workflow.

## Desired End State

One `CouponService` (`create` / `getByCode` / `redeem`). `GeoIpResolver.resolve` returns
`Optional<Country>`. No JaCoCo plugin, no `package-info.java`, no `smoke.sh`, no CI workflow,
no custom `resilience4j.*` config, no `ApplicationConfig` class. Domain purity, ports,
hexagonal packages, observability, springdoc, and the Docker/Testcontainers setup are all
untouched. `./mvnw verify` green after each phase; API contract byte-identical.

## Key Decisions Made

| Decision | Choice | Why | Source |
| --- | --- | --- | --- |
| North star | Leaner but production-credible | Task grades architecture and says avoid simplified implementations | Plan |
| Hexagonal structure | Keep pure domain + ports; merge/flatten inside layers | Protects the one signal the task names | Plan |
| Persistence Row + RowMapper split | Keep as-is | Clean, unit-testable; not the bloat | Plan |
| 3 application services | Merge into one `CouponService` | Two are one-method wrappers; −2 files, layer intact | Plan |
| Application command/result records | Keep | Keep the service signature readable | Plan |
| `CountryResolution` type | Replace with `Optional<Country>` | JDK type fits; the "reason" was log-only; −1 file | Plan |
| Geo-IP resilience | Keep circuit breaker + cache; drop bounded retry; drop all `resilience4j.*` YAML | User chose defaults; retry+config were low value | Plan |
| Observability (correlation id, JSON logs, build-info, probes) | Keep all | Cheap, real ops signal | Plan |
| `ClientIpResolver` + dev IP override | Keep | Lets a reviewer exercise the country rule locally | Plan |
| OpenAPI / springdoc + annotations | Keep | Strong first impression; documents error codes | Plan |
| Build machinery | Drop JaCoCo entirely; keep Surefire/Failsafe split, `build-info`, TC/docker pins | Coverage gate config was the fiddliest part | Plan |
| Infra files | Drop `smoke.sh` + CI workflow; keep Docker, compose, wrapper | Fewer non-code files; clone-and-run still works | Plan |
| Micro-clutter | Delete 5 `package-info.java`; fold `Clock` bean into main class; keep `PersistenceExceptions` | −6 files, no logic lost | Plan |
| 7 domain exception classes | Keep separate | Idiomatic; 1:1 with error codes | Plan |
| Package depth (`adapter/web/dto`, `adapter/geoip`) | Keep | The package tree is the architecture diagram | Plan |

## Scope

**In scope:** merge 3 services → 1; `CountryResolution` → `Optional<Country>`; remove JaCoCo
plugin + gate; remove `resilience4j.*` YAML + the retry wrapper; delete `package-info.java`
×5, `ApplicationConfig`, `smoke.sh`, `ci.yml`; fold `Clock` bean into `CouponsApplication`;
README pass.

**Out of scope:** collapsing hexagonal to layered; touching the persistence `Row`/`RowMapper`
split; removing observability, springdoc, `ClientIpResolver`, `PersistenceExceptions`, the
Maven wrapper, Docker files, or the application-layer records; editing the `coupon-service`
change's plan/review.

## Architecture / Approach

Subtraction in three risk-ordered phases: (1) config + dead-weight, zero behaviour change;
(2) a mechanical service merge with call-site rewiring; (3) one port return-type change
(`Optional<Country>`) plus dropping the retry layer. Each phase ends green on `./mvnw verify`
and leaves the app bootable with all three endpoints working.

## Phases at a Glance

| Phase | What it delivers | Key risk |
| --- | --- | --- |
| 1. Config & dead-weight | −8 files, no JaCoCo, no `resilience4j.*` YAML, `Clock` bean moved | A deleted file was referenced somewhere unexpected |
| 2. Merge services | One `CouponService`; controllers + 1 test rewired | Transaction boundary / insert-first ordering must move verbatim |
| 3. `Optional<Country>` | Simpler geo-IP port; retry wrapper gone | Fail-closed semantics must stay identical; 2 tests + 1 IT stub adjust |

**Prerequisites:** JDK 21 (`JAVA_HOME=$(/usr/libexec/java_home -v 21)`), Docker for the ITs.
**Estimated effort:** ~1 session, ~2-3 hours across the three phases.

## Open Risks & Assumptions

- With `resilience4j.*` config removed, the circuit breaker runs on defaults
  (`minimumNumberOfCalls` 100) and effectively never opens under low traffic. The per-call
  fail-closed behaviour and the cache carry the resilience story; the circuit-open branch is
  defensive-only. `IpApiGeoIpResolverTest` still proves that branch with its own low-threshold
  registry.
- Dropping `retry.ignore-exceptions` is moot because the retry code itself is removed in
  Phase 3.
- Assumes the `coupon-service` plan's CI rows (1.4 / 5.3) are already accepted as
  un-tickable; this change does not revisit them.

## Success Criteria (Summary)

- `./mvnw verify` green after every phase; `ArchitectureTest` green throughout.
- ~12 net files removed; `CouponService` is the only application service;
  `grep -rn CountryResolution src` is empty.
- Every documented API outcome (201 / 200 / 404 / 409 / 403 / 422, all `problem+json`) is
  unchanged, verified by `curl` and the untouched integration tests.
