package com.medplus.agreement_tracker_backend.service.commercial.jbp;

import com.medplus.agreement_tracker_backend.entity.AgreementJbpCommercialPeriod;
import com.medplus.agreement_tracker_backend.entity.AgreementTimePeriod;
import com.medplus.agreement_tracker_backend.entity.AgreementTimePeriodMonth;
import com.medplus.agreement_tracker_backend.enums.JbpValueType;
import com.medplus.agreement_tracker_backend.enums.PayoutFrequency;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

@Service
public class EarnedPayoutCalculationServiceImpl implements EarnedPayoutCalculationService {

    private static final String SEGMENT_SLAB = "JBP_SLAB";
    private static final String STATUS_TARGET_MISSED = "Target Missed";
    private static final String STATUS_QUALIFIER_MISSED = "Qualifier Missed";
    private static final String STATUS_PAYABLE = "Payable";
    private static final BigDecimal HUNDRED = new BigDecimal("100");

    @Override
    public MutableJbpLineItem evaluateLadder(
            List<AgreementJbpCommercialPeriod> ladder,
            Map<String, AgreementJbpCommercialPeriod> rowByCell,
            Map<String, List<AgreementJbpCommercialPeriod>> ladders,
            BigDecimal achieved,
            long achievedQty,
            List<Integer> overlapKeys) {

        AgreementJbpCommercialPeriod anyRow = ladder.get(0);
        AgreementTimePeriod timePeriod = anyRow.getTimePeriod();
        Long configId = anyRow.getJbpConfiguration().getId();
        String periodName = timePeriod.getName();
        PayoutFrequency periodFrequency = timePeriod.getPeriodFrequency();
        String label = periodName;

        Set<Integer> periodMonthKeys = new TreeSet<>(periodKeysFor(timePeriod));
        List<Integer> overlap = periodMonthKeys.stream()
                .filter(overlapKeys::contains)
                .toList();
        if (overlap.isEmpty()) {
            return null;
        }

        List<AgreementJbpCommercialPeriod> tiers = ladder.stream()
                .sorted(Comparator.comparing(AgreementJbpCommercialPeriod::getSlabTierNumber))
                .toList();

        AgreementJbpCommercialPeriod lowestTier = tiers.get(0);
        BigDecimal lowestTarget = resolveTarget(lowestTier, rowByCell, ladders, new HashSet<>());
        BigDecimal achievedPercentOfLowest = percentOfTarget(achieved, lowestTarget);

        AgreementJbpCommercialPeriod selectedTier = null;
        BigDecimal selectedTarget = null;
        for (AgreementJbpCommercialPeriod tier : tiers) {
            BigDecimal resolvedTarget = resolveTarget(tier, rowByCell, ladders, new HashSet<>());
            if (achieved.compareTo(resolvedTarget) >= 0) {
                selectedTier = tier;
                selectedTarget = resolvedTarget;
            }
        }

        if (selectedTier == null) {
            BigDecimal qualifierPercent = nullSafe(lowestTier.getQualifierPercent());
            BigDecimal qualifierThreshold = lowestTarget
                    .multiply(qualifierPercent)
                    .divide(HUNDRED, 2, RoundingMode.HALF_UP);
            boolean qualifierMet = achieved.compareTo(qualifierThreshold) >= 0;

            return new MutableJbpLineItem(
                    SEGMENT_SLAB, label, periodName, timePeriod.getId(), configId, periodFrequency, periodMonthKeys,
                    null, achieved, achievedQty, BigDecimal.ZERO, qualifierMet, false,
                    "Achieved net value " + achieved.toPlainString() + " below lowest slab target "
                            + lowestTarget.toPlainString(),
                    STATUS_TARGET_MISSED,
                    qualifierPercent,
                    achievedPercentOfLowest);
        }

        BigDecimal qualifierPercent = nullSafe(selectedTier.getQualifierPercent());
        BigDecimal qualifierThreshold = selectedTarget
                .multiply(qualifierPercent)
                .divide(HUNDRED, 2, RoundingMode.HALF_UP);
        BigDecimal achievedPercentOfSelected = percentOfTarget(achieved, selectedTarget);

        if (achieved.compareTo(qualifierThreshold) < 0) {
            return new MutableJbpLineItem(
                    SEGMENT_SLAB, label, periodName, timePeriod.getId(), configId, periodFrequency, periodMonthKeys,
                    selectedTier.getSlabTierNumber(), achieved, achievedQty, BigDecimal.ZERO, false, false,
                    "Qualifier not met: achieved " + achieved.toPlainString() + " < required threshold "
                            + qualifierThreshold.toPlainString() + " (" + qualifierPercent.stripTrailingZeros().toPlainString()
                            + "% of target " + selectedTarget.toPlainString() + ")",
                    STATUS_QUALIFIER_MISSED,
                    qualifierPercent,
                    achievedPercentOfSelected);
        }

        BigDecimal qualifyingValue = achieved;
        if (selectedTier.getMaxPurchase() != null && qualifyingValue.compareTo(selectedTier.getMaxPurchase()) > 0) {
            qualifyingValue = selectedTier.getMaxPurchase();
        }

        BigDecimal payout = computeTierPayout(selectedTier, qualifyingValue);
        boolean capped = false;
        if (selectedTier.getMaxPayout() != null && payout.compareTo(selectedTier.getMaxPayout()) > 0) {
            payout = selectedTier.getMaxPayout();
            capped = true;
        }
        payout = payout.setScale(2, RoundingMode.HALF_UP);

        String detail = "Tier " + selectedTier.getSlabTierNumber() + " (target " + selectedTarget.toPlainString()
                + ", " + selectedTier.getPayoutType() + " payout "
                + nullSafe(selectedTier.getPayout()).toPlainString() + ")";

        return new MutableJbpLineItem(
                SEGMENT_SLAB, label, periodName, timePeriod.getId(), configId, periodFrequency, periodMonthKeys,
                selectedTier.getSlabTierNumber(), achieved, achievedQty, payout, true, capped, detail,
                STATUS_PAYABLE, qualifierPercent, achievedPercentOfSelected);
    }

