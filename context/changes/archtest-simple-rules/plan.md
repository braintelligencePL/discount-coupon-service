# Rewrite ArchitectureTest as Small, Plain-English Rules — Implementation Plan

## Overview

`src/test/java/com/example/coupons/architecture/ArchitectureTest.java` currently
holds two terse `@ArchTest` fields: `the_domain_is_pure` (a `noClasses()` rule with
an 8-entry package **denylist**) and `respects_layering` (one
`layeredArchitecture().consideringOnlyDependenciesInLayers()` chain with four layer
definitions and four `whereLayer(...)` constraints, and **no failure message**). The
architectural intent lives in a dense `<ul>` javadoc.

This plan rewrites the file so each rule is a **single readable sentence** with a
plain-English field name and a `.because(...)` clause explaining why it exists, and
replaces the javadoc with a compact ASCII layer diagram. It is a **test-only,
single-file** change. No production code, `pom.xml`, or other test changes.

The rewrite is **behaviour-preserving for what gets rejected**: every dependency the
current rules forbid, the new rules still forbid. The one deliberate strengthening is
the domain rule flipping from denylist to allowlist — verified safe because the
current domain packages import only `java.*` and `com.example.coupons.domain.*`.

## Current State Analysis

- **ArchUnit 1.3.0** (`pom.xml`), `archunit-junit5`, `@AnalyzeClasses(packages =
  "com.example.coupons", importOptions = {DoNotIncludeTests, DoNotIncludeJars})`.
- **Production package tree** (41 classes):
  - `api/{dto,web,web.support}` — controllers, request/response DTOs, `ApiRoutes`
  - `application` + `application/{dto,port}` — `CouponService`, boundary records, the
    3 port interfaces
  - `domain/{model,exception}` — `Coupon`, `CouponCode`, `Country`, `CouponRedemption`,
    `UsageLimit`, 7 exceptions
  - `infrastructure/web` — `ApiExceptionHandler`, `ClientIpResolver`,
    `CorrelationIdFilter`
  - `infrastructure/persistence` + `infrastructure/persistence/entity` — the two
    adapters, the two `JpaRepository` interfaces, `CouponEntity`,
    `CouponRedemptionEntity`
  - `infrastructure/geoip` — `IpApiGeoIpResolver`, `GeoIpClientConfig`,
    `GeoIpProperties`
  - `CouponsApplication` (root package) — composition root, wires beans via component
    scan; imports none of the adapters directly.
- **What `respects_layering` enforces today** (translated to plain English):
  1. `whereLayer("Inbound").mayNotBeAccessedByAnyLayer()` — nothing depends on `api`
     or `infrastructure.web`.
  2. `whereLayer("Infrastructure").mayNotBeAccessedByAnyLayer()` — nothing depends on
     `infrastructure.persistence` or `infrastructure.geoip`.
  3. `whereLayer("Application").mayOnlyBeAccessedByLayers("Inbound", "Infrastructure")`
     — the only thing this forbids in practice is `domain → application`.
  4. `whereLayer("Domain").mayOnlyBeAccessedByLayers("Inbound", "Application",
     "Infrastructure")` — forbids nothing meaningful (every internal layer is
     listed); `.consideringOnlyDependenciesInLayers()` already ignores the root
     `CouponsApplication`.
- **What `the_domain_is_pure` enforces today** — `..domain..` must not depend on
  `..api..`, `..application..`, `..infrastructure..`, `org.springframework..`,
  `jakarta.persistence..`, `com.fasterxml.jackson..`,
  `com.github.benmanes.caffeine..`, `io.github.resilience4j..`.

### Key Discoveries:

