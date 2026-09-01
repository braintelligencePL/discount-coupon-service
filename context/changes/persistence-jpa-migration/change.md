---
change_id: persistence-jpa-migration
title: Migrate persistence to JPA / Hibernate + rename persistence types
status: implementing
created: 2026-09-01
updated: 2026-09-01
archived_at: null
---

## Notes

Replace Spring Data JDBC with Spring Data JPA / Hibernate and rename the persistence
layer into a `persistence/entity/` subpackage — one change. Domain, ports, application,
API, transaction model and Liquibase schema are untouched; behaviour is proven
identical by the existing Testcontainers ITs.

Key decisions (from the `/10x-plan` questioning):

- Full replace of the starter (not coexist).
- Keep the atomic conditional `UPDATE ... WHERE current_uses < max_uses` — as a
  `@Modifying` JPQL bulk update with `clearAutomatically` + `flushAutomatically`.
- `spring.jpa.hibernate.ddl-auto=validate` (Liquibase still owns the schema).
- `spring.jpa.open-in-view=false`.
- Entities via Lombok (`@Getter/@Setter/@NoArgsConstructor/@AllArgsConstructor`), no `@Data`.
- Names: `CouponEntity` / `CouponRedemptionEntity` (in `persistence/entity/`),
  `CouponJpaRepository` / `CouponRedemptionJpaRepository`,
  `CouponPersistenceAdapter` / `CouponRedemptionPersistenceAdapter`, `UniqueViolations`.
- No new test slices — rely on the existing ITs.
