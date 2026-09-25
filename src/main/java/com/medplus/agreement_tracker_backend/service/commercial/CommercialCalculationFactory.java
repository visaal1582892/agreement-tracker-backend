package com.medplus.agreement_tracker_backend.service.commercial;

import com.medplus.agreement_tracker_backend.entity.AgreementVersion;
import com.medplus.agreement_tracker_backend.entity.IncomeType;
import com.medplus.agreement_tracker_backend.exception.BusinessException;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
public class CommercialCalculationFactory {

    private final Map<String, CommercialCalculationStrategy> strategiesByIncomeType;

    public CommercialCalculationFactory(List<CommercialCalculationStrategy> strategies) {
        this.strategiesByIncomeType = strategies.stream()
                .collect(Collectors.toMap(
                        strategy -> normalize(strategy.supportedIncomeType()),
                        Function.identity()));
    }

    public CommercialCalculationStrategy resolve(AgreementVersion version) {
        IncomeType incomeType = version.getIncomeType();
        if (incomeType == null || incomeType.getName() == null) {
            throw new BusinessException("Agreement version " + version.getId() + " has no income type assigned");
        }
        CommercialCalculationStrategy strategy = strategiesByIncomeType.get(normalize(incomeType.getName()));
        if (strategy == null) {
            throw new BusinessException(
                    "No commercial calculation strategy registered for income type '" + incomeType.getName() + "'");
        }
        return strategy;
    }

    private String normalize(String incomeTypeName) {
        return incomeTypeName.trim().toLowerCase(Locale.ROOT);
    }
}
