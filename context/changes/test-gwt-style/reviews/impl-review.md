<!-- IMPL-REVIEW-REPORT -->
# Implementation Review: Test Suite → Given/When/Then Style

- **Plan**: context/changes/test-gwt-style/plan.md
- **Scope**: Phase 1–3 of 3 (full plan)
- **Date**: 2026-09-01
- **Verdict**: APPROVED
- **Findings**: 0 critical, 0 warnings, 3 observations

## Verdicts

| Dimension | Verdict |
|-----------|---------|
| Plan Adherence | PASS |
| Scope Discipline | PASS |
| Safety & Quality | PASS |
| Architecture | PASS |
| Pattern Consistency | WARNING |
| Success Criteria | PASS |

## Summary

All 11 planned test files converted; no other files touched. `./mvnw verify` green
(45 unit + 4 IT, up from 23 + 4). Assertion payloads verified byte-identical old→new
across all 11 files after whitespace-strip — the only content deltas are (a) the
sanctioned geo-IP split adding a second `assertThat(httpCalls()).isZero()` per the
plan's Phase 2 contract, and (b) one explanatory comment relocated to its own line in
`CouponCodeTest`. No production code, no `pom.xml`, no `ArchitectureTest`, no support
classes, no `@ParameterizedTest` / `@Nested` — every "What We're NOT Doing" guardrail
held. Findings are all OBSERVATION-level internal-consistency notes.

## Success Criteria

**Automated — all pass:**

| Check | Result |
|---|---|
| `./mvnw -o test-compile` | pass |
| domain tests (`CouponTest,CouponCodeTest,CountryTest,CouponRedemptionTest,UsageLimitTest`) | 31 pass |
| full unit suite `./mvnw -o test` | 45 pass |
| targeted P2 classes | 12 pass |
| `./mvnw -o verify` | BUILD SUCCESS — 45 unit + 4 IT |
| `git status --porcelain src/main` empty | pass |
| `ArchitectureTest` unchanged (`git diff --quiet`) | pass |
| Failsafe `Tests run: 4` across 3 IT classes | pass |
| only the 11 planned test files differ under `src/test` | pass |

**Manual — all checked, evidence present:** every `@Test` renamed to `should_…` with a
`@DisplayName` (verified by grep parity: `@Test` count == `@DisplayName` count in every
file); marker style present; assertion-preservation confirmed by payload diff. Not
rubber-stamped.

## Findings

### F1 — IT marker style diverges from the rest of the suite

- **Severity**: 🟦 OBSERVATION
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Pattern Consistency
- **Location**: src/test/java/com/example/coupons/CouponApiIT.java, CouponRedemptionApiIT.java, CouponRedemptionCountryIT.java
- **Detail**: The plan's convention decision was "lowercase `// given` / `// when` /
  `// then`, `// and` for continuations" — bare markers. Phases 1–2 (domain + mid-layer)
  follow this. Phase 3's integration flows instead use descriptive tails:
  `// when the first user redeems`, `// then one use remains`,
  `// and when the second user redeems`. Rationale given at the Phase 3 gate: bare
  markers repeated 4–5× in a ladder don't make step boundaries legible, which is the
  plan's stated goal for ITs. The user accepted this at the gate ("its ok i think").
  Net effect: a reader sees two marker dialects across the suite.
- **Fix**: Accept as-is (already user-approved) — optionally add a one-line note to the
  plan-brief's convention section recording that ladder tests use descriptive marker
  tails while single-scenario tests use bare markers, so the split is intentional and
  documented.
- **Decision**: PENDING

### F2 — One split test renamed vs the plan's suggested name

- **Severity**: 🟦 OBSERVATION
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Plan Adherence
- **Location**: src/test/java/com/example/coupons/domain/model/CouponRedemptionTest.java:57
- **Detail**: Plan Phase 1 contract lists `should_reject_a_null_timestamp_in_the_canonical_constructor`;
  implementation uses `should_reject_a_null_required_field_in_the_canonical_constructor`.
  The original assertion is `new CouponRedemption(CouponCode.of("x"), "u", null, null)` —
  two null args, not just the timestamp — so the implemented name is more accurate. The
  plan explicitly says method names are for "the implementer to finalize".
- **Fix**: None needed — the rename is a correctness improvement within delegated latitude.
- **Decision**: PENDING

### F3 — Fused-throw marker choice differs between domain and service tests

- **Severity**: 🟦 OBSERVATION
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Pattern Consistency
- **Location**: src/test/java/com/example/coupons/domain/model/*Test.java vs src/test/java/com/example/coupons/application/CouponServiceTest.java
- **Detail**: In the domain tests, a test whose whole body is one fused
  `assertThatThrownBy(() -> factory())` expression is marked `// then` only (no `// when`),
  relying on the convention's "omit empty phases". In `CouponServiceTest`, the fused
  `assertThatThrownBy(() -> service.redeem(...))` is marked `// when`, with the following
  `verify(..., never())` calls under `// then`. Both are defensible — in the service test
  `redeem()` is the genuine action and the `verify` calls are the real assertions; in the
  domain tests the throw *is* the assertion — but a reader skimming for a rule sees the
  same construct (`assertThatThrownBy`) tagged two different ways.
- **Fix**: Leave as-is — the distinction tracks a real difference (fused-throw-is-the-test
  vs. action-then-verify). If uniformity is preferred, tag every fused `assertThatThrownBy`
  `// when` and drop the lone `// then`, accepting a `// when`-only body in the domain tests.
- **Decision**: PENDING
