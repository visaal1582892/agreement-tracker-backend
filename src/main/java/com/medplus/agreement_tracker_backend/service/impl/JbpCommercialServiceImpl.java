package com.medplus.agreement_tracker_backend.service.impl;

import com.medplus.agreement_tracker_backend.dto.request.CommitJbpRequest;
import com.medplus.agreement_tracker_backend.dto.request.JbpConfigurationBlockDto;
import com.medplus.agreement_tracker_backend.dto.request.JbpWorkbookRequest;
import com.medplus.agreement_tracker_backend.dto.response.JbpStagedWorkbookDto;
import com.medplus.agreement_tracker_backend.dto.response.JbpStructureHydrationResponse;
import com.medplus.agreement_tracker_backend.dto.response.JbpStagedWorkbookDto.UnpivotedRow;
import com.medplus.agreement_tracker_backend.dto.response.TimePeriodSummaryResponse;
import com.medplus.agreement_tracker_backend.entity.AgreementJbpCommercialPeriod;
import com.medplus.agreement_tracker_backend.entity.AgreementJbpConfiguration;
import com.medplus.agreement_tracker_backend.entity.AgreementSlab;
import com.medplus.agreement_tracker_backend.entity.AgreementTimePeriod;
import com.medplus.agreement_tracker_backend.entity.AgreementVersion;
import com.medplus.agreement_tracker_backend.enums.CommercialStructure;
import com.medplus.agreement_tracker_backend.enums.JbpValueType;
import com.medplus.agreement_tracker_backend.enums.PayoutFrequency;
import com.medplus.agreement_tracker_backend.exception.BusinessException;
import com.medplus.agreement_tracker_backend.exception.IncompleteAgreementException;
import com.medplus.agreement_tracker_backend.exception.ResourceNotFoundException;
import com.medplus.agreement_tracker_backend.repository.AgreementJbpCommercialPeriodRepository;
import com.medplus.agreement_tracker_backend.repository.AgreementJbpConfigurationRepository;
import com.medplus.agreement_tracker_backend.repository.AgreementTimePeriodRepository;
import com.medplus.agreement_tracker_backend.repository.AgreementVersionRepository;
import com.medplus.agreement_tracker_backend.service.AgreementTimePeriodResolutionService;
import com.medplus.agreement_tracker_backend.service.CommercialVersionGuard;
import com.medplus.agreement_tracker_backend.service.JbpCommercialService;
import com.medplus.agreement_tracker_backend.util.JbpConfigurationCollisionValidator;
import com.medplus.agreement_tracker_backend.util.JbpExcelSheetLayout;
import com.medplus.agreement_tracker_backend.util.JbpTemporalReconciliationUtil;
import com.medplus.agreement_tracker_backend.util.TimePeriodDimensions;
import com.medplus.agreement_tracker_backend.util.TimePeriodDisplayFormatter;
import com.medplus.agreement_tracker_backend.util.TimePeriodNameParser;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class JbpCommercialServiceImpl implements JbpCommercialService {

    private static final BigDecimal PERCENT_LIMIT = new BigDecimal("100");

    private final AgreementVersionRepository agreementVersionRepository;
    private final AgreementJbpConfigurationRepository jbpConfigurationRepository;
    private final AgreementJbpCommercialPeriodRepository jbpCommercialPeriodRepository;
    private final AgreementTimePeriodRepository timePeriodRepository;
    private final CommercialVersionGuard commercialVersionGuard;
    private final AgreementTimePeriodResolutionService periodResolutionService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void commitJbpStructure(Long agreementVersionId, CommitJbpRequest request, Long currentUserId) {
        AgreementVersion version = commercialVersionGuard.loadForCommercialMutation(agreementVersionId, currentUserId);
        if (request.stagedWorkbook() == null || request.stagedWorkbook().sheets() == null
                || request.stagedWorkbook().sheets().isEmpty()) {
            throw new IncompleteAgreementException("Staged JBP workbook is required.");
        }

        Map<Long, AgreementJbpConfiguration> configurationsById =
                jbpConfigurationRepository.findByAgreementVersionId(agreementVersionId).stream()
                        .collect(Collectors.toMap(AgreementJbpConfiguration::getId, Function.identity()));

        jbpCommercialPeriodRepository.deleteByAgreementVersionId(agreementVersionId);

        Map<Long, AgreementTimePeriod> periodsById = loadPeriodsForCommit(request);
        List<AgreementJbpCommercialPeriod> entities = new ArrayList<>();
        Set<String> seenCellCoordinates = new HashSet<>();

        for (var sheet : request.stagedWorkbook().sheets()) {
            validateIncreasingTargets(sheet.rows());
            for (var row : sheet.rows()) {
                AgreementTimePeriod period = periodsById.get(row.timePeriodId());
                if (period == null) {
                    throw new ResourceNotFoundException("AgreementTimePeriod", row.timePeriodId());
                }

                AgreementJbpConfiguration configuration = configurationsById.get(row.jbpConfigurationId());
                if (configuration == null) {
                    throw new BusinessException(
                            "JBP configuration " + row.jbpConfigurationId() + " does not belong to this agreement version.");
                }
                boolean isMasterRow = row.subPeriodName() == null;
                String periodLabel = isMasterRow ? row.parentPeriodName() : row.subPeriodName();

                validateCommitThresholds(sheet, row, periodLabel, isMasterRow);
                validateUniqueCellCoordinate(row, periodLabel, sheet.sheetName(), seenCellCoordinates);

                AgreementTimePeriod parentTimePeriod = null;
                if (!isMasterRow && row.parentPeriodId() != null) {
                    parentTimePeriod = periodsById.get(row.parentPeriodId());
                    if (parentTimePeriod == null) {
                        throw new ResourceNotFoundException("AgreementTimePeriod", row.parentPeriodId());
                    }
                }

                entities.add(AgreementJbpCommercialPeriod.builder()
                        .agreementVersion(version)
                        .jbpConfiguration(configuration)
                        .targetType(row.targetType())
                        .target(row.target())
                        .qualifierPercent(row.qualifierPercent() != null ? row.qualifierPercent() : BigDecimal.ZERO)
                        .payoutType(row.payoutType())
                        .payout(row.payout())
                        .maxPurchase(row.maxPurchase())
                        .maxPayout(row.maxPayout())
                        .slabTierNumber(row.slabTierNumber())
                        .timePeriod(period)
                        .parentTimePeriod(parentTimePeriod)
                        .build());
            }
        }

        if (!entities.isEmpty()) {
            jbpCommercialPeriodRepository.saveAll(entities);
        }

        version.setCommercialStructure(CommercialStructure.SLAB);
        version.setCommercialValue(null);
        version.setFlatValueType(null);
        version.setFlatBaselineFrequency(null);
        version.setUpdatedByUserId(currentUserId);
        agreementVersionRepository.save(version);
    }

    @Override
    @Transactional(readOnly = true)
    public JbpStructureHydrationResponse getJbpStructure(Long agreementVersionId, Long currentUserId) {
        agreementVersionRepository.findById(agreementVersionId)
                .orElseThrow(() -> new ResourceNotFoundException("AgreementVersion", agreementVersionId));

        List<JbpConfigurationBlockDto> configurations = jbpConfigurationRepository
                .findHydratedByAgreementVersionId(agreementVersionId).stream()
                .sorted(Comparator.comparing(AgreementJbpConfiguration::getId))
                .map(this::toConfigurationBlockDto)
                .toList();

        List<AgreementJbpCommercialPeriod> commercialPeriods =
                jbpCommercialPeriodRepository.findHydrationRowsByAgreementVersionId(agreementVersionId);

        if (configurations.isEmpty() && commercialPeriods.isEmpty()) {
            throw new ResourceNotFoundException("JBP structure", agreementVersionId);
        }

        Map<String, JbpStagedWorkbookDto> stagedWorkbooks = new HashMap<>();
        if (!commercialPeriods.isEmpty()) {
            // Build global config ID -> config number mapping so sheet names are correct
            java.util.List<Long> allConfigIds = commercialPeriods.stream()
                    .map(p -> p.getJbpConfiguration().getId())
                    .distinct()
                    .sorted()
                    .toList();
            java.util.Map<Long, Integer> globalConfigIdToNumber = new java.util.HashMap<>();
            for (int i = 0; i < allConfigIds.size(); i++) {
                globalConfigIdToNumber.put(allConfigIds.get(i), i + 1);
            }

            // Group commercial periods by config ID and build a staged workbook per config
            Map<Long, List<AgreementJbpCommercialPeriod>> periodsByConfig = commercialPeriods.stream()
                    .collect(Collectors.groupingBy(p -> p.getJbpConfiguration().getId()));
            for (Map.Entry<Long, List<AgreementJbpCommercialPeriod>> entry : periodsByConfig.entrySet()) {
                String configId = String.valueOf(entry.getKey());
                Integer configNumber = globalConfigIdToNumber.get(entry.getKey());
                JbpStagedWorkbookDto workbook = rebuildStagedWorkbookForConfig(entry.getValue(), configNumber);
                stagedWorkbooks.put(configId, workbook);
            }
        }

        return new JbpStructureHydrationResponse(configurations, stagedWorkbooks);
    }

    @Override
    @Transactional(readOnly = false, rollbackFor = Exception.class)
    public List<TimePeriodSummaryResponse> listAvailablePeriods(
            Long agreementVersionId,
            PayoutFrequency frequency,
            Long currentUserId,
            Integer financialYearStartMonth) {
        AgreementVersion version = commercialVersionGuard.loadForCommercialMutation(agreementVersionId, currentUserId);
        if (version.getStartDate() == null || version.getExpiryDate() == null) {
            throw new IncompleteAgreementException("Contract dates must be saved before listing time periods.");
        }

        AgreementSlab probe = AgreementSlab.builder().payoutFrequency(frequency).build();
        return periodResolutionService.resolvePeriodsForSlab(
                        version,
                        probe,
                        currentUserId,
                        financialYearStartMonth).stream()
                .sorted(TimePeriodDimensions.chronologicalComparator())
                .map(period -> {
                    YearMonth earliest = period.earliestIncludedMonth();
                    var boundary = TimePeriodNameParser.resolveBoundary(
                            period.getName(),
                            financialYearStartMonth,
                            TimePeriodDimensions.sortedIncludedMonths(period));
                    return new TimePeriodSummaryResponse(
                            period.getId(),
                            period.getName(),
                            period.getPeriodFrequency() != null ? period.getPeriodFrequency().name() : null,
                            earliest != null ? earliest.getYear() : null,
                            earliest != null ? earliest.getMonthValue() : null,
                            earliest != null ? earliest.getYear() : null,
                            boundary.fyStartYear(),
                            boundary.fyEndYear());
                })
                .toList();
    }

    @Override
    @Transactional(readOnly = false, rollbackFor = Exception.class)
    public List<TimePeriodSummaryResponse> listPeriodsForDateWindow(
            Long sourceVersionId,
            java.time.LocalDate startDate,
            java.time.LocalDate expiryDate,
            PayoutFrequency frequency,
            Long currentUserId,
            Integer financialYearStartMonth) {
        AgreementVersion source = commercialVersionGuard.loadOwnedSourceVersion(sourceVersionId, currentUserId);
        if (startDate == null || expiryDate == null) {
            throw new IncompleteAgreementException("Contract dates are required before listing time periods.");
        }
        if (expiryDate.isBefore(startDate)) {
            throw new BusinessException("Expiry date must be on or after start date.");
        }

        AgreementVersion probe = new AgreementVersion();
        probe.setId(source.getId());
        probe.setAgreement(source.getAgreement());
        probe.setStartDate(startDate);
        probe.setExpiryDate(expiryDate);
        probe.setFinancialYearStartMonth(
                financialYearStartMonth != null ? financialYearStartMonth : source.getFinancialYearStartMonth());

        AgreementSlab slabProbe = AgreementSlab.builder().payoutFrequency(frequency).build();
        return periodResolutionService.resolvePeriodsForSlab(
                        probe,
                        slabProbe,
                        currentUserId,
                        financialYearStartMonth).stream()
                .sorted(TimePeriodDimensions.chronologicalComparator())
                .map(period -> {
                    YearMonth earliest = period.earliestIncludedMonth();
                    var boundary = TimePeriodNameParser.resolveBoundary(
                            period.getName(),
                            financialYearStartMonth,
                            TimePeriodDimensions.sortedIncludedMonths(period));
                    return new TimePeriodSummaryResponse(
                            period.getId(),
                            period.getName(),
                            period.getPeriodFrequency() != null ? period.getPeriodFrequency().name() : null,
                            earliest != null ? earliest.getYear() : null,
                            earliest != null ? earliest.getMonthValue() : null,
                            earliest != null ? earliest.getYear() : null,
                            boundary.fyStartYear(),
                            boundary.fyEndYear());
                })
                .toList();
    }

    @Transactional(rollbackFor = Exception.class)
    public void persistWorkbookMetadata(AgreementVersion version, JbpWorkbookRequest request, Long currentUserId) {
        JbpConfigurationCollisionValidator.validateNoPaymentIntervalOverlap(request.configurations());
        version.setUpdatedByUserId(currentUserId);
        agreementVersionRepository.save(version);
    }

    @Transactional(rollbackFor = Exception.class)
    public void persistConfigurationBlock(
            AgreementJbpConfiguration configuration,
            JbpConfigurationBlockDto config) {
        configuration.setPaymentIntervals(config.paymentIntervals() != null
                ? new ArrayList<>(config.paymentIntervals())
                : new ArrayList<>());
        configuration.setTargetIntervals(config.targetIntervals() != null
                ? new ArrayList<>(config.targetIntervals())
                : new ArrayList<>());
        jbpConfigurationRepository.save(configuration);
    }

    public List<PayoutFrequency> resolveSubFrequencies(List<String> targetIntervals, PayoutFrequency masterFrequency) {
        int masterRank = JbpTemporalReconciliationUtil.frequencyRank(masterFrequency);
        List<PayoutFrequency> subFrequencies = new ArrayList<>();
        for (String raw : targetIntervals) {
            PayoutFrequency frequency = PayoutFrequency.valueOf(raw.trim().toUpperCase());
            if (JbpTemporalReconciliationUtil.frequencyRank(frequency) < masterRank) {
                subFrequencies.add(frequency);
            }
        }
        subFrequencies.sort(Comparator.comparingInt(JbpTemporalReconciliationUtil::frequencyRank).reversed());
        return subFrequencies;
    }

    public PayoutFrequency resolveMasterFrequency(List<String> selectedFrequencies) {
        return selectedFrequencies.stream()
                .map(value -> PayoutFrequency.valueOf(value.trim().toUpperCase()))
                .max(Comparator.comparingInt(JbpTemporalReconciliationUtil::frequencyRank))
                .orElseThrow(() -> new IncompleteAgreementException("Select at least one target frequency."));
    }

    public List<AgreementTimePeriod> resolveSubPeriodsForConfig(
            AgreementVersion version,
            List<AgreementTimePeriod> parentPeriods,
            PayoutFrequency subFrequency,
            Long userId) {
        AgreementSlab probe = AgreementSlab.builder().payoutFrequency(subFrequency).build();
        return periodResolutionService.resolvePeriodsForSlab(version, probe, userId).stream()
                .filter(sub -> JbpTemporalReconciliationUtil.resolveParentPeriod(sub, parentPeriods).isPresent())
                .sorted(TimePeriodDimensions.chronologicalComparator())
                .toList();
    }

    private Map<Long, AgreementTimePeriod> loadPeriodsForCommit(CommitJbpRequest request) {
        Set<Long> periodIds = new HashSet<>();
        for (var sheet : request.stagedWorkbook().sheets()) {
            for (var row : sheet.rows()) {
                periodIds.add(row.timePeriodId());
                if (row.parentPeriodId() != null) {
                    periodIds.add(row.parentPeriodId());
                }
            }
        }
        return timePeriodRepository.findAllById(periodIds).stream()
                .collect(Collectors.toMap(AgreementTimePeriod::getId, Function.identity()));
    }

    private void validateUniqueCellCoordinate(
            UnpivotedRow row,
            String periodLabel,
            String sheetName,
            Set<String> seenCellCoordinates) {
        String coordinate = row.jbpConfigurationId() + "-" + row.slabTierNumber() + "-" + row.timePeriodId();
        if (!seenCellCoordinates.add(coordinate)) {
            throw new BusinessException(String.format(
                    "Duplicate JBP cell on sheet '%s' for %s (%s): configuration %d, tier %d, period %d.",
                    sheetName,
                    periodLabel,
                    row.slabTierLabel(),
                    row.jbpConfigurationId(),
                    row.slabTierNumber(),
                    row.timePeriodId()));
        }
    }

    private void validateHighestParentTargetType(
            JbpStagedWorkbookDto.StagedSheet sheet,
            UnpivotedRow row,
            String periodLabel) {
        // Target type is now hardcoded as ABSOLUTE in the parser for master rows.
        // No validation needed here.
    }

    private void validateCommitThresholds(
            JbpStagedWorkbookDto.StagedSheet sheet,
            UnpivotedRow row,
            String periodLabel,
            boolean isMasterRow) {
        // Validation removed for real-time structures
    }

    private void validateSubPeriodSignificance(String intervalName, UnpivotedRow row) {
        boolean hasPayout = row.payoutType() != null
                && row.payout() != null
                && row.payout().compareTo(BigDecimal.ZERO) > 0;
        boolean hasQualifier = row.qualifierPercent() != null
                && row.qualifierPercent().compareTo(BigDecimal.ZERO) > 0;
        if (hasPayout || hasQualifier) {
            return;
        }
        throw new BusinessException(String.format(
                "Business Rule Violation: Sub-period [%s] has no Payout and a Qualifier of 0%%. "
                        + "Sub-periods must have either a Payout or a Qualifier percentage to be contractually significant.",
                intervalName));
    }

    private void validateIncreasingTargets(List<UnpivotedRow> rows) {
        // Validation removed for real-time structures
    }

    private void validateStoredConfigurationCollisions(
            Long agreementVersionId,
            List<String> selectedFrequencies) {
        // Collision check now done at template generation time via validateNoPaymentIntervalOverlap
    }

    private List<JbpConfigurationBlockDto> loadStoredConfigurations(
            Long agreementVersionId,
            List<String> selectedFrequencies) {
        List<JbpConfigurationBlockDto> configurations = jbpConfigurationRepository
                .findHydratedByAgreementVersionId(agreementVersionId).stream()
                .map(this::toConfigurationBlockDto)
                .toList();
        if (configurations.isEmpty()) {
            throw new IncompleteAgreementException(
                    "Generate the JBP workbook template before committing JBP structure.");
        }
        return configurations;
    }

    private JbpConfigurationBlockDto toConfigurationBlockDto(AgreementJbpConfiguration configuration) {
        return new JbpConfigurationBlockDto(
                String.valueOf(configuration.getId()),
                configuration.getPaymentIntervals() != null ? configuration.getPaymentIntervals() : List.of(),
                configuration.getTargetIntervals() != null ? configuration.getTargetIntervals() : List.of(),
                configuration.getSlabCount());
    }

    private String resolvePeriodName(Long periodId) {
        return timePeriodRepository.findById(periodId)
                .map(AgreementTimePeriod::getName)
                .orElse("ID " + periodId);
    }

    private JbpStagedWorkbookDto rebuildStagedWorkbookForConfig(
            List<AgreementJbpCommercialPeriod> commercialPeriods,
            Integer configNumber) {
        
        // Use the passed-in config number for all periods in this config group
        int effectiveConfigNumber = configNumber != null ? configNumber : 1;
        Map<Long, Integer> configIdToNumber = new java.util.HashMap<>();
        for (AgreementJbpCommercialPeriod period : commercialPeriods) {
            configIdToNumber.put(period.getJbpConfiguration().getId(), effectiveConfigNumber);
        }

        java.util.List<AgreementJbpCommercialPeriod> sortedPeriods = new java.util.ArrayList<>(commercialPeriods);
        sortedPeriods.sort(Comparator
                .comparing((AgreementJbpCommercialPeriod p) -> p.getParentTimePeriod() != null
                                ? p.getParentTimePeriod().earliestIncludedMonth()
                                : p.getTimePeriod().earliestIncludedMonth(),
                        Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(p -> p.getTimePeriod().earliestIncludedMonth(),
                        Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(AgreementJbpCommercialPeriod::getSlabTierNumber,
                        Comparator.nullsLast(Integer::compareTo)));

        Map<String, HydratedSheetBucket> sheetBuckets = new LinkedHashMap<>();
        for (AgreementJbpCommercialPeriod period : sortedPeriods) {
            HydratedSheetBucket bucket = resolveSheetBucket(period, configNumber);
            sheetBuckets.computeIfAbsent(bucket.sheetName(), ignored -> bucket).rows().add(toHydratedRow(period));
        }

        // Extract config number from sheet name (Config{N}-...) for sorting
        java.util.regex.Pattern configPattern = java.util.regex.Pattern.compile("^Config(\\d+)-");
        List<JbpStagedWorkbookDto.StagedSheet> sheets = sheetBuckets.values().stream()
                .sorted(Comparator
                        .comparing((HydratedSheetBucket b) -> {
                            java.util.regex.Matcher m = configPattern.matcher(b.sheetName());
                            return m.find() ? Integer.parseInt(m.group(1)) : Integer.MAX_VALUE;
                        })
                        .thenComparing(HydratedSheetBucket::master).reversed()
                        .thenComparing(bucket -> JbpTemporalReconciliationUtil.frequencyRank(bucket.frequency()),
                                Comparator.reverseOrder()))
                .map(bucket -> {
                    applyFirstInParentGroupFlags(bucket.rows());
                    return new JbpStagedWorkbookDto.StagedSheet(
                            null,
                            bucket.sheetName(),
                            bucket.sheetName(),
                            bucket.frequency().name(),
                            bucket.master(),
                            bucket.rows());
                })
                .toList();

        return new JbpStagedWorkbookDto(sheets, false, null);
    }

    private HydratedSheetBucket resolveSheetBucket(AgreementJbpCommercialPeriod period, int configNumber) {
        AgreementTimePeriod timePeriod = period.getTimePeriod();
        if (period.getParentTimePeriod() == null) {
            // Master rows: use Target_ naming (new convention) for hydration.
            // This aligns with the new generator that creates Target_ sheets.
            PayoutFrequency frequency = timePeriod.getPeriodFrequency();
            String sheetName = JbpExcelSheetLayout.canonicalTargetSheetName(String.valueOf(configNumber), frequency);
            return new HydratedSheetBucket(sheetName, frequency, true, new ArrayList<>());
        }

        // Legacy spread sheets (kept for backward compatibility with existing data).
        PayoutFrequency frequency = timePeriod.getPeriodFrequency();
        String sheetName = JbpExcelSheetLayout.canonicalSpreadSheetName(String.valueOf(configNumber), frequency);
        return new HydratedSheetBucket(sheetName, frequency, false, new ArrayList<>());
    }

    private UnpivotedRow toHydratedRow(AgreementJbpCommercialPeriod period) {
        AgreementTimePeriod timePeriod = period.getTimePeriod();
        AgreementTimePeriod parentTimePeriod = period.getParentTimePeriod();
        boolean masterRow = parentTimePeriod == null;
        Integer fyStartMonth = period.getAgreementVersion() != null
                ? period.getAgreementVersion().getFinancialYearStartMonth()
                : null;
        String parentPeriodName = TimePeriodDisplayFormatter.format(
                masterRow ? timePeriod : parentTimePeriod,
                fyStartMonth);
        Long parentPeriodId = masterRow ? timePeriod.getId() : parentTimePeriod.getId();
        String subPeriodName = masterRow
                ? null
                : TimePeriodDisplayFormatter.formatBase(timePeriod);
        String slabTierLabel = "Slab " + period.getSlabTierNumber();

        return new UnpivotedRow(
                parentPeriodName,
                parentPeriodId,
                subPeriodName,
                timePeriod.getId(),
                period.getSlabTierNumber(),
                period.getJbpConfiguration().getId(),
                slabTierLabel,
                period.getTargetType(),
                period.getTarget(),
                period.getQualifierPercent(),
                period.getPayoutType(),
                period.getPayout(),
                period.getMaxPurchase(),
                period.getMaxPayout(),
                false);
    }


    private void applyFirstInParentGroupFlags(List<UnpivotedRow> rows) {
        String lastParent = null;
        for (int index = 0; index < rows.size(); index++) {
            UnpivotedRow row = rows.get(index);
            boolean firstInGroup = !row.parentPeriodName().equals(lastParent);
            if (firstInGroup != row.firstInParentGroup()) {
                rows.set(index, copyHydratedRow(row, firstInGroup));
            }
            lastParent = row.parentPeriodName();
        }
    }

    private UnpivotedRow copyHydratedRow(UnpivotedRow row, boolean firstInGroup) {
        return new UnpivotedRow(
                row.parentPeriodName(),
                row.parentPeriodId(),
                row.subPeriodName(),
                row.timePeriodId(),
                row.slabTierNumber(),
                row.jbpConfigurationId(),
                row.slabTierLabel(),
                row.targetType(),
                row.target(),
                row.qualifierPercent(),
                row.payoutType(),
                row.payout(),
                row.maxPurchase(),
                row.maxPayout(),
                firstInGroup);
    }

    private record HydratedSheetBucket(
            String sheetName,
            PayoutFrequency frequency,
            boolean master,
            List<UnpivotedRow> rows) {
    }
}
