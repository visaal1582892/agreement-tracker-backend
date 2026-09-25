package com.medplus.agreement_tracker_backend.service.impl;

import com.medplus.agreement_tracker_backend.entity.AgreementSlab;
import com.medplus.agreement_tracker_backend.entity.AgreementTimePeriod;
import com.medplus.agreement_tracker_backend.entity.AgreementTimePeriodMonth;
import com.medplus.agreement_tracker_backend.entity.AgreementVersion;
import com.medplus.agreement_tracker_backend.enums.PayoutFrequency;
import com.medplus.agreement_tracker_backend.repository.AgreementTimePeriodRepository;
import com.medplus.agreement_tracker_backend.service.AgreementTimePeriodResolutionService;
import com.medplus.agreement_tracker_backend.util.DynamicFinancialYearPeriodGenerator;
import com.medplus.agreement_tracker_backend.util.TimePeriodDimensions;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class AgreementTimePeriodResolutionServiceImpl implements AgreementTimePeriodResolutionService {

    private final AgreementTimePeriodRepository timePeriodRepository;

    @Override
    @Transactional(propagation = Propagation.REQUIRED, readOnly = false, rollbackFor = Exception.class)
    public AgreementTimePeriod resolveOrCreatePeriod(String name, PayoutFrequency frequency, Long userId) {
        return resolveOrCreatePeriod(name, frequency, userId, null, LocalDate.now());
    }

    @Transactional(propagation = Propagation.REQUIRED, readOnly = false, rollbackFor = Exception.class)
    public AgreementTimePeriod resolveOrCreatePeriod(
            String name,
            PayoutFrequency frequency,
            Long userId,
            Integer financialYearStartMonthOverride,
            LocalDate anchorDate) {
        int financialYearStartMonth = DynamicFinancialYearPeriodGenerator.resolveStartMonth(
                financialYearStartMonthOverride);
        List<YearMonth> months = TimePeriodDimensions.includedMonthsFromName(
                name, frequency, financialYearStartMonth, anchorDate);
        return resolveOrCreatePeriod(
                new DynamicFinancialYearPeriodGenerator.PeriodSpec(name, frequency, months),
                userId);
    }

    @Transactional(propagation = Propagation.REQUIRED, readOnly = false, rollbackFor = Exception.class)
    public AgreementTimePeriod resolveOrCreatePeriod(
            DynamicFinancialYearPeriodGenerator.PeriodSpec spec,
            Long userId) {
        if (spec == null || spec.months() == null || spec.months().isEmpty()) {
            throw new IllegalArgumentException("Period spec requires included months");
        }
        return findByIncludedMonths(spec.frequency(), spec.months())
                .map(existing -> synchronizePeriodName(existing, spec.name(), userId))
                .orElseGet(() -> savePeriod(spec, userId));
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRED, readOnly = false, rollbackFor = Exception.class)
    public AgreementTimePeriod canonicalizePeriod(
            AgreementTimePeriod period,
            AgreementVersion version,
            Integer financialYearStartMonthOverride,
            Long userId) {
        if (period == null || period.getPeriodFrequency() == null) {
            return period;
        }
        if (version.getStartDate() == null || version.getExpiryDate() == null) {
            return period;
        }
        int financialYearStartMonth = DynamicFinancialYearPeriodGenerator.resolveStartMonth(
                financialYearStartMonthOverride != null
                        ? financialYearStartMonthOverride
                        : version.getFinancialYearStartMonth());
        List<DynamicFinancialYearPeriodGenerator.PeriodSpec> specs =
                DynamicFinancialYearPeriodGenerator.generatePeriodSpecs(
                        period.getPeriodFrequency(),
                        version.getStartDate(),
                        version.getExpiryDate(),
                        financialYearStartMonth);

        List<YearMonth> periodMonths = TimePeriodDimensions.sortedIncludedMonths(period);
        for (DynamicFinancialYearPeriodGenerator.PeriodSpec spec : specs) {
            if (spec.months().equals(periodMonths)) {
                return resolveOrCreatePeriod(spec, userId);
            }
        }
        return period;
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRED, readOnly = false, rollbackFor = Exception.class)
    public List<AgreementTimePeriod> resolvePeriodsForSlab(AgreementVersion version, AgreementSlab slab, Long userId) {
        return resolvePeriodsForSlab(version, slab, userId, null);
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRED, readOnly = false, rollbackFor = Exception.class)
    public List<AgreementTimePeriod> resolvePeriodsForSlab(
            AgreementVersion version,
            AgreementSlab slab,
            Long userId,
            Integer financialYearStartMonthOverride) {
        if (version.getStartDate() == null || version.getExpiryDate() == null || slab.getPayoutFrequency() == null) {
            return List.of();
        }
        int financialYearStartMonth = DynamicFinancialYearPeriodGenerator.resolveStartMonth(
                financialYearStartMonthOverride != null
                        ? financialYearStartMonthOverride
                        : version.getFinancialYearStartMonth());
        List<DynamicFinancialYearPeriodGenerator.PeriodSpec> specs =
                DynamicFinancialYearPeriodGenerator.generatePeriodSpecs(
                        slab.getPayoutFrequency(),
                        version.getStartDate(),
                        version.getExpiryDate(),
                        financialYearStartMonth);

        List<AgreementTimePeriod> periods = new ArrayList<>();
        for (DynamicFinancialYearPeriodGenerator.PeriodSpec spec : specs) {
            periods.add(resolveOrCreatePeriod(spec, userId));
        }
        periods.sort(TimePeriodDimensions.chronologicalComparator());
        return periods.stream()
                .filter(period -> periodWithinContract(period, version))
                .toList();
    }

    @Override
    public boolean periodWithinContract(AgreementTimePeriod period, AgreementVersion version) {
        if (version.getStartDate() == null || version.getExpiryDate() == null) {
            return true;
        }
        if (period.getPeriodFrequency() == null
                || period.getIncludedMonths() == null
                || period.getIncludedMonths().isEmpty()) {
            return true;
        }

        YearMonth periodStart = TimePeriodDimensions.periodStart(period);
        YearMonth periodEnd = TimePeriodDimensions.periodEnd(period);
        YearMonth contractStart = YearMonth.from(version.getStartDate());
        YearMonth contractEnd = YearMonth.from(version.getExpiryDate());

        return !periodEnd.isBefore(contractStart) && !periodStart.isAfter(contractEnd);
    }

    private java.util.Optional<AgreementTimePeriod> findByIncludedMonths(
            PayoutFrequency frequency,
            List<YearMonth> months) {
        List<AgreementTimePeriod> candidates = frequency != null
                ? timePeriodRepository.findByPeriodFrequency(frequency)
                : timePeriodRepository.findAll();
        return candidates.stream()
                .filter(period -> TimePeriodDimensions.sameIncludedMonths(period, months))
                .findFirst();
    }

    private AgreementTimePeriod synchronizePeriodName(
            AgreementTimePeriod period,
            String canonicalName,
            Long userId) {
        if (canonicalName == null || canonicalName.equals(period.getName())) {
            return period;
        }
        period.setName(canonicalName);
        period.setUpdatedByUserId(userId);
        return timePeriodRepository.save(period);
    }

    private AgreementTimePeriod savePeriod(
            DynamicFinancialYearPeriodGenerator.PeriodSpec spec,
            Long userId) {
        AgreementTimePeriod period = AgreementTimePeriod.builder()
                .name(spec.name())
                .periodFrequency(spec.frequency())
                .build();
        for (YearMonth yearMonth : spec.months()) {
            period.getIncludedMonths().add(AgreementTimePeriodMonth.builder()
                    .timePeriod(period)
                    .calendarMonth(yearMonth.getMonthValue())
                    .calendarYear(yearMonth.getYear())
                    .build());
        }
        period.setCreatedByUserId(userId);
        return timePeriodRepository.save(period);
    }
}
