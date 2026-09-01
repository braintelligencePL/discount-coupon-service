# Test Suite → Given/When/Then Style Implementation Plan

## Overview

Convert every behavioral test in `src/test/` to a consistent Given/When/Then shape:

- **Method names**: `should_<behavior>_when_<condition>` in snake_case (the `_when_` clause
  is dropped when there is no meaningful precondition).
- **`@DisplayName`**: a human sentence on every `@Test`, e.g.
  `@DisplayName("should return 409 problem+json when the code is duplicate")`.
- **Body structure**: lowercase `// given` / `// when` / `// then` comment markers
  separating the three phases; `// and` for a second step within a phase; a marker
  is omitted entirely when its phase has no statements.
- **Splitting**: tests that bundle *independent scenarios* (distinct inputs, distinct
  rule violations) are split into one `@Test` per scenario. Tests that make several
  assertions about the result of a *single action* stay as one `@Test`.

This is a pure test refactor. No production code, no `pom.xml`, no schema, no
behaviour change. The full suite (`./mvnw verify`) is the equivalence check — every
assertion that exists today still runs afterwards; none are added or removed.

## Current State Analysis

The suite has 11 test classes plus support code. Naming today is snake_case behavior
phrases with no prefix and no structure comments:

- Domain unit tests — `CouponTest` (3), `CouponCodeTest` (2), `CountryTest` (2),
  `CouponRedemptionTest` (2), `UsageLimitTest` (1).
- `application/CouponServiceTest` (2) — Mockito, verifies two ordering guarantees.
- `api/web/CouponControllerTest` (4) — `@WebMvcTest` slice, MockMvc fluent chains.
- `infrastructure/geoip/IpApiGeoIpResolverTest` (5) — WireMock + real circuit breaker.
- `CouponApiIT` (1), `CouponRedemptionApiIT` (1), `CouponRedemptionCountryIT` (2) —
  `@SpringBootTest` + Testcontainers PostgreSQL, real HTTP.
- `architecture/ArchitectureTest` (2) — `@ArchTest` **static fields**, not methods.
- `support/StubGeoIpConfiguration`, `TestcontainersConfiguration` — test infra.

Surefire runs 23 unit tests (21 behavioral + 2 `@ArchTest`), Failsafe runs 4 ITs.

### Key Discoveries:

- **No naming enforcement.** `pom.xml` has no Checkstyle / Spotless / PMD / SpotBugs
  (`pom.xml:139-175` is only the Spring Boot, Surefire, Failsafe plugins). This is a
  convention-only change; nothing mechanical will reject or rewrite the names.
- **JUnit 5 + AssertJ + Mockito + WireMock + Testcontainers**, versions managed by
  `spring-boot-starter-parent` (`pom.xml:9`). `@DisplayName` is
  `org.junit.jupiter.api.DisplayName` — already on the classpath, no new dependency.
- **Three test-body shapes that are not one GWT triplet:**
  - *Multi-scenario domain tests* — `CouponTest.rejects_invalid_state`
    (`CouponTest.java:42-59`, 6 `assertThatThrownBy`), `CountryTest.rejects_unknown_or_malformed_codes`
    (`CountryTest.java:17-23`, 4), `CouponCodeTest.rejects_blank_null_and_oversized`
    (`CouponCodeTest.java:19-26`, 3 throws + 1 accept), `UsageLimitTest`
    (`UsageLimitTest.java:11-16`, 1 accept + 2 throws),
    `CouponRedemptionTest.rejects_missing_required_fields`
    (`CouponRedemptionTest.java:27-37`, 4). → **split**.
  - *Multi-facet single-action tests* — `CouponTest.create_normalizes_…`
    (`CouponTest.java:17-29`, 5 asserts on one `Coupon.create` call **plus** one
    unrelated blank-country throw), `CouponRedemptionTest.record_stamps_…`
    (`CouponRedemptionTest.java:17-25`, 4 asserts on one `record()` call). → the
    field asserts stay together; the trailing unrelated throw in `create_normalizes_…`
    splits out.
  - *Integration "ladder" flows* — `CouponApiIT.creates_fetches_case_insensitively_and_404s_on_a_miss`
    (`CouponApiIT.java:41-65`), `CouponRedemptionApiIT.redemption_ladder_produces_a_distinct_outcome_for_each_case`
    (`CouponRedemptionApiIT.java:42-61`), `CouponRedemptionCountryIT.a_country_restricted_coupon_enforces_the_resolved_country`
    (`CouponRedemptionCountryIT.java:41-57`). The class javadoc on `CouponRedemptionApiIT`
    calls it a "ladder" and shares one PostgreSQL bootstrap. → **keep as one `@Test`,
    label each step**.
