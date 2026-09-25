package com.medplus.agreement_tracker_backend.service.impl;

import com.medplus.agreement_tracker_backend.entity.RevenueRecognitionSettings;
import com.medplus.agreement_tracker_backend.repository.RevenueRecognitionSettingsRepository;
import com.medplus.agreement_tracker_backend.service.RevenueRecognitionCalculationService;
import com.medplus.agreement_tracker_backend.service.SchedulerSettingsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class SchedulerSettingsServiceImpl implements SchedulerSettingsService {

    private final RevenueRecognitionSettingsRepository repository;
    private final RevenueRecognitionCalculationService calculationService;
    private final com.medplus.agreement_tracker_backend.repository.AgreementMonthlyPayableRollupRepository rollupRepo;

    @Override
    public RevenueRecognitionSettings getSettings() {
        return repository.findById(1L).orElseGet(() -> {
            RevenueRecognitionSettings defaultSettings = RevenueRecognitionSettings.builder()
                    .isEnabled(false)
                    .cronExpression("0 0 1 1 * ?")
                    .timeZone("Asia/Kolkata")
                    .lookbackWindowMonths(1)
                    .currentStatus("IDLE")
                    .build();
            return repository.save(defaultSettings);
        });
    }

    @Override
    public RevenueRecognitionSettings updateSettings(RevenueRecognitionSettings settings) {
        RevenueRecognitionSettings existing = getSettings();
        existing.setIsEnabled(settings.getIsEnabled());
        existing.setCronExpression(settings.getCronExpression());
        existing.setTimeZone(settings.getTimeZone());
        existing.setLookbackWindowMonths(settings.getLookbackWindowMonths());
        return repository.save(existing);
    }

    @Override
    public void runManual(List<Integer> monthKeys, Long supplierId, Long agreementId) {
        RevenueRecognitionSettings existing = getSettings();
        if ("RUNNING".equals(existing.getCurrentStatus())) {
            throw new IllegalStateException("Scheduler is already running.");
        }
        
        existing.setCurrentStatus("RUNNING");
        repository.save(existing);
        
        java.util.concurrent.CompletableFuture.runAsync(() -> {
            try {
                calculationService.calculateAndStoreRevenue(monthKeys, supplierId, agreementId);
                
                RevenueRecognitionSettings successSettings = getSettings();
                successSettings.setLastSuccessfulRun(java.time.LocalDateTime.now());
                successSettings.setCurrentStatus("SUCCESS");
                repository.save(successSettings);
            } catch (com.medplus.agreement_tracker_backend.exception.SystemicDatabaseFailureException t) {
                log.error("Manual run aborted early due to systemic failure", t);
                try {
                    java.io.PrintWriter pw = new java.io.PrintWriter("/tmp/run_error.log");
                    t.printStackTrace(pw);
                    pw.close();
                } catch (Exception x) {}
                RevenueRecognitionSettings failedSettings = getSettings();
                failedSettings.setLastFailedRun(java.time.LocalDateTime.now());
                failedSettings.setCurrentStatus("FAILED");
                repository.save(failedSettings);
            } catch (Throwable t) {
                log.error("Manual run failed", t);
                try {
                    java.io.PrintWriter pw = new java.io.PrintWriter("/tmp/run_error.log");
                    t.printStackTrace(pw);
                    pw.close();
                } catch (Exception x) {}
                RevenueRecognitionSettings failedSettings = getSettings();
                failedSettings.setLastFailedRun(java.time.LocalDateTime.now());
                failedSettings.setCurrentStatus("FAILED");
                repository.save(failedSettings);
            }
        });
    }

    @Override
    @org.springframework.transaction.annotation.Transactional
    public void lockPeriods(Long agreementId, Integer calendarYear, Integer calendarMonth) {
        int limitKey = calendarYear * 100 + calendarMonth;
        rollupRepo.lockPeriods(agreementId, limitKey);
    }

    @org.springframework.context.event.EventListener(org.springframework.boot.context.event.ApplicationReadyEvent.class)
    public void resetZombieStatusOnStartup() {
        RevenueRecognitionSettings settings = getSettings();
        if ("RUNNING".equals(settings.getCurrentStatus())) {
            log.warn("Found RUNNING status on startup. Resetting to FAILED (Zombie process).");
            settings.setCurrentStatus("FAILED");
            settings.setLastFailedRun(java.time.LocalDateTime.now());
            repository.save(settings);
        }
    }
}
