package com.medplus.agreement_tracker_backend.repository;

import com.medplus.agreement_tracker_backend.entity.AgreementMonthlyPayableRollup;
import org.springframework.data.jpa.repository.JpaRepository;

import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import java.util.List;
import java.util.Optional;

public interface AgreementMonthlyPayableRollupRepository extends JpaRepository<AgreementMonthlyPayableRollup, Long>, JpaSpecificationExecutor<AgreementMonthlyPayableRollup> {

    List<AgreementMonthlyPayableRollup> findByAgreementVersionId(Long agreementVersionId);

    void deleteByCalendarYearAndCalendarMonth(Integer calendarYear, Integer calendarMonth);

    Optional<AgreementMonthlyPayableRollup> findByAgreementIdAndCalendarYearAndCalendarMonth(Long agreementId, Integer calendarYear, Integer calendarMonth);

    @org.springframework.data.jpa.repository.Query("SELECT MIN(r.calendarYear * 100 + r.calendarMonth) FROM AgreementMonthlyPayableRollup r WHERE r.agreementId = :agreementId AND r.status = 'OPEN'")
    Integer findEarliestOpenMonth(@org.springframework.data.repository.query.Param("agreementId") Long agreementId);

    @org.springframework.data.jpa.repository.Modifying
    @org.springframework.data.jpa.repository.Query("UPDATE AgreementMonthlyPayableRollup r SET r.status = 'LOCKED' WHERE r.agreementId = :agreementId AND (r.calendarYear * 100 + r.calendarMonth) <= :limitKey")
    void lockPeriods(@org.springframework.data.repository.query.Param("agreementId") Long agreementId, @org.springframework.data.repository.query.Param("limitKey") Integer limitKey);
}