- **MockMvc chains** (`CouponControllerTest`) put the exercise and the assertions in
  one fluent `mockMvc.perform(...).andExpect(...)` expression — needs a small extract
  (`ResultActions result = mockMvc.perform(...);` then `result.andExpect(...)`) to give
  `// when` and `// then` distinct statements.
- **`CouponServiceTest` already carries explanatory `//` comments**
  (`CouponServiceTest.java:71-72, 84`) — these fold into the new `// given` / `// then`
  blocks rather than being duplicated.

## Desired End State

Every `@Test` method in the 8 behavioral classes:

1. is named `should_…` (snake_case), optionally `…_when_…`;
2. has a `@DisplayName("should …")` sentence;
3. has its body divided by `// given` / `// when` / `// then` (+ `// and`) markers,
   with empty phases' markers omitted;
4. asserts exactly what the pre-refactor suite asserted — verifiable by
   `./mvnw verify` staying green and by a diff review showing no assertion
   dropped or weakened.

`ArchitectureTest`, `StubGeoIpConfiguration`, `TestcontainersConfiguration` are
byte-for-byte unchanged. No file outside `src/test/java/com/example/coupons/` is
touched.

Expected method counts after the refactor (approximate — implementer finalizes):

| Class | Before | After |
| --- | --- | --- |
| `CouponTest` | 3 | ~10 |
| `CouponCodeTest` | 2 | ~7 |
| `CountryTest` | 2 | ~6 |
| `CouponRedemptionTest` | 2 | ~5 |
| `UsageLimitTest` | 1 | 3 |
| `CouponServiceTest` | 2 | 2 |
| `CouponControllerTest` | 4 | 4 |
| `IpApiGeoIpResolverTest` | 5 | ~6 |
| **Unit behavioral total** | **21** | **~43** |
| `CouponApiIT` / `CouponRedemptionApiIT` / `CouponRedemptionCountryIT` | 1 / 1 / 2 | 1 / 1 / 2 |

## What We're NOT Doing

- Not touching `ArchitectureTest` — `@ArchTest` static fields have no method body to
  restructure; the existing rule names (`the_domain_is_pure`, `respects_layering`)
  stay.
- Not touching `StubGeoIpConfiguration` or `TestcontainersConfiguration`.
- Not changing any production code, `pom.xml`, `application*.yml`, Liquibase, or
  fixtures.
- Not adding, removing, or weakening any assertion; not changing what any test
  proves. Splitting redistributes existing assertions; it does not introduce new
  coverage.
- Not converting anything to `@ParameterizedTest` — splitting is done with discrete
  `@Test` methods (per the naming decision).
- Not introducing `@Nested` grouping or `@TestMethodOrder`.
- Not renaming test classes or files.
- Not reformatting unrelated lines, imports, or helper methods (`coupon(...)`,
  `command()`, `json(...)`, `stub(...)`, `redeem(...)` stay as they are).

## Implementation Approach

Work layer by layer, one phase per layer, each phase gated on the relevant test run
plus the full suite. Within a file:

