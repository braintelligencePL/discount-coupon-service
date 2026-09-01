---
change_id: test-gwt-style
title: Convert the test suite to Given/When/Then style with should_ names
status: impl_reviewed
created: 2026-09-01
updated: 2026-09-01
archived_at: null
---

## Notes

Test-only refactor. Every `@Test` in the 8 behavioral test classes gets:

- a `should_<behavior>_when_<condition>` snake_case method name,
- a `@DisplayName("should …")` sentence,
- lowercase `// given` / `// when` / `// then` blocks (`// and` for extra steps;
  empty sections omitted).

Multi-scenario domain tests (`rejects_invalid_state`, `rejects_unknown_or_malformed_codes`,
`rejects_blank_null_and_oversized`, the `UsageLimit` accept/reject bundle, the
`CouponRedemption` missing-fields bundle, the geo-IP loopback-vs-private test) split
one-behavior-per-test. The 3 integration "ladder" flows stay single `@Test`s with
each step labeled.

Out of scope: `ArchitectureTest` (@ArchTest fields, no bodies), `TestcontainersConfiguration`,
`StubGeoIpConfiguration`, any production code, `pom.xml`. No behaviour change — every
existing assertion is preserved; `./mvnw verify` is the equivalence check.

Decisions captured in `plan-brief.md`.
