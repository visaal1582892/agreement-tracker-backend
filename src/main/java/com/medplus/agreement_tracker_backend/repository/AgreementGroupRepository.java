package com.medplus.agreement_tracker_backend.repository;

import com.medplus.agreement_tracker_backend.entity.AgreementGroup;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface AgreementGroupRepository extends JpaRepository<AgreementGroup, Long>,
        JpaSpecificationExecutor<AgreementGroup> {

    List<AgreementGroup> findByIsActiveTrueOrderByNameAsc();

    boolean existsByNameIgnoreCase(String name);

    boolean existsByNameIgnoreCaseAndIdNot(String name, Long excludeId);

    Optional<AgreementGroup> findByNameIgnoreCase(String name);
}