1. Add `import org.junit.jupiter.api.DisplayName;`.
2. For each existing `@Test`: decide *keep* or *split* using the rule in the Overview.
3. For a *keep*: rename to `should_…`, add `@DisplayName`, insert `// given` / `// when`
   / `// then` markers around the existing statements (reordering only to group
   arrange / act / assert — never changing values or call order that a test depends
   on, e.g. `CouponServiceTest`'s ordering verifications).
4. For a *split*: create one `should_…` `@Test` per scenario, each with its own
   `@DisplayName` and GWT markers, moving the matching `assertThatThrownBy` /
   `assertThat` into it. Shared arrange (constants, `FIXED_CLOCK`) stays at class
   level; per-case arrange goes in that test's `// given`.
5. Keep helper methods and constants untouched.

**Marker rules (from the comment-convention decision):**

- Lowercase `// given`, `// when`, `// then`; `// and` for a second statement/step
  in the same phase.
- Omit a phase's marker when that phase has no statements (e.g. value-object tests
  with nothing to arrange start at `// when`).
- In the integration ladders, the flow is a sequence of `when → then` pairs; label
  each: `// when`, `// then`, then `// and` / `// when` for the next step, so the
  step boundaries are visible.

## Critical Implementation Details

- **`CouponServiceTest` statement order is load-bearing.** Both tests assert an
  ordering guarantee (`redemption_row_is_inserted_before_the_counter_update`,
  `country_check_runs_before_any_database_write`). The `when(...)` stubs, the
  `assertThatThrownBy`, and the `verify(..., never())` / `verifyNoInteractions`
  calls must keep their relative order — only add markers around them, do not
  resequence.
- **`CouponControllerTest` needs a statement extract.** Turn
  `mockMvc.perform(post(...)...).andExpect(...)...` into
  `ResultActions result = mockMvc.perform(post(...)...);` under `// when` and
  `result.andExpect(...)...` under `// then`. Add
  `import org.springframework.test.web.servlet.ResultActions;`.
- **`IpApiGeoIpResolverTest.repeated_provider_errors_…` must not be split.** The
  8-iteration loop and the "breaker opened" assertion are one scenario; splitting
  breaks the circuit-state build-up.

## Phase 1: Domain unit tests

### Overview

Convert the 5 `domain/model` test classes. This phase carries the bulk of the
splitting.

### Changes Required:

#### 1. `CouponTest`

**File**: `src/test/java/com/example/coupons/domain/model/CouponTest.java`

**Intent**: Rename + `@DisplayName` + GWT markers on all 3 tests; split the two
multi-scenario ones. Keep `NOW` / `FIXED_CLOCK` constants.

**Contract**:
- `create_normalizes_stamps_time_zeroes_the_counter_and_handles_country` → keep the
  5 asserts on the one `Coupon.create("  SUMMER ", 3, "de", …)` call as
  `should_normalize_code_stamp_time_and_zero_the_counter_on_create`; move the
  trailing blank-country `assertThatThrownBy` into a new
  `should_reject_a_blank_country_on_create`.
- `query_methods_reflect_counter_state` → split into
  `should_report_uses_remaining_when_the_counter_is_below_the_limit` and
  `should_report_exhausted_when_the_counter_reaches_the_limit`.
- `rejects_invalid_state` → split into one test per throw:
  `should_reject_a_null_code`, `should_reject_a_null_created_at`,
  `should_reject_a_null_country`, `should_reject_a_negative_use_counter`,
  `should_reject_a_use_counter_above_the_limit`,
  `should_reject_a_non_positive_max_uses_via_the_factory`.

#### 2. `CouponCodeTest`

**File**: `src/test/java/com/example/coupons/domain/model/CouponCodeTest.java`

**Intent**: Rename + `@DisplayName` + markers; split both tests.

**Contract**:
- `normalizes_and_is_case_insensitive` → `should_trim_and_lowercase_the_value`,
  `should_treat_differently_cased_codes_as_equal`,
  `should_reject_a_non_normalized_value_in_the_canonical_constructor`.
- `rejects_blank_null_and_oversized` → `should_reject_a_blank_code`,
  `should_reject_a_null_code`, `should_reject_a_code_longer_than_the_max`,
  `should_accept_a_code_at_the_max_length`.

