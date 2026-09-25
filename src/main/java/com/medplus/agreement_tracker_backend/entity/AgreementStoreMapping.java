package com.medplus.agreement_tracker_backend.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "agreement_store_mappings", indexes = {
        @Index(name = "idx_asm_version", columnList = "agreement_version_id")
}, uniqueConstraints = @UniqueConstraint(name = "uk_version_store_id", columnNames = {"agreement_version_id", "store_id"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AgreementStoreMapping {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "agreement_version_id", nullable = false)
    private AgreementVersion agreementVersion;

    @Column(name = "store_id", nullable = false, length = 50)
    private String storeId;

    @Column(name = "name")
    private String name;

    @Column(name = "address", columnDefinition = "TEXT")
    private String address;

    @Column(name = "pin_code")
    private Integer pinCode;

    @Column(name = "region_1", length = 100)
    private String region1;

    @Column(name = "region_2", length = 100)
    private String region2;

    @Column(name = "region_3", length = 100)
    private String region3;

    @Column(name = "is_custom", nullable = false)
    @Builder.Default
    private boolean isCustom = false;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
}
