# Coupon Discount Service — Plan Brief

> Full plan: `context/changes/coupon-service/plan.md`
> Task source: `context/foundation/task.md`

## What & Why

A REST service that manages discount coupons: **create a coupon** (no auth), **redeem a
coupon on behalf of a user**, and **look one up by code**. The assignment is graded on
architecture, design, code quality, tests, and production-readiness — not on doing the
minimum — and it explicitly calls out correctness in a **multithreaded production
environment**. So the build treats the max-uses cap and the one-redemption-per-user rule as
**database-enforced invariants**, not application checks that can race.

## Starting Point

Greenfield. The repo has only the assignment (`context/foundation/task.md`) and the
`context/` scaffold — no build file, no code, no CI. Every choice below is a fresh decision.

## Desired End State

A service that boots against PostgreSQL with three endpoints under `/api/v1/coupons`.
Redemption returns a **distinct, documented outcome** for every case (not found, usage limit
reached, wrong country, already redeemed, country undetermined), each as RFC 7807
`application/problem+json` with a stable `code`. Firing hundreds of parallel redemptions at a
coupon with `max_uses = N` yields **exactly `N`** successes — proven by a dedicated
concurrency test in CI. OpenAPI/Swagger, Actuator health probes, structured logs, and a
README that lets a reviewer reproduce every response.

## Key Decisions Made

| Decision | Choice | Why (1 sentence) | Source |
| --- | --- | --- | --- |
| Language / build / framework | Java 21, Maven, Spring Boot 3 | Widest reviewer familiarity and the richest production-shaped ecosystem. | Plan |
| Database + migrations | PostgreSQL + Liquibase | Production-representative locking behaviour; changelog-based migrations. | Plan |
| Persistence layer | Spring Data JDBC | No ORM cache / dirty-checking to obscure the concurrency behaviour under test. | Plan |
| Max-uses cap | Atomic `UPDATE … WHERE current_uses < max_uses` + `CHECK` constraint | One round-trip, no lost updates, no app locking, scales horizontally. | Plan |
| One-per-user | `UNIQUE (coupon_id, user_id)` on a redemption table + counter on the coupon | Makes "already redeemed" a DB invariant; also gives an audit trail. | Plan |
| Transaction scope | Short tx at `READ_COMMITTED`; geo-IP resolved **before** the tx opens | Never hold a pooled DB connection across a slow external call. | Plan |
| Geo-IP implementation | Remote free API (`ip-api.com`) behind a `GeoIpResolver` port | No dataset to bundle/refresh; port keeps it swappable and testable. | Plan |
| Geo-IP resilience | Timeouts + Resilience4j circuit breaker + bounded retry + short-TTL Caffeine cache | Protects the redemption hot path and stays under the free rate ceiling. | Plan |
| Geo-IP failure mode | **Fail closed** → distinct `COUNTRY_NOT_DETERMINED` (422); unrestricted coupons skip geo-IP | Safe default for an access-restricting rule; still a clear response. | Plan |
| Client IP | Trusted-proxy `X-Forwarded-For` via `forward-headers-strategy`, dev-only override flag | Correct and non-spoofable behind a load balancer, still testable locally. | Plan |
| API error contract | RFC 7807 Problem Details + per-case HTTP status + documented code catalogue | Standard, first-class Spring support, self-documenting outcomes. | Plan |
| Architecture | Single-module hexagonal (ports & adapters), ArchUnit-enforced | Framework-free domain and clear seams without multi-module overhead. | Plan |
| Testing | Domain unit + `@WebMvcTest` slices + Testcontainers integration + explicit concurrency test + WireMock geo-IP + JaCoCo gate | Proves the graded behaviour (the cap under threads, the contract). | Plan |

## Scope

**In scope:** create coupon, redeem coupon (with per-user single-use — the optional
requirement), get coupon by code; case-insensitive unique codes; DB-enforced max-uses cap;
IP-based country restriction with fail-closed geo-IP; RFC 7807 errors; OpenAPI; Docker
Compose + image; CI; concurrency test; README with design rationale and error catalogue.

