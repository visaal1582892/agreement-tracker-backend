package com.medplus.agreement_tracker_backend.util;

import com.medplus.agreement_tracker_backend.enums.PriceOffDiscountType;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Locale;

public final class PriceOffCalculationUtil {

    private static final int INTERMEDIATE_SCALE = 4;
    private static final int RESULT_SCALE = 4;
    private static final RoundingMode ROUNDING = RoundingMode.HALF_UP;

    private PriceOffCalculationUtil() {}

    public record DerivedFields(
            int totalQty,
            BigDecimal creditNote,
            BigDecimal marginPercent,
            BigDecimal finalOffer,
            BigDecimal percentOff,
            BigDecimal finalMarginPercent,
            boolean negativeMargin) {}

    public static DerivedFields calculateDerivedFields(
            PriceOffDiscountType discountType,
            String discountTypeLabel,
            BigDecimal cp,
            BigDecimal mrp,
            BigDecimal baseOffer,
            BigDecimal medplusContribution,
            int totalQty) {
        BigDecimal medplus = medplusContribution != null ? medplusContribution : BigDecimal.ZERO;
        PriceOffCalculationEngine.CalculatedFields calculated = PriceOffCalculationEngine.calculate(
                discountType, cp, mrp, baseOffer, medplus);
        BigDecimal creditNote = calculateCreditNote(
                totalQty, baseOffer, mrp, discountTypeLabel, discountType);
        BigDecimal finalMarginPercent = calculateFinalMarginPercent(
                mrp, cp, medplus, discountType);
        boolean negativeMargin = finalMarginPercent.compareTo(BigDecimal.ZERO) < 0;
        return new DerivedFields(
                totalQty,
                creditNote,
                calculated.marginPercent(),
                calculated.finalOffer(),
                calculated.percentOff(),
                finalMarginPercent,
                negativeMargin);
    }

    /**
     * Final margin fraction (stored as 0.13 for 13%), matching Excel:
     * baseMargin = (MRP - CP) / MRP
     * Disc_%: finalMargin = baseMargin - (medplusContribution / 100)
     * Disc_Val: finalMargin = baseMargin - (medplusContribution / MRP)
     * medplusContribution is Column K (Medplus price-off discount; Base Offer excluded).
     */
    public static BigDecimal calculateFinalMarginPercent(
            BigDecimal mrp,
            BigDecimal cp,
            BigDecimal medplusContribution,
            PriceOffDiscountType discountType) {
        if (mrp == null || cp == null || mrp.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO.setScale(RESULT_SCALE, ROUNDING);
        }

        BigDecimal safeDiscount = medplusContribution != null ? medplusContribution : BigDecimal.ZERO;
        BigDecimal baseMargin = mrp.subtract(cp).divide(mrp, INTERMEDIATE_SCALE, ROUNDING).multiply(BigDecimal.valueOf(100));

        BigDecimal finalMargin;
        if (discountType == PriceOffDiscountType.DISC_PERCENT) {
            finalMargin = baseMargin.subtract(safeDiscount);
        } else if (discountType == PriceOffDiscountType.DISC_VAL) {
            finalMargin = baseMargin.subtract(
                    safeDiscount.divide(mrp, INTERMEDIATE_SCALE, ROUNDING).multiply(BigDecimal.valueOf(100)));
        } else {
            finalMargin = baseMargin;
        }

        return finalMargin.setScale(RESULT_SCALE, ROUNDING);
    }

    /**
     * Credit Note business rules:
     * <ul>
     *   <li>DISC_VAL: baseOffer (₹) × totalQty</li>
     *   <li>DISC_PERCENT: ((MRP × baseOffer) / 100) × totalQty — baseOffer is whole-number %</li>
     * </ul>
     */
    public static BigDecimal calculateCreditNote(
            Integer totalQty,
            BigDecimal baseOffer,
            BigDecimal mrp,
            String discountTypeStr,
            PriceOffDiscountType discountTypeEnum) {
        if (totalQty == null || totalQty == 0 || baseOffer == null || baseOffer.signum() == 0) {
            return BigDecimal.ZERO.setScale(RESULT_SCALE, ROUNDING);
        }

        BigDecimal qty = BigDecimal.valueOf(totalQty);
        String type = resolveDiscountTypeKey(discountTypeStr, discountTypeEnum);

        if (isValType(type)) {
            return baseOffer.multiply(qty).setScale(RESULT_SCALE, ROUNDING);
        }

        if (isPercentType(type)) {
            if (mrp == null || mrp.signum() <= 0) {
                return BigDecimal.ZERO.setScale(RESULT_SCALE, ROUNDING);
            }
            BigDecimal baseOfferInRupees = mrp.multiply(baseOffer)
                    .divide(BigDecimal.valueOf(100), INTERMEDIATE_SCALE, ROUNDING);
            return baseOfferInRupees.multiply(qty).setScale(RESULT_SCALE, ROUNDING);
        }

        return BigDecimal.ZERO.setScale(RESULT_SCALE, ROUNDING);
    }

    private static String resolveDiscountTypeKey(String discountTypeStr, PriceOffDiscountType discountTypeEnum) {
        if (discountTypeEnum != null) {
            return discountTypeEnum.name();
        }
        if (discountTypeStr == null || discountTypeStr.isBlank()) {
            return "";
        }
        return discountTypeStr.trim().toUpperCase(Locale.ROOT).replace(' ', '_');
    }

    private static boolean isValType(String type) {
        return type.contains("VAL");
    }

    private static boolean isPercentType(String type) {
        return type.contains("PERCENT") || type.contains("%");
    }
}
