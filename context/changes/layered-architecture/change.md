---
change_id: layered-architecture
title: Restructure the coupon-service from hexagonal into a pragmatic 4-layer architecture
status: implementing
created: 2026-09-01
updated: 2026-09-01
archived_at: null
---

## Notes

Follow-on to `simplify-project` (which deliberately kept the hexagonal `adapter/*`
split out of scope). Directive: pragmatic layered architecture — `api` → `application`
→ `domain`, with `infrastructure` implementing the ports Application owns. Keep it
simple; no unnecessary interfaces, mappers, or layers.

Pure structural refactor: no behaviour, API-contract, or schema change. The 28-test
suite is the equivalence check. Delivered as working-tree changes — **no commits**
(user commits, or not).

Phase 4 (added mid-implementation): moves the inbound HTTP glue
(`ApiExceptionHandler`, `ClientIpResolver`, `CorrelationIdFilter`) from `api/support`
into `infrastructure/web`, and reworks the `ArchitectureTest` layering rule into an
inbound (`api` + `infrastructure.web`) / sealed-outbound (`persistence` + `geoip`)
carve-out. Also folds in: OpenAPI annotations moved to `CouponApi` /
`CouponRedemptionApi` interfaces (leaner controllers).

Phase 5 (added mid-implementation): moves the application boundary records into
`application/dto` (leaving `application/` root as just `CouponService`) and trims
the two input names — `CreateCouponCommand` → `CreateCoupon`, `RedeemCouponCommand`
→ `RedeemCoupon` (`RedemptionResult` kept as-is).

`README2.md` is deleted in the working tree (done by the user, outside these phases).

Decisions captured in `plan-brief.md`.