- **All `respects_layering` constraints collapse into three "leaf / sealed"
  statements plus the domain rule.** Constraints 1 and 2 are direct "package X is
  depended on by nobody outside its group" rules. Constraint 3 (`domain →
  application` forbidden) is *already covered* by an allowlist domain rule. Constraint
  4 is a no-op. `application`'s own outward purity (no `application → api`,
  `application → infrastructure.*`) falls out of constraints 1+2. So the four
  `whereLayer` constraints + the denylist reduce to **4 short rules** (one with a
  deliberate, readability-driven overlap).
- **The allowlist domain rule passes on current code.** `grep` over
  `src/main/java/com/example/coupons/domain/` shows imports only from `java.time`,
  `java.util`, and `com.example.coupons.domain.*`. No Lombok, no annotations. So
  `onlyDependOnClassesThat().resideInAnyPackage("..domain..", "java..")` is green
  today.
- **All three "leaf / sealed" rules pass on current code** (verified by import grep):
  nothing outside `api` + `infrastructure.web` imports them; nothing outside
  `persistence` + `geoip` imports them; `application` imports nothing from `api` or
  `infrastructure`.
- **`infrastructure.web` is grouped with `api`, not with the other infrastructure
  packages.** `CouponRedemptionController` (in `api.web`) depends on `ClientIpResolver`
  (in `infrastructure.web`), so the two form one "HTTP edge" group that is a leaf
  *together*.
- **The explicit `noClasses().that().resideOutsideOfPackages(...)` form is slightly
  stronger than the old chain** because it does *not* exempt the root
  `CouponsApplication`. That class imports no adapters, so this is green — and it is
  the correct behaviour (the composition root should wire beans by scanning, not by
  importing adapter types).

## Desired End State

`ArchitectureTest.java` contains:

1. A class javadoc that is a **4-ring ASCII diagram** + one line naming what lives in
   each package group, replacing the current `<ul>`.
2. **Four `@ArchTest` `ArchRule` fields**, each:
   - a plain-English `snake_case` name that reads as the guarantee
     (`domain_depends_only_on_java_and_itself`, `the_http_edge_is_used_by_nobody`,
     `outbound_adapters_are_reached_only_through_ports`,
     `application_never_depends_on_the_edges_or_adapters`),
   - built from one `noClasses()` / `classes()` fluent expression (no
     `layeredArchitecture()`),
   - carrying a `.because("...")` clause stating why the rule exists.
3. The same `@AnalyzeClasses` configuration as today.

**Verification of "same violations caught":** `./mvnw test -Dtest=ArchitectureTest`
is green on the untouched production code, and a manual deliberate-violation check
(add a forbidden import in each of the four categories, one at a time, confirm the
matching rule — and ideally only that rule — fails, then revert) shows each rule
rejects what its predecessor rejected.

## What We're NOT Doing

- Not touching any production code, `pom.xml`, `application*.yml`, or any other test
  file.
- Not adding coverage: the `application`-imports-Spring-Data / `@Entity`-escapes-
  `persistence` gaps discussed earlier are **out of scope** — a separate change.
- Not adding ArchUnit library rules (`NO_CLASSES_SHOULD_USE_FIELD_INJECTION`,
  `slices().should().beFreeOfCycles()`, `java.util.logging` bans, etc.).
- Not converting the rule fields to `should_`-style names or adding `@DisplayName` —
  `@ArchTest` fields are static `ArchRule`s, not behavioural test methods; ArchUnit
  derives the test name from the rule description.
- Not renaming the class or the file; not moving it out of `architecture/`.
- Not keeping a `layeredArchitecture()` chain anywhere in the file.

## Implementation Approach

Single phase, single file. Rewrite `ArchitectureTest.java` top to bottom:

1. Keep the `package` line and `@AnalyzeClasses` annotation verbatim.
2. Replace imports: drop `com.tngtech.archunit.library.Architectures.layeredArchitecture`;
   keep / add `com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes` and
   `...noClasses`.
3. Replace the javadoc with the ASCII diagram (see Contract below).
4. Write the four rules (see Contract). Each is `static final ArchRule <name> = ...;`
   annotated `@ArchTest`.
5. Run `./mvnw test -Dtest=ArchitectureTest` — must be green with zero production
   changes.
6. Do the deliberate-violation spot check for each rule, reverting each probe.
7. Run `./mvnw verify` for the full suite.

## Phase 1: Rewrite ArchitectureTest as small named rules

### Overview

Replace the two `@ArchTest` fields and the javadoc in
`src/test/java/com/example/coupons/architecture/ArchitectureTest.java` with an ASCII
layer diagram and four single-sentence rules.

### Changes Required:

#### 1. `ArchitectureTest` — class doc + rule set

**File**: `src/test/java/com/example/coupons/architecture/ArchitectureTest.java`

**Intent**: Make every architectural guarantee legible at a glance — a reader should
understand the layering from the class doc in five seconds and understand each rule
from its name alone, with `.because(...)` supplying the rationale when a rule fails.
Preserve exactly which dependencies are rejected.

**Contract**:

- **`@AnalyzeClasses`**: unchanged —
  `packages = "com.example.coupons"`, `importOptions = {ImportOption.DoNotIncludeTests.class,
  ImportOption.DoNotIncludeJars.class}`.

- **Class javadoc**: a compact diagram, e.g.

  ```
  /**
   * The service is built in rings; every dependency points inward.
   *
   *     HTTP edge     api + infrastructure.web
   *                   controllers, request/response DTOs, problem+json advice,
   *                   correlation-id filter, client-IP resolver — used by nobody
   *         │
   *         ▼
   *     application   use cases, the ports (application.port), the tx boundary
   *         │
   *         ▼
   *     domain        plain-Java model + rules — no Spring, no JPA, no libraries
   *
   *     outbound adapters   infrastructure.persistence + infrastructure.geoip
   *                         implement the ports application owns; reachable only
   *                         through those interfaces, never imported directly
   *
   * Each @ArchTest below pins one of these guarantees. The .because(...) text
   * on a failure explains why the rule exists.
   */
  ```

- **Rule 1 — `domain_depends_only_on_java_and_itself`** (replaces `the_domain_is_pure`):

  ```java
  @ArchTest
  static final ArchRule domain_depends_only_on_java_and_itself =
          classes().that().resideInAPackage("..domain..")
                  .should().onlyDependOnClassesThat().resideInAnyPackage("..domain..", "java..")
                  .because("the domain is a plain-Java model any adapter can reuse — "
                          + "it must not reference Spring, JPA, JSON, caching, resilience "
                          + "libraries, or the outer layers");
  ```

- **Rule 2 — `the_http_edge_is_used_by_nobody`** (replaces the Inbound-leaf constraint):

  ```java
  @ArchTest
  static final ArchRule the_http_edge_is_used_by_nobody =
          noClasses().that().resideOutsideOfPackages("..api..", "..infrastructure.web..")
                  .should().dependOnClassesThat().resideInAnyPackage("..api..", "..infrastructure.web..")
                  .because("controllers, DTOs and HTTP glue are the entry edge — "
                          + "application and domain must never point back at them");
  ```

- **Rule 3 — `outbound_adapters_are_reached_only_through_ports`** (replaces the
  Infrastructure-sealed constraint):

  ```java
  @ArchTest
  static final ArchRule outbound_adapters_are_reached_only_through_ports =
          noClasses().that().resideOutsideOfPackages(
                          "..infrastructure.persistence..", "..infrastructure.geoip..")
                  .should().dependOnClassesThat().resideInAnyPackage(
                          "..infrastructure.persistence..", "..infrastructure.geoip..")
                  .because("persistence and geo-IP are outbound adapters — reach them only "
                          + "through the interfaces in application.port, never by importing "
                          + "the adapter or JPA types directly");
  ```

- **Rule 4 — `application_never_depends_on_the_edges_or_adapters`** (explicit
  restatement of `application`'s inward-only dependency; overlaps rules 2–3 by design
  for a targeted failure message):

  ```java
  @ArchTest
  static final ArchRule application_never_depends_on_the_edges_or_adapters =
          noClasses().that().resideInAPackage("..application..")
                  .should().dependOnClassesThat().resideInAnyPackage(
                          "..api..", "..infrastructure..")
                  .because("use-case code orchestrates the domain through its own ports — "
                          + "it must not know about controllers, HTTP glue, or any adapter");
  ```

- **Imports**: remove
  `import static com.tngtech.archunit.library.Architectures.layeredArchitecture;`;
  ensure
  `import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;` and
  `import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;`
  are present. Keep the `ImportOption`, `AnalyzeClasses`, `ArchTest`, `ArchRule`
  imports.

- **Rule count / names may be tuned by the implementer** if a rule proves to have an
  unexpected violation on current code — but the default is these four, and any
  change must keep "every dependency the old rules rejected is still rejected".

### Success Criteria:

#### Automated Verification:

- Compiles: `JAVA_HOME=$(/usr/libexec/java_home -v 21) ./mvnw -q -o test-compile`
- ArchitectureTest green: `JAVA_HOME=$(/usr/libexec/java_home -v 21) ./mvnw -o test -Dtest=ArchitectureTest` — reports 4 rules run, 0 failures
- Full suite green: `JAVA_HOME=$(/usr/libexec/java_home -v 21) ./mvnw -o verify`
- No production sources changed: `git status --porcelain src/main` is empty
- Only this one test file changed: `git status --porcelain src/test` lists just
  `architecture/ArchitectureTest.java`
- `layeredArchitecture` no longer referenced: `grep -c layeredArchitecture src/test/java/com/example/coupons/architecture/ArchitectureTest.java` is 0

#### Manual Verification:

- Reading only the class javadoc conveys the four-ring layering.
- Each of the four rule names reads as the guarantee it enforces, with no need to
  read the body.
- Deliberate-violation spot check, one probe at a time, reverting each:
  - add `import com.example.coupons.application.CouponService;` to a `domain/model`
    class → `domain_depends_only_on_java_and_itself` fails
  - add `import org.springframework.stereotype.Component;` to a `domain/model` class →
    `domain_depends_only_on_java_and_itself` fails
  - make a `domain` class import `com.example.coupons.api.dto.CouponResponse` →
    `the_http_edge_is_used_by_nobody` fails
  - make `CouponService` import
    `com.example.coupons.infrastructure.persistence.CouponPersistenceAdapter` →
    `outbound_adapters_are_reached_only_through_ports` **and**
    `application_never_depends_on_the_edges_or_adapters` fail
- Each failure message shows the `.because(...)` rationale.

**Implementation Note**: After automated verification passes, pause for human
confirmation of the manual review (diagram readability, rule-name clarity, spot-check
results) before the change is considered done.

---

## Testing Strategy

### Unit Tests:

- The change *is* a test rewrite. `ArchitectureTest` itself is the artefact under
  change; its own pass/fail on untouched production code is the primary signal.
- The equivalence guarantee ("same violations caught") is checked by the manual
  deliberate-violation probes, since there is no automated way to assert "rule set A
  and rule set B reject the same set of dependency edges".

### Integration Tests:

- Unaffected. `./mvnw verify` runs the full unit + Testcontainers suite to confirm
  nothing else moved.

### Manual Testing Steps:

1. `git diff --stat` — exactly one file, `ArchitectureTest.java`.
2. Read the new class javadoc cold; confirm the layer model is clear without reading
   any rule body.
3. For each of the four probes listed under Manual Verification: apply it, run
   `./mvnw -o test -Dtest=ArchitectureTest`, confirm the expected rule fails with its
   `.because(...)` text, `git checkout` the probe.
4. Confirm `./mvnw -o verify` is green with all probes reverted.

## Performance Considerations

None. Four `noClasses()` / `classes()` rules over ~41 classes run in well under a
second — comparable to the current two-rule cost (~0.5s).

## Migration Notes

Not applicable — no data, no API, no deployed artefact.

## References

- Decisions: `context/changes/archtest-simple-rules/plan-brief.md`
- File under change: `src/test/java/com/example/coupons/architecture/ArchitectureTest.java`
- Layering origin: `context/changes/layered-architecture/plan-brief.md` (defines the
  four package groups this test pins)
- ArchUnit 1.3.0 fluent API: `classes()`, `noClasses()`, `onlyDependOnClassesThat()`,
  `resideOutsideOfPackages()`, `.because()`

## Progress

> Convention: `- [ ]` pending, `- [x]` done. Append ` — <commit sha>` when a step lands. Do not rename step titles. See `references/progress-format.md`.

### Phase 1: Rewrite ArchitectureTest as small named rules

#### Automated

- [x] 1.1 Compiles: `./mvnw -q -o test-compile`
- [x] 1.2 ArchitectureTest green (4 rules, 0 failures): `./mvnw -o test -Dtest=ArchitectureTest`
- [x] 1.3 Full suite green: `./mvnw -o verify`
- [x] 1.4 No production sources changed: `git status --porcelain src/main` is empty
- [x] 1.5 Only `architecture/ArchitectureTest.java` changed under `src/test`
- [x] 1.6 `grep -c layeredArchitecture` on the file is 0

#### Manual

- [ ] 1.7 Class javadoc conveys the four-ring layering on its own
- [ ] 1.8 Each rule name reads as its guarantee without reading the body
- [ ] 1.9 Deliberate-violation probes: each forbidden import fails the matching rule (4 probes, reverted)
- [ ] 1.10 Failure messages show the `.because(...)` rationale
