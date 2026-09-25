package com.medplus.agreement_tracker_backend.repository;

import com.medplus.agreement_tracker_backend.entity.AgreementActualMetric;
import com.medplus.agreement_tracker_backend.enums.MetricType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface AgreementActualMetricRepository extends JpaRepository<AgreementActualMetric, Long> {
    
    Optional<AgreementActualMetric> findByAgreementVersionIdAndCalendarYearAndCalendarMonthAndMetricType(
            Long agreementVersionId, Integer calendarYear, Integer calendarMonth, MetricType metricType);

    @org.springframework.data.jpa.repository.Query("SELECT a FROM AgreementActualMetric a WHERE a.agreementVersion.id = :agreementVersionId AND a.metricType = :metricType AND (a.calendarYear * 100 + a.calendarMonth) >= :minMonthKey")
    java.util.List<AgreementActualMetric> findByAgreementVersionIdAndMetricTypeStartingFromMonth(
            @org.springframework.data.repository.query.Param("agreementVersionId") Long agreementVersionId,
            @org.springframework.data.repository.query.Param("metricType") MetricType metricType,
            @org.springframework.data.repository.query.Param("minMonthKey") Integer minMonthKey);
}
