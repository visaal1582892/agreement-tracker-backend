package com.medplus.agreement_tracker_backend.repository;

import com.medplus.agreement_tracker_backend.entity.AgreementComputedProduct;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AgreementComputedProductRepository extends JpaRepository<AgreementComputedProduct, Long>, JpaSpecificationExecutor<AgreementComputedProduct> {

    List<AgreementComputedProduct> findByAgreementVersionId(Long agreementId);

    void deleteByAgreementVersionId(Long agreementId);

    Page<AgreementComputedProduct> findByAgreementVersionId(Long agreementVersionId, Pageable pageable);
}
