package com.medplus.agreement_tracker_backend.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "revenue_recognition_settings")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RevenueRecognitionSettings {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "is_enabled", nullable = false)
    private Boolean isEnabled;

    @Column(name = "cron_expression", nullable = false)
    private String cronExpression;

    @Column(name = "time_zone", nullable = false)
    private String timeZone;

    @Column(name = "lookback_window_months", nullable = false)
    private Integer lookbackWindowMonths;

    @Column(name = "last_successful_run")
    private LocalDateTime lastSuccessfulRun;

    @Column(name = "last_failed_run")
    private LocalDateTime lastFailedRun;

    @Column(name = "current_status")
    private String currentStatus;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "updated_by")
    private String updatedBy;
}