#### 3. `CountryTest`

**File**: `src/test/java/com/example/coupons/domain/model/CountryTest.java`

**Intent**: Rename + `@DisplayName` + markers; split both tests.

**Contract**:
- `normalizes_a_valid_alpha2_code_to_uppercase` → `should_trim_and_uppercase_the_code`,
  `should_accept_an_already_uppercase_code_in_the_canonical_constructor`.
- `rejects_unknown_or_malformed_codes` → `should_reject_an_unknown_code`,
  `should_reject_a_malformed_code`, `should_reject_a_null_code`,
  `should_reject_a_lowercase_value_in_the_canonical_constructor`.

#### 4. `CouponRedemptionTest`

**File**: `src/test/java/com/example/coupons/domain/model/CouponRedemptionTest.java`

**Intent**: Rename + `@DisplayName` + markers; keep the single-action test whole,
split the missing-fields bundle.

**Contract**:
- `record_stamps_the_time_and_the_resolved_country` → keep the 4 asserts together as
  `should_stamp_the_time_and_resolved_country_on_record`.
- `rejects_missing_required_fields` → `should_reject_a_null_coupon_code`,
  `should_reject_a_blank_user_id`, `should_reject_a_null_resolved_country`,
  `should_reject_a_null_timestamp_in_the_canonical_constructor`.

#### 5. `UsageLimitTest`

**File**: `src/test/java/com/example/coupons/domain/model/UsageLimitTest.java`

**Intent**: Rename + `@DisplayName` + markers; split the accept/reject bundle.

**Contract**: `accepts_a_positive_value_and_rejects_anything_else` →
`should_accept_a_positive_value`, `should_reject_zero`,
`should_reject_a_negative_value`. Value-object tests have nothing to arrange — start
at `// when` (or `// when` + `// then` only).

### Success Criteria:

#### Automated Verification:

- Compiles: `JAVA_HOME=$(/usr/libexec/java_home -v 21) ./mvnw -q -o test-compile`
- Domain tests pass: `JAVA_HOME=$(/usr/libexec/java_home -v 21) ./mvnw -q -o test -Dtest='com.example.coupons.domain.model.*'`
- Full unit suite passes: `JAVA_HOME=$(/usr/libexec/java_home -v 21) ./mvnw -q -o test`
- No production sources changed: `git status --porcelain src/main` is empty
- `ArchitectureTest` untouched: `git diff --quiet -- src/test/java/com/example/coupons/architecture/ArchitectureTest.java`

#### Manual Verification:

- Every `@Test` in the 5 files is named `should_…` and has a `@DisplayName`.
- Diff review confirms every original `assertThat` / `assertThatThrownBy` still
  exists in exactly one split test — none dropped, none weakened.
- Marker style matches the convention (lowercase, empty phases omitted, `// and` for
  continuations).
- `@DisplayName` sentences read naturally in the IDE test-run view.

**Implementation Note**: After automated verification passes, pause for human
confirmation of the manual review before starting Phase 2.

---

## Phase 2: Application, web-slice & geo-IP tests

### Overview

Convert `CouponServiceTest`, `CouponControllerTest`, `IpApiGeoIpResolverTest`. Mostly
rename + markers; one statement extract in the controller test; one split in the
geo-IP test.

### Changes Required:

#### 1. `CouponServiceTest`

**File**: `src/test/java/com/example/coupons/application/CouponServiceTest.java`

**Intent**: Rename both tests, add `@DisplayName`, wrap the existing statements in
`// given` / `// when` / `// then` markers, folding the existing explanatory `//`
comments into those blocks. Preserve statement order (ordering guarantees under
test).

**Contract**:
- `redemption_row_is_inserted_before_the_counter_update` →
  `should_insert_the_redemption_row_before_updating_the_counter`.
- `country_check_runs_before_any_database_write` →
  `should_run_the_country_check_before_any_database_write`.
