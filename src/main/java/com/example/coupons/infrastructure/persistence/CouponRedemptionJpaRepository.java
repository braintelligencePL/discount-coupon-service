package com.example.coupons.infrastructure.persistence;

import com.example.coupons.infrastructure.persistence.entity.CouponRedemptionEntity;
import org.springframework.data.jpa.repository.JpaRepository;

interface CouponRedemptionJpaRepository extends JpaRepository<CouponRedemptionEntity, Long> {
}
