# Migrate persistence to JPA / Hibernate + rename persistence types — Plan Brief

> Full plan: `context/changes/persistence-jpa-migration/plan.md`

## What & Why

Replace **Spring Data JDBC** with **Spring Data JPA / Hibernate** in
`infrastructure/persistence`, and in the same pass rename the seven persistence types into
clear role names with the two data holders moved to a `persistence/entity/` subpackage.
Driven by a preference to run the persistence layer on Hibernate and to drop the noisy
`Row` / `Crud` / `Jdbc` naming.

## Starting Point

`infrastructure/persistence` is 7 flat package-private types on Spring Data JDBC:
two `@Table` records (`CouponRow`, `CouponRedemptionRow`), two `CrudRepository`
interfaces (one carrying a hand-written atomic
`UPDATE coupon SET current_uses = current_uses + 1 WHERE id = :id AND current_uses < max_uses`),
two `@Repository` port adapters that map row↔domain and translate unique-constraint
violations, and `PersistenceExceptions`. The `domain` aggregates are framework-free
records; `application` owns the port interfaces; `CouponService` owns the only transaction
(a `TransactionTemplate` at READ_COMMITTED). Schema is Liquibase-owned. Grep confirms
nothing outside the persistence package references any of the 7 types.

## Desired End State

The persistence package runs on Hibernate via Spring Data JPA: `entity/CouponEntity` +
`entity/CouponRedemptionEntity` (`@Entity`, Lombok accessors), `CouponJpaRepository` +
`CouponRedemptionJpaRepository` (`JpaRepository`), `CouponPersistenceAdapter` +
`CouponRedemptionPersistenceAdapter` (port adapters), `UniqueViolations`. The usage-cap
guarantee is unchanged — still one atomic conditional bulk `UPDATE`. `./mvnw clean verify`
is green with `CouponServiceTest` unmodified and all five Testcontainers ITs — including
`RedemptionConcurrencyIT` — passing. Domain, ports, `CouponService`, the REST layer, and
the Liquibase schema are untouched.

## Key Decisions Made

| Decision | Choice | Why (1 sentence) | Source |
| --- | --- | --- | --- |
| Migration mode | Full replace of the starter | One persistence model; running JDBC + JPA together needs repository-scan disambiguation and double auto-config for no gain on 2 tables | Plan |
| Atomic increment | Keep the conditional `UPDATE`, as a `@Modifying` JPQL bulk update | Preserves the exact "first-wins, no retry" semantics the assignment is built around; IT passes unchanged | Plan |
| L1-cache coherence | `@Modifying(clearAutomatically = true, flushAutomatically = true)` | `CouponService:105` re-reads the coupon in-transaction; a bulk update bypasses the context, so without clear the read-back is stale | Plan |
| Unique-violation timing | Both adapters use `saveAndFlush` | `CouponServiceTest` and the `*ApiIT` 409 cases need the constraint to fire inside the adapter's try/catch, not at commit | Plan |
| Schema management | `ddl-auto: validate` | Liquibase still builds the schema; Hibernate cheaply catches mapping drift at startup | Plan |
| OSIV | `open-in-view: false` | Controllers return DTOs mapped inside the service transaction — no reason to hold a persistence context per request | Plan |
| Entity shape | Lombok `@Getter/@Setter/@NoArgsConstructor(PROTECTED)/@AllArgsConstructor`; **no `@Data`** | Least boilerplate; `@Data`'s generated `equals`/`hashCode` over a mutable `@GeneratedValue` id is a JPA footgun | Plan |
| Naming | `*Entity` / `*JpaRepository` / `*PersistenceAdapter` / `UniqueViolations`; entities in `persistence/entity/` | Each suffix names a distinct role; "JPA" is now accurate; ArchUnit already seals the subpackage | Plan |
| Test surface | No new slices — rely on existing ITs | The ITs already prove behaviour end-to-end against real Postgres; matches the lean-testing bar | Plan |

## Scope

**In scope:**
- `pom.xml`: `spring-boot-starter-data-jdbc` → `-data-jpa`; add Lombok (`optional`)
- `application.yml`: `spring.jpa.hibernate.ddl-auto: validate`, `spring.jpa.open-in-view: false`
- Rewrite + rename all 7 persistence types; add `persistence/entity/` subpackage
- Broaden `UniqueViolations` to cover Hibernate / Spring-DAO exception shapes
- README + code-comment sweep (Spring Data JDBC → JPA / Hibernate)

**Out of scope:**
- `domain`, `application` (ports, `CouponService`, DTOs), `api`, `infrastructure/{web,geoip}`
- Port signatures; transaction boundary / isolation level
- Liquibase schema — no new columns, no `@Version`, no `hbm2ddl` generation
- `@Version` optimistic or pessimistic locking
- New test classes / `@DataJpaTest` slices
- Running Spring Data JDBC and JPA side by side

## Architecture / Approach

Two phases. **Phase 1** is the whole migration — build, config, and all seven persistence
types with their final names — because a persistence-adapter swap can't be half-applied
while compiling; `./mvnw clean verify` (unit tests + all Testcontainers ITs + ArchUnit +
Hibernate `validate`) is the correctness proof. **Phase 2** is the README and comment
sweep plus an ArchUnit re-confirm. Data flow is unchanged: `SELECT by code` → flushed
`INSERT` → conditional `UPDATE` → `SELECT by code`, all inside the existing
`TransactionTemplate`.

## Phases at a Glance

| Phase | What it delivers | Key risk |
| --- | --- | --- |
| 1. JPA / Hibernate migration | Persistence package on Hibernate with final names; full suite green | Stale L1 cache on the in-transaction read-back; unique violations surfacing at commit instead of in-adapter — both mitigated by `clearAutomatically`/`flushAutomatically` + `saveAndFlush` |
| 2. Docs & naming sweep | README + comments say JPA / Hibernate; ArchUnit re-confirmed | Missing a stale "Jdbc" reference — covered by a grep check |

**Prerequisites:** Docker (Testcontainers ITs), JDK 21.
**Estimated effort:** ~1 session — one focused implementation pass plus the verify run.

## Open Risks & Assumptions

- Spring Boot's snake_case physical naming strategy maps every entity field to its column
  with no `@Column` overrides — `ddl-auto: validate` confirms this on the first IT run; if
  a field mismatches, add an explicit `@Column(name = ...)`.
- Hibernate's translated `DataIntegrityViolationException` carries a cause chain reaching
  either a SQLSTATE `23505` `SQLException` or `org.hibernate.exception.ConstraintViolationException`;
  `UniqueViolations` is written to match both, but the exact chain is verified by the
  `*ApiIT` 409 cases.
- `GenerationType.IDENTITY` forces an immediate INSERT on `persist`, so `saveAndFlush` is
  belt-and-braces rather than the sole safeguard — kept for an explicit contract.

## Success Criteria (Summary)

- `./mvnw clean verify` green: `CouponServiceTest` unmodified, all 5 ITs pass, ArchUnit green.
- `RedemptionConcurrencyIT`: exactly 20/100 racers succeed, `current_uses == 20`, 20 redemption rows — no drift.
- README end-to-end walkthrough steps 1–8 return the documented status codes against the running app.
