package com.medplus.agreement_tracker_backend.service;

import com.medplus.agreement_tracker_backend.entity.RevenueRecognitionSettings;

import java.util.List;

public interface SchedulerSettingsService {

    RevenueRecognitionSettings getSettings();

    RevenueRecognitionSettings updateSettings(RevenueRecognitionSettings settings);

    void runManual(List<Integer> monthKeys, Long supplierId, Long agreementId);

    void lockPeriods(Long agreementId, Integer calendarYear, Integer calendarMonth);
}
