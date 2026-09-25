package com.medplus.agreement_tracker_backend.service.impl;

import com.medplus.agreement_tracker_backend.dto.request.PurchaseAggregationRequest;
import com.medplus.agreement_tracker_backend.dto.response.MonthlyCalculationDto;
import com.medplus.agreement_tracker_backend.dto.response.RevenueRecognitionMonthDto;
import com.medplus.agreement_tracker_backend.dto.response.RevenueRecognitionPreviewResponse;
import com.medplus.agreement_tracker_backend.entity.AgreementActualMetric;
import com.medplus.agreement_tracker_backend.entity.AgreementMonthlyPayableRollup;
import com.medplus.agreement_tracker_backend.entity.AgreementVersion;
import com.medplus.agreement_tracker_backend.entity.RevenueRecognitionSettings;
import com.medplus.agreement_tracker_backend.enums.CalculationBasis;
import com.medplus.agreement_tracker_backend.enums.MetricType;
import com.medplus.agreement_tracker_backend.repository.AgreementActualMetricRepository;
import com.medplus.agreement_tracker_backend.repository.AgreementMonthlyPayableRollupRepository;
import com.medplus.agreement_tracker_backend.repository.AgreementVersionRepository;
import com.medplus.agreement_tracker_backend.repository.CommercialCalculationDao;
import com.medplus.agreement_tracker_backend.repository.RevenueRecognitionSettingsRepository;
import com.medplus.agreement_tracker_backend.service.AgreementPurchaseScopeResolver;
import com.medplus.agreement_tracker_backend.service.RevenueRecognitionCalculationService;
import com.medplus.agreement_tracker_backend.service.RevenueRecognitionPreviewService;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationContext;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class RevenueRecognitionCalculationServiceImpl implements RevenueRecognitionCalculationService {

    private final AgreementVersionRepository agreementVersionRepository;
    private final RevenueRecognitionPreviewService previewService;
    private final AgreementMonthlyPayableRollupRepository rollupRepository;
    private final RevenueRecognitionSettingsRepository settingsRepository;
    private final AgreementPurchaseScopeResolver scopeResolver;
    private final CommercialCalculationDao commercialCalculationDao;
    private final AgreementActualMetricRepository actualMetricRepository;
    private final EntityManager entityManager;
    private final ApplicationContext applicationContext;
    private final Executor revenueCalculationExecutor;

    private volatile boolean abortJob = false;

    @Override
    public void calculateAndStoreRevenue(List<Integer> monthKeys, Long supplierId, Long agreementId) {
        if (monthKeys == null || monthKeys.isEmpty()) return;

        log.info("Starting revenue recognition calculation for month keys: {}", monthKeys);

        int minKey = monthKeys.stream().min(Integer::compareTo).orElse(monthKeys.get(0));
        int maxKey = monthKeys.stream().max(Integer::compareTo).orElse(monthKeys.get(0));
        
        LocalDate rangeStart = LocalDate.of(minKey / 100, minKey % 100, 1);
        YearMonth endYm = YearMonth.of(maxKey / 100, maxKey % 100);
        LocalDate rangeEnd = endYm.atEndOfMonth();

        log.info("Processing agreements active between {} and {}", rangeStart, rangeEnd);

        int batchSize = 50;
        int pageNumber = 0;
        Page<AgreementVersion> agreementPage;
        RevenueRecognitionCalculationServiceImpl self = applicationContext.getBean(RevenueRecognitionCalculationServiceImpl.class);

        AtomicInteger failureCount = new AtomicInteger(0);
        abortJob = false;

        do {
            Pageable pageable = PageRequest.of(pageNumber, batchSize);
            agreementPage = agreementVersionRepository.findActiveVersionsInPeriod(rangeStart, rangeEnd, supplierId, agreementId, pageable);
            
            if (!agreementPage.isEmpty()) {
                List<CompletableFuture<Void>> futures = new ArrayList<>();
                for (AgreementVersion version : agreementPage.getContent()) {
                    CompletableFuture<Void> future = CompletableFuture.supplyAsync(() -> {
                        if (abortJob) {
                            return null;
                        }
                        self.processAgreement(version, monthKeys, failureCount);
                        return null;
                    }, revenueCalculationExecutor);
                    futures.add(future);
                }
                
                CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
                
                if (abortJob) {
                    throw new com.medplus.agreement_tracker_backend.exception.SystemicDatabaseFailureException("Aborted early due to POS database unreachability.");
                }
            }
            
            pageNumber++;
        } while (agreementPage.hasNext() && !abortJob);
        
        log.info("Revenue recognition calculation completed successfully.");
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void processAgreement(AgreementVersion version, List<Integer> monthKeys, AtomicInteger failureCount) {
        try {
            // Check watermark for short-circuit
            Integer earliestOpenMonth = rollupRepository.findEarliestOpenMonth(version.getAgreement().getId());
            if (earliestOpenMonth != null) {
                int maxRequestedMonth = monthKeys.stream().max(Integer::compareTo).orElse(0);
                if (earliestOpenMonth > maxRequestedMonth) {
                    log.info("Skipping agreement ID {} because earliest open month {} is greater than max requested {}", version.getAgreement().getId(), earliestOpenMonth, maxRequestedMonth);
                    return; // Guardrail 2: Short-circuit
                }
            }

            // 1. Resolve Scope
            List<String> productIds = scopeResolver.resolveProductScope(version.getId(), null);
            List<Long> supplierIds = scopeResolver.resolveSupplierScope(version.getId(), null);
            AgreementPurchaseScopeResolver.ResolvedGeoFilter geoFilter = scopeResolver.resolveGeoFilter(version, null, null);

            // 2. Rule B: Lookback Trap logic
            LocalDate calcStart = version.getStartDate();
            if (earliestOpenMonth != null) {
                YearMonth openYm = YearMonth.of(earliestOpenMonth / 100, earliestOpenMonth % 100);
                YearMonth fyStart = com.medplus.agreement_tracker_backend.util.DynamicFinancialYearPeriodGenerator.alignToFinancialYearStart(openYm, 4);
                if (fyStart.atDay(1).isAfter(calcStart)) {
                    calcStart = fyStart.atDay(1);
                }
            }
            LocalDate calcEnd = version.getExpiryDate() != null ? version.getExpiryDate() : LocalDate.now().plusYears(1);

            // Fetch Aggregated Calculations (ONCE per agreement)
            List<MonthlyCalculationDto> dtos = commercialCalculationDao.fetchAggregatedCalculations(
                    supplierIds,
                    productIds,
                    null,
                    geoFilter.stateCodes(),
                    calcStart,
                    calcEnd,
                    version.getCalculationBasis() == CalculationBasis.VENDOR_INVOICE
            );

            Map<Integer, BigDecimal> monthlyTotals = new HashMap<>();
            for (MonthlyCalculationDto dto : dtos) {
                monthlyTotals.merge(dto.periodMonth(), dto.totalNetValue(), BigDecimal::add);
            }

            // 3. Upsert AgreementActualMetric with Batching and Uniqueness Map (Guardrail 4)
            int minKeyToFetch = earliestOpenMonth != null ? earliestOpenMonth : (calcStart.getYear() * 100 + calcStart.getMonthValue());
            List<AgreementActualMetric> existingMetricsList = actualMetricRepository.findByAgreementVersionIdAndMetricTypeStartingFromMonth(
                    version.getId(), MetricType.PURCHASE, minKeyToFetch);
            Map<Integer, AgreementActualMetric> existingMetricsMap = existingMetricsList.stream()
                    .collect(Collectors.toMap(m -> m.getCalendarYear() * 100 + m.getCalendarMonth(), m -> m));

            List<AgreementActualMetric> metricsToSave = new ArrayList<>();
            YearMonth currentYm = YearMonth.from(calcStart);
            YearMonth endYm = YearMonth.from(calcEnd);
            
            while (!currentYm.isAfter(endYm)) {
                int monthKey = currentYm.getYear() * 100 + currentYm.getMonthValue();
                
                // Rule C: Write Protection
                if (earliestOpenMonth != null && monthKey < earliestOpenMonth) {
                    currentYm = currentYm.plusMonths(1);
                    continue; // Skip LOCKED
                }

                BigDecimal actualValue = monthlyTotals.getOrDefault(monthKey, BigDecimal.ZERO);
                AgreementActualMetric metric = existingMetricsMap.getOrDefault(monthKey, new AgreementActualMetric());

                metric.setAgreementVersion(version);
                metric.setCalendarYear(currentYm.getYear());
                metric.setCalendarMonth(currentYm.getMonthValue());
                metric.setMetricType(MetricType.PURCHASE);
                metric.setActualValue(actualValue);

                metricsToSave.add(metric);
                currentYm = currentYm.plusMonths(1);
            }

            // 4. Generate and Save Rollup with Batching
            PurchaseAggregationRequest req = new PurchaseAggregationRequest(
                    new ArrayList<>(), productIds, supplierIds, geoFilter.stateCodes(), geoFilter.cityCodes());

            RevenueRecognitionPreviewResponse previewResponse = previewService.preview(version.getId(), req);

            List<AgreementMonthlyPayableRollup> rollupsToSave = new ArrayList<>();
            for (RevenueRecognitionMonthDto dto : previewResponse.getMonthlyPreviews()) {
                String[] parts = dto.getCalendarMonth().split("-");
                int y = Integer.parseInt(parts[0]);
                int m = Integer.parseInt(parts[1]);
                int rollupKey = y * 100 + m;

                // Rule C: Write Protection
                if (earliestOpenMonth != null && rollupKey < earliestOpenMonth) {
                    continue; // Skip LOCKED
                }

                AgreementMonthlyPayableRollup rollup = rollupRepository
                    .findByAgreementIdAndCalendarYearAndCalendarMonth(version.getAgreement().getId(), y, m)
                    .orElse(new AgreementMonthlyPayableRollup());

                rollup.setAgreementId(version.getAgreement().getId());
                rollup.setAgreementVersionId(version.getId());
                rollup.setSupplierId(version.getInvoiceVendorId());
                rollup.setCalendarYear(y);
                rollup.setCalendarMonth(m);

                String triggeredFreqs = String.join(", ", dto.getTriggeredPeriods());
                rollup.setTriggeredFrequencies(triggeredFreqs);
                rollup.setEarnedAmount(dto.getEarnedPayout());
                rollup.setPaymentInterval(dto.getPaymentInterval());
                rollup.setPayableAmount(dto.getFinalPayableAmount() != null ? dto.getFinalPayableAmount() : BigDecimal.ZERO);
                rollup.setCalculatedAt(LocalDateTime.now());
                rollup.setStatus(com.medplus.agreement_tracker_backend.enums.PeriodStatus.OPEN); // Guardrail 3

                rollupsToSave.add(rollup);
            }
            
            // Execute batch saves
            if (!metricsToSave.isEmpty()) {
                actualMetricRepository.saveAll(metricsToSave);
            }
            if (!rollupsToSave.isEmpty()) {
                rollupRepository.saveAll(rollupsToSave);
            }
            
            actualMetricRepository.flush();
            rollupRepository.flush();
            entityManager.clear();
        } catch (org.springframework.jdbc.CannotGetJdbcConnectionException ex) {
            log.error("Database connection failure processing agreement version ID {}", version.getId(), ex);
            if (failureCount.incrementAndGet() >= 3) {
                abortJob = true;
            }
        } catch (org.springframework.dao.DataAccessResourceFailureException ex) {
            log.error("Data access resource failure processing agreement version ID {}", version.getId(), ex);
            if (failureCount.incrementAndGet() >= 3) {
                abortJob = true;
            }
        } catch (Exception e) {
            // Check nested cause for ConnectException or SocketException
            Throwable cause = e.getCause();
            boolean isDbFailure = false;
            while (cause != null) {
                if (cause instanceof java.net.ConnectException || cause instanceof java.net.SocketException || cause instanceof java.sql.SQLException) {
                    isDbFailure = true;
                    break;
                }
                cause = cause.getCause();
            }
            
            if (isDbFailure) {
                log.error("Systemic network/DB failure processing agreement version ID {}", version.getId(), e);
                if (failureCount.incrementAndGet() >= 3) {
                    abortJob = true;
                }
            } else {
                log.error("Failed to calculate revenue recognition for Agreement Version ID {}", version.getId(), e);
            }
        }
    }

    @Override
    @Transactional
    public void runScheduledTask() {
        RevenueRecognitionSettings settings = settingsRepository.findById(1L).orElse(null);
        if (settings == null || !settings.getIsEnabled()) {
            log.info("Revenue recognition scheduler is disabled or not configured.");
            return;
        }

        settings.setCurrentStatus("RUNNING");
        settingsRepository.save(settings);

        try {
            LocalDate now = LocalDate.now(ZoneId.of(settings.getTimeZone()));
            YearMonth currentMonth = YearMonth.from(now);
            
            List<Integer> monthKeys = new ArrayList<>();
            int lookback = settings.getLookbackWindowMonths();
            for (int i = 0; i < lookback; i++) {
                YearMonth ym = currentMonth.minusMonths(i);
                monthKeys.add(ym.getYear() * 100 + ym.getMonthValue());
            }

            calculateAndStoreRevenue(monthKeys, null, null);

            settings.setLastSuccessfulRun(LocalDateTime.now());
            settings.setCurrentStatus("IDLE");
        } catch (Exception e) {
            log.error("Scheduled revenue recognition task failed", e);
            settings.setLastFailedRun(LocalDateTime.now());
            settings.setCurrentStatus("FAILED");
        } finally {
            settingsRepository.save(settings);
        }
    }
}

