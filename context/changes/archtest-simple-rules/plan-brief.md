# Rewrite ArchitectureTest as Small, Plain-English Rules — Plan Brief

> Full plan: `context/changes/archtest-simple-rules/plan.md`

## What & Why

`ArchitectureTest.java` guards the layering but is hard to read: one 8-entry package
denylist and one `layeredArchitecture()` chain with four `whereLayer(...)` clauses and
no failure message. This change rewrites it as four small `noClasses()` / `classes()`
rules, each a single sentence whose name reads as the guarantee and whose
`.because(...)` explains why it exists — plus an ASCII layer diagram for the class
doc. Goal: a reader understands the architecture and every rule at a glance, and a
failure teaches rather than just reporting.

## Starting Point

ArchUnit 1.3.0. Two `@ArchTest` fields: `the_domain_is_pure` (denylist of `..api..`,
`..application..`, `..infrastructure..`, Spring, JPA, Jackson, Caffeine, resilience4j)
and `respects_layering` (a `layeredArchitecture().consideringOnlyDependenciesInLayers()`
chain: Domain / Application / Inbound = `api` + `infrastructure.web` / Infrastructure =
`persistence` + `geoip`). Intent lives in a dense `<ul>` javadoc. 41 production classes
across the four package groups.

## Desired End State

The file has a 4-ring ASCII diagram for a class doc and four `@ArchTest` rules:
`domain_depends_only_on_java_and_itself`, `the_http_edge_is_used_by_nobody`,
`outbound_adapters_are_reached_only_through_ports`,
`application_never_depends_on_the_edges_or_adapters` — each one fluent expression with
a `.because(...)`. No `layeredArchitecture()` anywhere. Same dependencies rejected as
before; `./mvnw verify` green with zero production changes.

## Key Decisions Made

| Decision | Choice | Why | Source |
| --- | --- | --- | --- |
| Rule granularity | Split into ~4 small named rules; drop `layeredArchitecture()` | Each rule name + message explains one idea; a failure points at the exact broken relationship | Plan |
| Coverage | Readability only — reject exactly what the current rules reject | Tightly scoped, verifiable by deliberate-violation probes | Plan |
| Domain-purity form | Allowlist: `..domain..` may depend only on `..domain..` + `java..` | One positive sentence; auto-catches any future foreign dependency, not just listed ones | Plan |
| Allowlist is safe today | Verified | Domain packages import only `java.time`, `java.util`, `com.example.coupons.domain.*` — no Lombok, no annotations | Plan |
| Wording | Plain-English `snake_case` names + `.because(...)` + ASCII diagram | Name = the guarantee; failure explains the rationale; diagram gives the whole picture in seconds | Plan |
| Rule 4 overlap | Keep an explicit `application`-inward rule even though rules 2–3 already cover it | A targeted failure message ("use-case code must not know adapters") beats a derived one | Plan |
| `should_` naming / `@DisplayName` | Not applied | `@ArchTest` fields are static `ArchRule`s, not behavioural tests; noun-phrase names are the ArchUnit idiom | Plan |
| Known coverage gaps | Deferred | `application`-imports-JPA and `@Entity`-escapes-`persistence` are a separate change | Plan |

## Scope

**In scope:** rewrite `src/test/java/com/example/coupons/architecture/ArchitectureTest.java`
— class javadoc → ASCII diagram; two dense rules → four single-sentence rules; drop
the `layeredArchitecture` import. Keep `@AnalyzeClasses` as-is.

**Out of scope:** production code, `pom.xml`, other test files, new coverage
(persistence-agnostic `application`, entity confinement), ArchUnit library rules, file
/ class rename.

## Architecture / Approach

The four current `whereLayer(...)` constraints plus the denylist collapse to four
"leaf / sealed / allowlist" statements: (1) domain depends only on `java..` + itself —
which also covers `domain → application`; (2) nothing outside `api` +
`infrastructure.web` depends on them; (3) nothing outside `persistence` + `geoip`
depends on them; (4) `application` depends on neither `api` nor `infrastructure.*`
(explicit, for the message). `application`'s outward purity falls out of (2)+(3). The
explicit `resideOutsideOfPackages(...)` form is marginally stronger than the old chain
— it does not exempt the root `CouponsApplication`, which imports no adapters, so it
stays green.

## Phases at a Glance

| Phase | What it delivers | Key risk |
| --- | --- | --- |
| 1. Rewrite ArchitectureTest | ASCII-diagram class doc + 4 named `.because(...)` rules; `layeredArchitecture()` gone | A rewritten rule has an unexpected violation on current code, or silently stops rejecting something the old chain caught — caught by the deliberate-violation probes + green suite |

**Prerequisites:** JDK 21 (`JAVA_HOME=$(/usr/libexec/java_home -v 21)`); Docker for the
full `verify` (Testcontainers) — `-Dtest=ArchitectureTest` alone needs no Docker.
**Estimated effort:** ~30 minutes.

## Open Risks & Assumptions

- Verified by import grep that all four rules pass on the untouched code (no domain
  import outside `java.*` / `..domain..`; nothing depends on `api` / `infrastructure.web`
  from outside; nothing depends on `persistence` / `geoip` from outside; `application`
  imports nothing from `api` / `infrastructure`).
- No automated check proves "new rule set rejects exactly what the old one did" — the
  manual deliberate-violation probes (four categories, one at a time) are the
  equivalence evidence.
- Assumes ArchUnit's `java..` package match covers `java.lang` (it does), so primitive
  / `Object` / `String` deps in the domain don't trip the allowlist.

## Success Criteria (Summary)

- `./mvnw -o test -Dtest=ArchitectureTest` green — 4 rules, 0 failures, 0 production
  changes.
- `./mvnw -o verify` green; only `ArchitectureTest.java` differs; no `layeredArchitecture`
  reference remains.
- Each rule name reads as its guarantee; each of the four deliberate-violation probes
  fails the matching rule with its `.because(...)` message.
