package com.medplus.agreement_tracker_backend.repository;

import com.medplus.agreement_tracker_backend.entity.ConsumerPriceOffCampaign;
import com.medplus.agreement_tracker_backend.enums.PriceOffApprovalStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface ConsumerPriceOffCampaignRepository extends JpaRepository<ConsumerPriceOffCampaign, Long>,
        JpaSpecificationExecutor<ConsumerPriceOffCampaign> {

    Page<ConsumerPriceOffCampaign> findByApprovalStatus(
            PriceOffApprovalStatus approvalStatus,
            Pageable pageable);

    @Query("""
            SELECT c FROM ConsumerPriceOffCampaign c
            WHERE c.id = :id
            """)
    Optional<ConsumerPriceOffCampaign> findByIdWithProduct(@Param("id") Long id);

    @Query("""
            SELECT c FROM ConsumerPriceOffCampaign c
            LEFT JOIN FETCH c.locationAllocations
            WHERE c.id = :id
            """)
    Optional<ConsumerPriceOffCampaign> findByIdWithProductAndAllocations(@Param("id") Long id);

    List<ConsumerPriceOffCampaign> findByIdIn(List<Long> ids);

    @Query("""
            SELECT COUNT(c) FROM ConsumerPriceOffCampaign c
            WHERE c.approvalStatus = com.medplus.agreement_tracker_backend.enums.PriceOffApprovalStatus.APPROVED
            AND c.startDate <= :today
            AND c.endDate >= :today
            """)
    long countLiveCampaigns(@Param("today") java.time.LocalDate today);

    long countByApprovalStatus(PriceOffApprovalStatus approvalStatus);

    @Modifying(clearAutomatically = true)
    @Query("""
            UPDATE ConsumerPriceOffCampaign c
            SET c.approvalStatus = com.medplus.agreement_tracker_backend.enums.PriceOffApprovalStatus.EXPIRED
            WHERE c.approvalStatus = com.medplus.agreement_tracker_backend.enums.PriceOffApprovalStatus.APPROVED
            AND c.endDate < :currentDate
            """)
    int markExpiredCampaigns(@Param("currentDate") LocalDate currentDate);

    @Query("""
            SELECT COUNT(c) FROM ConsumerPriceOffCampaign c
            WHERE c.productId = :productId
            AND c.approvalStatus NOT IN (
                com.medplus.agreement_tracker_backend.enums.PriceOffApprovalStatus.REJECTED,
                com.medplus.agreement_tracker_backend.enums.PriceOffApprovalStatus.EXPIRED)
            AND c.startDate <= :endDate
            AND c.endDate >= :startDate
            AND (:excludeId IS NULL OR c.id <> :excludeId)
            """)
    long countOverlappingCampaigns(
            @Param("productId") String productId,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate,
            @Param("excludeId") Long excludeId);

    @Query("""
            SELECT c FROM ConsumerPriceOffCampaign c
            WHERE c.productId IN :productIds
            AND c.approvalStatus NOT IN (
                com.medplus.agreement_tracker_backend.enums.PriceOffApprovalStatus.REJECTED,
                com.medplus.agreement_tracker_backend.enums.PriceOffApprovalStatus.EXPIRED)
            """)
    List<ConsumerPriceOffCampaign> findActiveCampaignsByProductIds(@Param("productIds") List<String> productIds);
}