- `// given` = the `when(...)` / `doThrow(...)` stubs; `// when` = the
  `assertThatThrownBy(() -> service.redeem(...))`; `// then` = the `verify(...,
  never())` / `verifyNoInteractions(...)`. Keep `@MockitoSettings(strictness = LENIENT)`,
  `@BeforeEach setUp`, and helpers as-is.

#### 2. `CouponControllerTest`

**File**: `src/test/java/com/example/coupons/api/web/CouponControllerTest.java`

**Intent**: Rename all 4 tests, add `@DisplayName`, and extract the MockMvc call so
`// when` (perform) and `// then` (andExpect chain) are separate statements. Add the
`ResultActions` import.

**Contract**:
- `create_returns_201_with_location_and_body` →
  `should_return_201_with_location_and_body_on_create`.
- `create_with_duplicate_code_returns_409_problem_json` →
  `should_return_409_problem_json_when_the_code_is_duplicate`.
- `create_with_an_invalid_body_returns_400_validation_error_with_field_details` →
  `should_return_400_with_field_details_when_the_body_is_invalid`.
- `malformed_json_body_returns_400_problem_json_with_code` →
  `should_return_400_problem_json_when_the_json_is_malformed`.
- Body shape: `// given` = `when(couponService…)` stub (omit for the two tests that
  have none); `// when` = `ResultActions result = mockMvc.perform(post(…)…);`;
  `// then` = `result.andExpect(…)…`.

#### 3. `IpApiGeoIpResolverTest`

**File**: `src/test/java/com/example/coupons/infrastructure/geoip/IpApiGeoIpResolverTest.java`

**Intent**: Rename all 5 tests, add `@DisplayName`, add markers; split the
loopback-vs-private-network test. Do **not** split the circuit-breaker loop test.
Keep `@BeforeEach` / `@AfterEach` / helpers.

**Contract**:
- `resolves_country_on_a_successful_response` →
  `should_resolve_the_country_on_a_successful_response`.
- `provider_fail_status_is_undetermined` →
  `should_be_undetermined_when_the_provider_reports_failure`.
- `a_successful_resolution_is_cached` →
  `should_cache_a_successful_resolution` (`// when` = two `resolve` calls,
  `// then` = `httpCalls() == 1`).
- `a_non_public_ip_is_undetermined_without_calling_the_provider` → split into
  `should_not_call_the_provider_for_a_loopback_ip` (127.0.0.1) and
  `should_not_call_the_provider_for_a_private_network_ip` (10.0.0.1); each asserts
  empty result **and** `httpCalls() == 0`.
- `repeated_provider_errors_stay_undetermined_and_open_the_circuit` →
  `should_stay_undetermined_and_open_the_circuit_on_repeated_provider_errors`
  (kept whole).

### Success Criteria:

#### Automated Verification:

- Compiles: `JAVA_HOME=$(/usr/libexec/java_home -v 21) ./mvnw -q -o test-compile`
- Targeted classes pass: `JAVA_HOME=$(/usr/libexec/java_home -v 21) ./mvnw -q -o test -Dtest='CouponServiceTest,CouponControllerTest,IpApiGeoIpResolverTest'`
- Full unit suite passes: `JAVA_HOME=$(/usr/libexec/java_home -v 21) ./mvnw -q -o test`
- No production sources changed: `git status --porcelain src/main` is empty

#### Manual Verification:

- All three files use `should_` names + `@DisplayName`; markers match the convention.
- `CouponControllerTest` — `// when` and `// then` are genuinely separate statements
  in every test.
- `CouponServiceTest` — stub/exercise/verify order is unchanged from the original.
- Diff review confirms no assertion lost; the geo-IP split preserves both the
  empty-result and the `httpCalls()==0` checks for each IP family.

**Implementation Note**: After automated verification passes, pause for human
confirmation before starting Phase 3.

---

## Phase 3: Integration tests

### Overview

Convert `CouponApiIT`, `CouponRedemptionApiIT`, `CouponRedemptionCountryIT`. Each
flow stays one `@Test`; every step inside gets a marker. Requires Docker for
Testcontainers.

