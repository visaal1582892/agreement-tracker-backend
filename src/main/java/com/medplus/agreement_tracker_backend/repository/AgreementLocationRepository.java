package com.medplus.agreement_tracker_backend.repository;

import com.medplus.agreement_tracker_backend.entity.AgreementLocation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AgreementLocationRepository extends JpaRepository<AgreementLocation, Long> {

    List<AgreementLocation> findByAgreementVersionId(Long agreementVersionId);

    void deleteByAgreementVersionId(Long agreementVersionId);
}
