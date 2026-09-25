package com.medplus.agreement_tracker_backend.service.commercial.jbp;

import com.medplus.agreement_tracker_backend.entity.AgreementJbpCommercialPeriod;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class PeriodDetectionServiceImpl implements PeriodDetectionService {

    @Override
    public Map<String, AgreementJbpCommercialPeriod> indexByCell(List<AgreementJbpCommercialPeriod> rows) {
        Map<String, AgreementJbpCommercialPeriod> index = new HashMap<>();
        for (AgreementJbpCommercialPeriod row : rows) {
            index.put(cellKey(
                    row.getJbpConfiguration().getId(),
                    row.getTimePeriod().getId(),
                    row.getSlabTierNumber()), row);
        }
        return index;
    }

    @Override
    public Map<String, List<AgreementJbpCommercialPeriod>> groupByConfigAndPeriod(
            List<AgreementJbpCommercialPeriod> rows) {
        Map<String, List<AgreementJbpCommercialPeriod>> ladders = new LinkedHashMap<>();
        rows.stream()
                .sorted(Comparator
                        .comparing((AgreementJbpCommercialPeriod row) -> row.getTimePeriod().getName(),
                                Comparator.nullsLast(String::compareTo))
                        .thenComparing(AgreementJbpCommercialPeriod::getSlabTierNumber,
                                Comparator.nullsLast(Integer::compareTo)))
                .forEach(row -> ladders
                        .computeIfAbsent(ladderKey(row), ignored -> new ArrayList<>())
                        .add(row));
        return ladders;
    }

    private String ladderKey(AgreementJbpCommercialPeriod row) {
        return row.getJbpConfiguration().getId() + "::" + row.getTimePeriod().getId();
    }

    private String cellKey(Long configId, Long timePeriodId, Integer tierNumber) {
        return configId + "::" + timePeriodId + "::" + tierNumber;
    }
}
