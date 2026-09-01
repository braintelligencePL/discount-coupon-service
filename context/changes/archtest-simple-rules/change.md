---
change_id: archtest-simple-rules
title: Rewrite ArchitectureTest as small, plain-English ArchUnit rules
status: implementing
created: 2026-09-01
updated: 2026-09-01
archived_at: null
---

## Notes

Single-file, test-only rewrite of
`src/test/java/com/example/coupons/architecture/ArchitectureTest.java`.

Replace the 2 dense `@ArchTest` fields (a package **denylist** for domain purity + one
`layeredArchitecture()` chain) with ~4 small `noClasses()` / `classes()` rules, each a
single readable sentence carrying a `.because(...)` that says *why* the rule exists.
Class javadoc becomes a compact 4-ring ASCII layer diagram.

Decisions (from planning):
- **Granularity**: split into small named rules (no `layeredArchitecture()` chain).
- **Coverage**: readability only — the rewritten rules must reject exactly the code
  the current ones reject. The domain rule flips denylist → allowlist, which is
  strictly stronger, but the current domain code passes it (verified: domain imports
  only `java.*` + `com.example.coupons.domain.*`).
- **Domain rule**: allowlist — `..domain..` may depend only on `..domain..` + `java..`.
- **Wording**: plain-English field names + `.because(...)` + ASCII diagram.

Out of scope: production code, `pom.xml`, the two known coverage gaps
(`application` importing Spring Data / JPA; `@Entity` confined to
`infrastructure.persistence`) — deliberately deferred to a separate change.

Decisions captured in `plan-brief.md`.
