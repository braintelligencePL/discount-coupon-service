package com.example.coupons.infrastructure.persistence;

import com.example.coupons.infrastructure.persistence.entity.CouponEntity;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface CouponJpaRepository extends JpaRepository<CouponEntity, Long> {

    Optional<CouponEntity> findByCode(String code);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            UPDATE CouponEntity c
               SET c.currentUses = c.currentUses + 1
             WHERE c.id = :id
               AND c.currentUses < c.maxUses
            """)
    int incrementUsageIfBelowLimit(@Param("id") long couponId);
}
