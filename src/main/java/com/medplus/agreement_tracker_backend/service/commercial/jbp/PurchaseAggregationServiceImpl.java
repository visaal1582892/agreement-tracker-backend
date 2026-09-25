package com.medplus.agreement_tracker_backend.service.commercial.jbp;

import com.medplus.agreement_tracker_backend.enums.CalculationBasis;
import com.medplus.agreement_tracker_backend.repository.CommercialCalculationDao;
import com.medplus.agreement_tracker_backend.service.commercial.CommercialCalculationContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

@Service
@RequiredArgsConstructor
public class PurchaseAggregationServiceImpl implements PurchaseAggregationService {

    private final CommercialCalculationDao commercialCalculationDao;

    @Override
    public CommercialCalculationDao.PurchaseTotals sumTotals(List<Integer> overlap, CommercialCalculationContext context) {
        if (overlap == null || overlap.isEmpty()) {
            return new CommercialCalculationDao.PurchaseTotals(0L, java.math.BigDecimal.ZERO);
        }

        LocalDate startDate = overlap.stream()
                .map(key -> LocalDate.of(key / 100, key % 100, 1))
                .min(LocalDate::compareTo)
                .orElse(LocalDate.now());

        LocalDate endDate = overlap.stream()
                .map(key -> YearMonth.of(key / 100, key % 100).atEndOfMonth())
                .max(LocalDate::compareTo)
                .orElse(LocalDate.now());

        return commercialCalculationDao.sumTotals(
                overlap,
                context.supplierIds(),
                context.productIds(),
                null,
                context.stateCodes(),
                startDate,
                endDate,
                context.calculationBasis() == CalculationBasis.VENDOR_INVOICE
        );
    }
}
