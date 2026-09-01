---
change_id: simplify-project
title: Trim the coupon-service to a leaner, still production-credible form
status: impl_reviewed
created: 2026-08-31
updated: 2026-08-31
archived_at: null
---

## Notes

Audit outcome: the project is 51 main Java files / ~1,659 lines for a 3-endpoint
service. Simplify without losing the design signals the recruitment task grades
(framework-free domain, ports, hexagonal layout). Decisions captured in
`plan-brief.md`.
