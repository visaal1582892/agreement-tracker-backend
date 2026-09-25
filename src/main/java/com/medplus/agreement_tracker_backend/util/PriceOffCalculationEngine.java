package com.medplus.agreement_tracker_backend.util;

import com.medplus.agreement_tracker_backend.enums.PriceOffDiscountType;
import com.medplus.agreement_tracker_backend.exception.BusinessException;

import java.math.BigDecimal;
import java.math.RoundingMode;

public final class PriceOffCalculationEngine {

    private static final int SCALE = 4;
    private static final RoundingMode ROUNDING = RoundingMode.HALF_UP;

    private PriceOffCalculationEngine() {}

    public record CalculatedFields(
            BigDecimal marginPercent,
            BigDecimal finalOffer,
            BigDecimal percentOff,
            BigDecimal finalMarginPercent
    ) {}

    public static BigDecimal calculateCreditNote(
            PriceOffDiscountType discountType,
            int totalQty,
            BigDecimal mrp,
            BigDecimal baseOffer) {
        return PriceOffCalculationUtil.calculateCreditNote(totalQty, baseOffer, mrp, null, discountType);
    }

    public static CalculatedFields calculate(
            PriceOffDiscountType discountType,
            BigDecimal cp,
            BigDecimal mrp,
            BigDecimal baseOffer,
            BigDecimal medplusContribution) {
        validatePositive(mrp, "MRP");
        validatePositive(cp, "CP");
        BigDecimal safeBaseOffer = baseOffer != null ? baseOffer : BigDecimal.ZERO;
        if (safeBaseOffer.signum() < 0) {
            throw new BusinessException("Base Offer cannot be negative");
        }
        BigDecimal contribution = medplusContribution != null ? medplusContribution : BigDecimal.ZERO;
        if (contribution.signum() < 0) {
            throw new BusinessException("Medplus Contribution cannot be negative");
        }

        BigDecimal margin = mrp.subtract(cp).divide(mrp, SCALE, ROUNDING).multiply(BigDecimal.valueOf(100)).setScale(SCALE, ROUNDING);
        BigDecimal finalOffer = safeBaseOffer.add(contribution).setScale(SCALE, ROUNDING);

        BigDecimal percentOff;
        if (discountType == PriceOffDiscountType.DISC_PERCENT) {
            percentOff = finalOffer;
        } else {
            percentOff = finalOffer.divide(mrp, SCALE, ROUNDING).multiply(BigDecimal.valueOf(100)).setScale(SCALE, ROUNDING);
        }

        BigDecimal finalMargin = PriceOffCalculationUtil.calculateFinalMarginPercent(
                mrp, cp, contribution, discountType);

        return new CalculatedFields(margin, finalOffer, percentOff, finalMargin);
    }

    private static void validatePositive(BigDecimal value, String label) {
        if (value == null || value.signum() <= 0) {
            throw new BusinessException(label + " must be greater than zero");
        }
    }
}