    @Override
    public void applyUpwardContagion(List<MutableJbpLineItem> items) {
        Map<Long, Map<Long, MutableJbpLineItem>> byConfigAndPeriod = new HashMap<>();
        for (MutableJbpLineItem item : items) {
            byConfigAndPeriod
                    .computeIfAbsent(item.configId(), ignored -> new HashMap<>())
                    .put(item.timePeriodId(), item);
        }

        Map<Long, Map<Long, Long>> parentByConfig = buildCalendarContainmentParentMap(items);
        Map<Long, List<MutableJbpLineItem>> itemsByConfig = new HashMap<>();
        for (MutableJbpLineItem item : items) {
            itemsByConfig.computeIfAbsent(item.configId(), ignored -> new ArrayList<>()).add(item);
        }

        for (Map.Entry<Long, List<MutableJbpLineItem>> configEntry : itemsByConfig.entrySet()) {
            Long configId = configEntry.getKey();
            List<MutableJbpLineItem> configItems = configEntry.getValue();
            configItems.sort(Comparator.comparingInt(item -> item.periodMonthKeys().size()));
            Map<Long, Long> parentMap = parentByConfig.getOrDefault(configId, Map.of());
            Map<Long, MutableJbpLineItem> periodItems = byConfigAndPeriod.get(configId);

            for (MutableJbpLineItem item : configItems) {
                if (item.qualifierMet()) {
                    continue;
                }
                Long parentId = parentMap.get(item.timePeriodId());
                if (parentId == null) {
                    continue;
                }
                MutableJbpLineItem parent = periodItems.get(parentId);
                if (parent == null || !parent.qualifierMet()) {
                    continue;
                }
                parent.forceChildQualifierFailure(item.periodName());
            }
        }
    }

