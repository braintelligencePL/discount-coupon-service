# Test Suite → Given/When/Then Style — Plan Brief

> Full plan: `context/changes/test-gwt-style/plan.md`

## What & Why

The test suite names tests as bare snake_case behavior phrases with no internal
structure. This change gives every behavioral test a uniform shape: a
`should_<behavior>_when_<condition>` name, a `@DisplayName` sentence, and lowercase
`// given` / `// when` / `// then` blocks in the body. The point is readability and a
single house convention — a reviewer should be able to read any test top-to-bottom
and see arrange / act / assert at a glance.

## Starting Point

11 test classes: 5 domain unit tests, `CouponServiceTest`, `CouponControllerTest`,
`IpApiGeoIpResolverTest`, and 3 Testcontainers ITs, plus `ArchitectureTest` and two
support classes. JUnit 5 + AssertJ + Mockito + WireMock + Testcontainers. No
Checkstyle/Spotless — nothing enforces naming, so this is a convention-only refactor
with `./mvnw verify` (23 unit + 4 IT) as the equivalence check. Several tests bundle
multiple scenarios in one method; the 3 ITs are deliberately sequential "ladder"
flows.

## Desired End State

Every `@Test` in the 8 behavioral classes is named `should_…`, carries a
`@DisplayName("should …")` sentence, and has its body split by `// given` / `// when`
/ `// then` (`// and` for extra steps, empty phases omitted). Multi-scenario domain
tests are split one-behavior-per-test (~21 → ~43 unit methods); the 3 IT flows stay
single tests with each step labeled. `ArchitectureTest` and the support classes are
untouched. No production code changes; the full suite passes unchanged.

## Key Decisions Made

| Decision | Choice | Why | Source |
| --- | --- | --- | --- |
| Method-name pattern | `should_<behavior>_when_<cond>` snake_case | Matches the repo's existing snake_case test convention; reads as a sentence | Plan |
| Multi-scenario domain tests | Split into separate `should_` `@Test`s | Each rule violation reports independently; names document each case | Plan |
| "Split" vs "multi-assert" | Split *independent scenarios* only; keep multiple asserts about *one action* together | Splitting a single `Coupon.create` call's field checks would duplicate setup and assert one field each — worse | Plan |
| Integration "ladder" tests | Keep one `@Test` per flow, label each step | Preserves the intended end-to-end story and shared PostgreSQL bootstrap; honors the class javadoc | Plan |
| Comment markers | lowercase `// given` / `// when` / `// then`, omit empty, `// and` for continuations | Minimal noise; no filler comments on trivial value-object tests | Plan |
| `@DisplayName` | Add a human sentence to every `@Test` | Prettiest IDE/CI output; room for real punctuation ("problem+json", "404") | Plan |
| Scope | 8 classes with `@Test` methods (11 files) | Whole behavioral suite converges on one style | Plan |
| `ArchitectureTest` | Excluded | `@ArchTest` static fields have no body to restructure; rule names already clear | Plan |
| Parameterized tests | Not used — discrete `@Test`s | Naming decision was "split", not "parameterize" | Plan |

## Scope

**In scope:** `CouponTest`, `CouponCodeTest`, `CountryTest`, `CouponRedemptionTest`,
`UsageLimitTest`, `CouponServiceTest`, `CouponControllerTest`,
`IpApiGeoIpResolverTest`, `CouponApiIT`, `CouponRedemptionApiIT`,
`CouponRedemptionCountryIT` — rename, `@DisplayName`, GWT markers, split where the
decision calls for it. One statement extract in `CouponControllerTest`
(`ResultActions result = …`) so `// when`/`// then` are separate.

**Out of scope:** `ArchitectureTest`, `StubGeoIpConfiguration`,
`TestcontainersConfiguration`, all production code, `pom.xml`, YAML, Liquibase,
fixtures. No assertion added, removed, or weakened. No class/file renames. No
`@Nested` / `@TestMethodOrder`. No reformatting of helpers or imports beyond the
`DisplayName` / `ResultActions` additions.

## Architecture / Approach

Layer-by-layer, one phase per layer, each gated on the relevant test run plus the
full suite and a diff review. Per file: add the `DisplayName` import; for each test
decide keep vs split by the "independent scenario" rule; rename; insert markers
around existing statements without reordering anything a test depends on (notably
`CouponServiceTest`'s ordering verifications); move each scenario's assertions into
its own `should_` method when splitting. Helpers and constants stay put.

## Phases at a Glance

| Phase | What it delivers | Key risk |
| --- | --- | --- |
| 1. Domain unit tests | 5 `domain/model` classes converted; the bulk of the splitting (~21 → ~43 methods) | An `assertThatThrownBy` from a bundled test silently dropped during a split — caught by diff review + green suite |
| 2. Application / web-slice / geo-IP | `CouponServiceTest`, `CouponControllerTest`, `IpApiGeoIpResolverTest` converted; MockMvc extract; one geo-IP split | Reordering `CouponServiceTest`'s stub/verify sequence and breaking an ordering assertion |
| 3. Integration tests | 3 ITs converted; each flow one `@Test` with labeled steps | Dropping or reordering an HTTP step / `problem+json` code assertion in a ladder |

**Prerequisites:** JDK 21 (`JAVA_HOME=$(/usr/libexec/java_home -v 21)`); Docker
running for Phase 3 (Testcontainers).
**Estimated effort:** ~1 session, front-loaded on Phase 1.

## Open Risks & Assumptions

- Splitting redistributes assertions; the only real failure mode is losing one in
  the move. Mitigated by per-phase `git diff` review for `assertThat*` on `-` lines
  absent from `+` lines, plus the suite staying green.
- Assumes `should_…` snake_case method names are acceptable at their resulting length
  (some exceed 60 chars, e.g.
  `should_produce_a_distinct_outcome_for_each_rung_of_the_redemption_ladder`).
- Assumes no external tooling (IDE inspections, CI grep rules) keys off the current
  test-method names.
- `@DisplayName` on every test is ~1 extra line per method; the suite grows by
  roughly a third in method count. Accepted per the decisions above.

## Success Criteria (Summary)

- `./mvnw verify` green after every phase; IT method count unchanged at 4; no file
  under `src/main/` modified.
- Every behavioral `@Test` is `should_…` + `@DisplayName` + GWT-marked; markers
  follow the lowercase / omit-empty / `// and` convention.
- Diff review confirms the post-refactor suite asserts exactly what the pre-refactor
  suite asserted — nothing added, dropped, or weakened.
