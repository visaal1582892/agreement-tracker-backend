package com.medplus.agreement_tracker_backend.scheduler;

import com.medplus.agreement_tracker_backend.repository.ConsumerPriceOffCampaignRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

@Component
@RequiredArgsConstructor
@Slf4j
public class PriceOffExpiryScheduler {

    private final ConsumerPriceOffCampaignRepository repository;

    @Scheduled(cron = "0 1 0 * * ?")
    @Transactional
    public void expireOldCampaigns() {
        log.info("Starting nightly Price Off expiry sweep...");
        int updatedCount = repository.markExpiredCampaigns(LocalDate.now());
        log.info("Successfully expired {} campaigns.", updatedCount);
    }
}
