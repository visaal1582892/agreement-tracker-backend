package com.medplus.agreement_tracker_backend.repository;

import com.medplus.agreement_tracker_backend.entity.PriceOffLocationMaster;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;
import java.util.Optional;

public interface PriceOffLocationMasterRepository extends JpaRepository<PriceOffLocationMaster, Long>,
        JpaSpecificationExecutor<PriceOffLocationMaster> {

    List<PriceOffLocationMaster> findByIsActiveTrueOrderByCodeAsc();

    List<PriceOffLocationMaster> findAllByOrderByCodeAsc();

    Optional<PriceOffLocationMaster> findByCodeIgnoreCaseAndIsActiveTrue(String code);

    Optional<PriceOffLocationMaster> findByCodeIgnoreCase(String code);

    boolean existsByCodeIgnoreCase(String code);
}
