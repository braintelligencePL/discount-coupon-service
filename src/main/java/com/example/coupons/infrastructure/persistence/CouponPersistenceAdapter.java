package com.example.coupons.infrastructure.persistence;

import com.example.coupons.application.port.CouponRepository;
import com.example.coupons.domain.exception.DuplicateCouponCodeException;
import com.example.coupons.domain.model.Coupon;
import com.example.coupons.domain.model.CouponCode;
import com.example.coupons.domain.model.Country;
import com.example.coupons.domain.model.UsageLimit;
import com.example.coupons.infrastructure.persistence.entity.CouponEntity;
import java.util.Optional;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Repository;

@Repository
class CouponPersistenceAdapter implements CouponRepository {

    private final CouponJpaRepository jpa;

    CouponPersistenceAdapter(CouponJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public Coupon save(Coupon coupon) {
        try {
            return toDomain(jpa.saveAndFlush(toNewEntity(coupon)));
        } catch (DataIntegrityViolationException ex) {
            throw new DuplicateCouponCodeException(coupon.code().value());
        }
    }

    @Override
    public Optional<Coupon> findByCode(CouponCode code) {
        return jpa.findByCode(code.value())
                .map(CouponPersistenceAdapter::toDomain);
    }

    @Override
    public int incrementUsageIfBelowLimit(CouponCode code) {
        return jpa.incrementUsageIfBelowLimit(code.value());
    }

    private static CouponEntity toNewEntity(Coupon coupon) {
        return new CouponEntity(
                null,
                coupon.code().value(),
                coupon.createdAt(),
                coupon.maxUses().value(),
                coupon.currentUses(),
                coupon.country().value());
    }

    private static Coupon  toDomain(CouponEntity entity) {
        return new Coupon(
                new CouponCode(entity.getCode()),
                entity.getCreatedAt(),
                UsageLimit.of(entity.getMaxUses()),
                entity.getCurrentUses(),
                new Country(entity.getCountry()));
    }
}