### Changes Required:

#### 1. `CouponApiIT`

**File**: `src/test/java/com/example/coupons/CouponApiIT.java`

**Intent**: Rename the flow, add `@DisplayName`, label each step (create → fetch →
miss → duplicate) with `// when` / `// then` / `// and`. Keep `@BeforeEach clean`,
the `json(...)` helper, and the class javadoc.

**Contract**: `creates_fetches_case_insensitively_and_404s_on_a_miss` →
`should_create_fetch_case_insensitively_and_404_on_a_miss`. Existing inline comment
(`// a code that differs only in case is a duplicate…`) becomes the `// and` step
label or sits under it.

#### 2. `CouponRedemptionApiIT`

**File**: `src/test/java/com/example/coupons/CouponRedemptionApiIT.java`

**Intent**: Rename the ladder, add `@DisplayName`, label each rung (1st redeem → 2nd
redeem → limit reached → already redeemed → not found) with `// when` / `// then` /
`// and`. Keep `@BeforeEach` (truncate + create `rush`), `redeem(...)`, `json(...)`.

**Contract**: `redemption_ladder_produces_a_distinct_outcome_for_each_case` →
`should_produce_a_distinct_outcome_for_each_rung_of_the_redemption_ladder`. The
`@BeforeEach`'s coupon creation is the flow's `// given` (noted in a comment at the
top of the test body, since the arrange lives in the fixture).

#### 3. `CouponRedemptionCountryIT`

**File**: `src/test/java/com/example/coupons/CouponRedemptionCountryIT.java`

**Intent**: Rename both tests, add `@DisplayName`, add markers. The country flow is a
ladder (allowed → blocked → undetermined) — label each step. The no-country test is
a single scenario — plain given/when/then. Keep helpers and `@Autowired StubGeoIpResolver`.

**Contract**:
- `a_country_restricted_coupon_enforces_the_resolved_country` →
  `should_enforce_the_resolved_country_for_a_country_restricted_coupon`; each
  `geoIp.next = …` + `redeem(...)` + assert triple is a `// given` / `// when` /
  `// then` (or `// and`) step.
- `creating_a_coupon_without_a_country_is_rejected` →
  `should_reject_creating_a_coupon_without_a_country`.

### Success Criteria:

#### Automated Verification:

- Full verify passes (unit + IT): `JAVA_HOME=$(/usr/libexec/java_home -v 21) ./mvnw -q -o verify`
- Failsafe still runs exactly the 3 IT classes / 4 IT methods (count unchanged):
  check `target/failsafe-reports` summary shows `Tests run: 4`
- No production sources changed: `git status --porcelain src/main` is empty
- Only the 8 intended test files differ: `git status --porcelain src/test` lists
  only the 8 behavioral classes (no `ArchitectureTest`, no support classes)

#### Manual Verification:

- Each IT flow is still a single `@Test` telling the same end-to-end story, now with
  visible `// given` / `// when` / `// then` / `// and` step boundaries.
- `@DisplayName` sentences read naturally.
- Diff review confirms no HTTP call, status assertion, or `problem+json` `code`
  assertion was dropped or reordered.
- App behaviour unaffected — ITs prove this by passing unchanged.

**Implementation Note**: After automated verification passes, pause for final human
confirmation.

---

## Testing Strategy

### Unit Tests:

- The refactor *is* the unit-test change. The safety net is that the pre-existing
  suite and the post-refactor suite assert the same things — enforced by
  `./mvnw test` staying green and by per-phase diff review.
- Watch for accidentally-silenced assertions when splitting: every
  `assertThatThrownBy` / `assertThat` from a bundled test must land in exactly one
  new method.

### Integration Tests:

- `./mvnw verify` runs the 3 Testcontainers ITs unchanged in count; green = the
  end-to-end flows still hold.

### Manual Testing Steps:

1. `git diff --stat` — exactly 8 files under `src/test/`, 0 under `src/main/`.
2. Open one converted domain file and one converted IT; confirm marker style and
   `@DisplayName` readability against the convention.
