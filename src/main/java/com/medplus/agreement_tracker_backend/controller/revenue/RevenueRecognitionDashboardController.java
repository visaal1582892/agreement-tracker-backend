package com.medplus.agreement_tracker_backend.controller.revenue;

import com.medplus.agreement_tracker_backend.dto.response.DashboardRowDto;
import com.medplus.agreement_tracker_backend.entity.AgreementMonthlyPayableRollup;
import com.medplus.agreement_tracker_backend.repository.AgreementMonthlyPayableRollupRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Tuple;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/revenue-recognition")
@RequiredArgsConstructor
public class RevenueRecognitionDashboardController {

    private final AgreementMonthlyPayableRollupRepository rollupRepository;
    private final EntityManager entityManager;

    @GetMapping("/dashboard")
    @PreAuthorize("hasAnyAuthority('ADMIN', 'MASTER_MANAGE', 'COMMERCIAL_PAYOUT_CALCULATE')")
    public ResponseEntity<List<DashboardRowDto>> getDashboardData(
            @RequestParam(required = false) List<Integer> years,
            @RequestParam(required = false) List<Integer> months) {

        Specification<AgreementMonthlyPayableRollup> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            
            if (years != null && !years.isEmpty()) {
                predicates.add(root.get("calendarYear").in(years));
            }
            if (months != null && !months.isEmpty()) {
                predicates.add(root.get("calendarMonth").in(months));
            }
            
            return cb.and(predicates.toArray(new Predicate[0]));
        };

        Sort sort = Sort.by(Sort.Direction.DESC, "calendarYear", "calendarMonth")
                .and(Sort.by(Sort.Direction.ASC, "agreementId"));

        List<AgreementMonthlyPayableRollup> results = rollupRepository.findAll(spec, sort);
        
        if (results.isEmpty()) {
            return ResponseEntity.ok(List.of());
        }

        Set<Long> versionIds = results.stream().map(AgreementMonthlyPayableRollup::getAgreementVersionId).collect(Collectors.toSet());
        
        List<Tuple> versionTuples = entityManager.createQuery(
                "SELECT av.id, a.agreementName, av.versionNumber FROM AgreementVersion av JOIN av.agreement a WHERE av.id IN :ids", Tuple.class)
                .setParameter("ids", versionIds)
                .getResultList();
                
        Map<Long, String[]> versionMap = versionTuples.stream()
                .collect(Collectors.toMap(
                        t -> t.get(0, Long.class),
                        t -> new String[]{t.get(1, String.class), "v" + t.get(2, Integer.class)}
                ));

        List<DashboardRowDto> dtos = results.stream().map(r -> {
            String[] names = versionMap.getOrDefault(r.getAgreementVersionId(), new String[]{"Unknown", "Unknown"});
            return new DashboardRowDto(
                    r.getId(),
                    names[0],
                    names[1],
                    r.getSupplierId(),
                    r.getCalendarYear(),
                    r.getCalendarMonth(),
                    r.getTriggeredFrequencies(),
                    r.getEarnedAmount(),
                    r.getPayableAmount(),
                    r.getPaymentInterval()
            );
        }).toList();

        return ResponseEntity.ok(dtos);
    }
}
