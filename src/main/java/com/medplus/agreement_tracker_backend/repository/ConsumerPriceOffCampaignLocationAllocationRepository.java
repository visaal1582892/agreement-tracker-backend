package com.medplus.agreement_tracker_backend.repository;

import com.medplus.agreement_tracker_backend.entity.ConsumerPriceOffCampaignLocationAllocation;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ConsumerPriceOffCampaignLocationAllocationRepository
        extends JpaRepository<ConsumerPriceOffCampaignLocationAllocation, Long> {
}