**Out of scope:** authentication/authorization; admin UI / frontend; message i18n; listing /
search / update / delete endpoints; coupon validity window / expiry; Redis / message broker /
multi-region infra; mutation testing and load-testing harnesses.

## Architecture / Approach

Hexagonal, one Maven module. **`domain`** (pure Java: `Coupon` aggregate, value objects,
redemption policy, ports) ← **`application`** (use-case services, the only transaction
boundary) ← **`adapter`** (`web` controllers + `@RestControllerAdvice` → `ProblemDetail`;
`persistence` Spring Data JDBC + the atomic `UPDATE`; `geoip` resilience-wrapped HTTP client).

Redemption flow: normalize code → load coupon (`404`) → if restricted, resolve caller country
off-transaction (`403` mismatch / `422` undetermined, fail closed) → one short transaction:
insert redemption row (unique violation → `409 ALREADY_REDEEMED`) then atomic counter
`UPDATE` (0 rows → `409 USAGE_LIMIT_REACHED`) → commit → `200`. Insert-first ordering means a
repeat user always gets the precise "already redeemed" message.

## Phases at a Glance

| Phase | What it delivers | Key risk |
| --- | --- | --- |
| 1. Project skeleton & infrastructure | Buildable/bootable Spring Boot app, hexagonal layout, PostgreSQL + Liquibase, Docker Compose + image, Actuator, CI, ArchUnit scaffold | Testcontainers/Docker setup friction in CI |
| 2. Domain model & coupon creation | `Coupon` aggregate + value objects, create + get-by-code endpoints, `coupon` migration, RFC 7807 handler | Getting case-insensitive uniqueness canonicalization exactly right |
| 3. Redemption — core rules & concurrency | Redeem endpoint, `coupon_redemption` table, atomic cap `UPDATE`, **explicit N-thread concurrency test** | The concurrency test is the make-or-break proof; unique-violation → outcome translation |
| 4. Geo-IP country restriction | Remote resolver with circuit breaker + cache, trusted-proxy IP resolution, country check wired in fail-closed | Flaky free provider; correct client-IP resolution behind a proxy |
| 5. Hardening, docs & observability | README with design rationale + error catalogue, JSON logging + correlation id, readiness/liveness, enforced coverage + ArchUnit gates, smoke script | Time sink polishing docs; keeping the error catalogue in sync |

**Prerequisites:** Docker (for local Postgres and Testcontainers); JDK 21; a public git repo
to push to. No paid services, no API keys (`ip-api.com` free tier).
**Estimated effort:** ~4–6 focused sessions across the 5 phases.

## Open Risks & Assumptions

- **Free geo-IP provider reliability.** `ip-api.com` is rate-limited (~45 req/min) and can be
  slow or down. Mitigated by the cache + circuit breaker + fail-closed behaviour; a reviewer
  testing heavily may still see `422`s. An offline MaxMind GeoLite2 DB is the noted upgrade.
- **Client-IP resolution depends on deployment.** Correct behind a load balancer only when
  the trusted-proxy config matches the environment; documented in the README. Local testing
  uses the dev-only override flag.
- **`READ_COMMITTED` + atomic `UPDATE` is sufficient** for the cap; no `SERIALIZABLE` and no
  app-level lock. The concurrency test is the guardrail for this assumption.
- **`userId` is any opaque string** (per the task) — no validation beyond non-blank, no join
  to a user table.
- Assumes GitHub Actions as the CI host (Docker available on hosted runners).

## Success Criteria (Summary)

- Every redemption outcome returns its own documented status + `problem+json` `code`; a
  reviewer reproduces them all from the README.
- `max_uses = N` under `K > N` concurrent distinct-user redemptions → exactly `N` succeed,
  `current_uses == N`, `N` redemption rows — asserted by `RedemptionConcurrencyIT` in CI.
- A user cannot redeem the same coupon twice; a caller outside the coupon's country cannot
  redeem it; an undeterminable country fails closed.
- `mvn verify` is green (all test tiers + coverage gate + architecture rules); the service
  runs from a clean clone via Docker Compose.
