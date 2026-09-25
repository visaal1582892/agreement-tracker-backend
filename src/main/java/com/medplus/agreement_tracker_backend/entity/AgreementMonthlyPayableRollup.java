package com.medplus.agreement_tracker_backend.entity;

import com.medplus.agreement_tracker_backend.entity.base.AuditableEntity;
import com.medplus.agreement_tracker_backend.enums.PeriodStatus;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

@Entity
@Table(name = "agreement_monthly_payable_rollup", indexes = {
        @Index(name = "idx_ampr_agreement_id", columnList = "agreement_id"),
        @Index(name = "idx_ampr_agreement_version_id", columnList = "agreement_version_id"),
        @Index(name = "idx_ampr_calendar", columnList = "calendar_year, calendar_month")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AgreementMonthlyPayableRollup extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "agreement_version_id", nullable = false)
    private Long agreementVersionId;

    @Column(name = "agreement_id", nullable = false)
    private Long agreementId;

    @Column(name = "supplier_id")
    private Long supplierId;

    @Column(name = "calendar_year", nullable = false)
    private Integer calendarYear;

    @Column(name = "calendar_month", nullable = false)
    private Integer calendarMonth;

    @Column(name = "triggered_frequencies", length = 255)
    private String triggeredFrequencies;

    @Column(name = "earned_amount", precision = 15, scale = 2)
    private BigDecimal earnedAmount;

    @Column(name = "payable_amount", precision = 15, scale = 2)
    private BigDecimal payableAmount;

    @Column(name = "payment_interval", length = 50)
    private String paymentInterval;

    @Column(name = "calculated_at")
    private java.time.LocalDateTime calculatedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    @Builder.Default
    private PeriodStatus status = PeriodStatus.OPEN;
}