3. In the IDE, run one converted class and confirm the test-run tree shows the
   `@DisplayName` sentences.
4. `git diff` scan for any `assertThat` that appears in the `-` lines but not the
   `+` lines.

## Performance Considerations

None. More `@Test` methods (~21 → ~43 unit) add negligible runtime — these are
in-memory value-object and mock tests. IT count and Testcontainers bootstrap cost
are unchanged.

## Migration Notes

Not applicable — no data, no API, no deployed artifact.

## References

- Naming / convention decisions: `context/changes/test-gwt-style/plan-brief.md`
- Convention precedent in-repo: none — this establishes it. Closest prior style is
  the current snake_case behavior naming in `src/test/java/com/example/coupons/domain/model/*`.
- Files to convert: `src/test/java/com/example/coupons/{domain/model/CouponTest,
  domain/model/CouponCodeTest, domain/model/CountryTest, domain/model/CouponRedemptionTest,
  domain/model/UsageLimitTest, application/CouponServiceTest, api/web/CouponControllerTest,
  infrastructure/geoip/IpApiGeoIpResolverTest}.java` + the 3 root `*IT.java`.

## Progress

> Convention: `- [ ]` pending, `- [x]` done. Append ` — <commit sha>` when a step lands. Do not rename step titles. See `references/progress-format.md`.

### Phase 1: Domain unit tests

#### Automated

- [x] 1.1 Compiles: `./mvnw -q -o test-compile`
- [x] 1.2 Domain tests pass: `./mvnw -q -o test -Dtest='com.example.coupons.domain.model.*'`
- [x] 1.3 Full unit suite passes: `./mvnw -q -o test`
- [x] 1.4 No production sources changed: `git status --porcelain src/main` is empty
- [x] 1.5 `ArchitectureTest` untouched: `git diff --quiet -- src/test/java/com/example/coupons/architecture/ArchitectureTest.java`

#### Manual

- [x] 1.6 Every `@Test` in the 5 files is `should_…` with a `@DisplayName`
- [x] 1.7 Diff review: every original assertion lands in exactly one split test, none weakened
- [x] 1.8 Marker style matches the convention (lowercase, empty phases omitted, `// and` continuations)
- [x] 1.9 `@DisplayName` sentences read naturally in the IDE test view

### Phase 2: Application, web-slice & geo-IP tests

#### Automated

- [x] 2.1 Compiles: `./mvnw -q -o test-compile`
- [x] 2.2 Targeted classes pass: `./mvnw -q -o test -Dtest='CouponServiceTest,CouponControllerTest,IpApiGeoIpResolverTest'`
- [x] 2.3 Full unit suite passes: `./mvnw -q -o test`
- [x] 2.4 No production sources changed: `git status --porcelain src/main` is empty

#### Manual

- [x] 2.5 All three files use `should_` names + `@DisplayName`; markers match the convention
- [x] 2.6 `CouponControllerTest` — `// when` and `// then` are separate statements in every test
- [x] 2.7 `CouponServiceTest` — stub/exercise/verify order unchanged from the original
- [x] 2.8 Diff review: no assertion lost; geo-IP split keeps both empty-result and `httpCalls()==0` per IP family

### Phase 3: Integration tests

#### Automated

- [x] 3.1 Full verify passes: `./mvnw -q -o verify`
- [x] 3.2 Failsafe still runs 3 IT classes / 4 IT methods (`target/failsafe-reports` shows `Tests run: 4`)
- [x] 3.3 No production sources changed: `git status --porcelain src/main` is empty
- [x] 3.4 Only the 8 intended test files differ under `src/test` (no `ArchitectureTest`, no support classes)

#### Manual

- [x] 3.5 Each IT flow is still one `@Test` telling the same story, with visible step markers
- [x] 3.6 `@DisplayName` sentences read naturally
- [x] 3.7 Diff review: no HTTP call, status assertion, or `problem+json` code assertion dropped or reordered