    private Map<Long, Map<Long, Long>> buildCalendarContainmentParentMap(List<MutableJbpLineItem> items) {
        Map<Long, Map<Long, Set<Integer>>> monthsByConfig = new HashMap<>();
        for (MutableJbpLineItem item : items) {
            monthsByConfig
                    .computeIfAbsent(item.configId(), ignored -> new HashMap<>())
                    .put(item.timePeriodId(), item.periodMonthKeys());
        }

        Map<Long, Map<Long, Long>> parentByConfig = new HashMap<>();
        for (Map.Entry<Long, Map<Long, Set<Integer>>> configEntry : monthsByConfig.entrySet()) {
            Map<Long, Set<Integer>> periodMonths = configEntry.getValue();
            for (Map.Entry<Long, Set<Integer>> childEntry : periodMonths.entrySet()) {
                Long childId = childEntry.getKey();
                Set<Integer> childMonths = childEntry.getValue();
                if (childMonths.isEmpty()) {
                    continue;
                }

                Long immediateParentId = null;
                int smallestParentSize = Integer.MAX_VALUE;
                for (Map.Entry<Long, Set<Integer>> candidateEntry : periodMonths.entrySet()) {
                    if (candidateEntry.getKey().equals(childId)) {
                        continue;
                    }
                    Set<Integer> candidateMonths = candidateEntry.getValue();
                    if (candidateMonths.size() <= childMonths.size()) {
                        continue;
                    }
                    if (!candidateMonths.containsAll(childMonths)) {
                        continue;
                    }
                    if (candidateMonths.size() < smallestParentSize) {
                        immediateParentId = candidateEntry.getKey();
                        smallestParentSize = candidateMonths.size();
                    }
                }

                if (immediateParentId != null) {
                    parentByConfig
                            .computeIfAbsent(configEntry.getKey(), ignored -> new HashMap<>())
                            .put(childId, immediateParentId);
                }
            }
        }
        return parentByConfig;
    }

    private BigDecimal percentOfTarget(BigDecimal achieved, BigDecimal target) {
        if (target == null || target.compareTo(BigDecimal.ZERO) <= 0) {
            return null;
        }
        return achieved.multiply(HUNDRED).divide(target, 2, RoundingMode.HALF_UP);
    }

    private BigDecimal computeTierPayout(AgreementJbpCommercialPeriod tier, BigDecimal qualifyingValue) {
        BigDecimal payout = nullSafe(tier.getPayout());
        if (tier.getPayoutType() == JbpValueType.RELATIVE) {
            return payout.multiply(qualifyingValue).divide(HUNDRED, 2, RoundingMode.HALF_UP);
        }
        return payout;
    }

    private BigDecimal resolveTarget(
            AgreementJbpCommercialPeriod tier,
            Map<String, AgreementJbpCommercialPeriod> rowByCell,
            Map<String, List<AgreementJbpCommercialPeriod>> ladders,
            Set<String> visited) {

        BigDecimal target = nullSafe(tier.getTarget());
        if (tier.getTargetType() != JbpValueType.RELATIVE) {
            return target;
        }

        String selfKey = cellKey(tier.getJbpConfiguration().getId(), tier.getTimePeriod().getId(),
                tier.getSlabTierNumber());
        if (!visited.add(selfKey)) {
            return target;
        }

        if (tier.getParentTimePeriod() != null) {
            AgreementJbpCommercialPeriod parentTier = rowByCell.get(cellKey(
                    tier.getJbpConfiguration().getId(),
                    tier.getParentTimePeriod().getId(),
                    tier.getSlabTierNumber()));
            if (parentTier != null) {
                BigDecimal parentResolved = resolveTarget(parentTier, rowByCell, ladders, visited);
                return target.multiply(parentResolved).divide(HUNDRED, 2, RoundingMode.HALF_UP);
            }
        }

        BigDecimal anchor = highestAbsoluteTarget(ladders.get(ladderKey(tier)));
        if (anchor != null) {
            return target.multiply(anchor).divide(HUNDRED, 2, RoundingMode.HALF_UP);
        }
        return target;
    }

    private BigDecimal highestAbsoluteTarget(List<AgreementJbpCommercialPeriod> tiers) {
        if (tiers == null) {
            return null;
        }
        BigDecimal highest = null;
        for (AgreementJbpCommercialPeriod tier : tiers) {
            if (tier.getTargetType() == JbpValueType.ABSOLUTE && tier.getTarget() != null) {
                highest = highest == null ? tier.getTarget() : highest.max(tier.getTarget());
            }
        }
        return highest;
    }

    @Override
    public List<Integer> periodKeysFor(AgreementTimePeriod timePeriod) {
        List<Integer> keys = new ArrayList<>();
        for (AgreementTimePeriodMonth month : timePeriod.getIncludedMonths()) {
            keys.add(month.getCalendarYear() * 100 + month.getCalendarMonth());
        }
        return keys;
    }

    private String ladderKey(AgreementJbpCommercialPeriod row) {
        return row.getJbpConfiguration().getId() + "::" + row.getTimePeriod().getId();
    }

    private String cellKey(Long configId, Long timePeriodId, Integer tierNumber) {
        return configId + "::" + timePeriodId + "::" + tierNumber;
    }

    private BigDecimal nullSafe(BigDecimal value) {
        return value != null ? value : BigDecimal.ZERO;
    }
}
