package com.example.coupons.infrastructure.persistence;

import com.example.coupons.application.port.CouponRedemptionRepository;
import com.example.coupons.domain.exception.AlreadyRedeemedException;
import com.example.coupons.domain.model.CouponRedemption;
import com.example.coupons.infrastructure.persistence.entity.CouponRedemptionEntity;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Repository;

@Repository
class CouponRedemptionPersistenceAdapter implements CouponRedemptionRepository {

    private final CouponRedemptionJpaRepository jpa;

    CouponRedemptionPersistenceAdapter(CouponRedemptionJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public void insert(CouponRedemption redemption) {
        try {
            jpa.saveAndFlush(new CouponRedemptionEntity(
                    null,
                    redemption.couponId(),
                    redemption.userId(),
                    redemption.redeemedAt(),
                    redemption.resolvedCountry().value()));
        } catch (DataIntegrityViolationException ex) {
            throw new AlreadyRedeemedException(redemption.couponId(), redemption.userId());
        }
    }
}
