package com.medplus.agreement_tracker_backend.repository;

import com.medplus.agreement_tracker_backend.entity.RevenueRecognitionSettings;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface RevenueRecognitionSettingsRepository extends JpaRepository<RevenueRecognitionSettings, Long> {
}
