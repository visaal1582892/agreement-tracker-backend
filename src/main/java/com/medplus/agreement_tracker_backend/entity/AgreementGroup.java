package com.medplus.agreement_tracker_backend.entity;

import com.medplus.agreement_tracker_backend.entity.base.AuditableEntity;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "company_agreement_groups", indexes = {
        @Index(name = "idx_cag_name", columnList = "name")
},
uniqueConstraints = @UniqueConstraint(name = "uk_cag_name", columnNames = {"name"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AgreementGroup extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "name", nullable = false, unique = true, length = 255)
    private String name;

    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private boolean isActive = true;
}
