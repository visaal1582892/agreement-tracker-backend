package com.medplus.agreement_tracker_backend.entity;

import com.medplus.agreement_tracker_backend.enums.PayoutFrequency;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Entity
@Table(name = "agreement_jbp_configurations", indexes = {
        @Index(name = "idx_jbp_cfg_version", columnList = "agreement_version_id")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AgreementJbpConfiguration {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "agreement_version_id", nullable = false)
    private AgreementVersion agreementVersion;

    @Column(name = "slab_count", nullable = false)
    private Integer slabCount;

    /**
     * Payment intervals (parent-level frequencies) for this configuration.
     * Stored as string values of PayoutFrequency enum (e.g., "YEARLY", "QUARTERLY").
     * These determine which time periods are auto-resolved for the Excel template.
     */
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(
            name = "agreement_jbp_config_payment_intervals",
            joinColumns = @JoinColumn(name = "jbp_configuration_id")
    )
    @Column(name = "interval_value", length = 50)
    @Builder.Default
    private List<String> paymentIntervals = new ArrayList<>();

    /**
     * Target intervals (sub-period frequencies) for Spread sheets in this configuration.
     */
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(
            name = "agreement_jbp_config_target_intervals",
            joinColumns = @JoinColumn(name = "jbp_configuration_id")
    )
    @Column(name = "interval_value", length = 50)
    @Builder.Default
    private List<String> targetIntervals = new ArrayList<>();

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }
}
