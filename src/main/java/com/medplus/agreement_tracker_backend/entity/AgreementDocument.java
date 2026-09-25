package com.medplus.agreement_tracker_backend.entity;

import com.medplus.agreement_tracker_backend.entity.base.AuditableEntity;
import com.medplus.agreement_tracker_backend.enums.DocumentType;
import jakarta.persistence.*;
import lombok.*;
import org.springframework.util.StringUtils;

@Entity
@Table(name = "agreement_documents", indexes = {
        @Index(name = "idx_adoc_agreement_version_id", columnList = "agreement_version_id")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AgreementDocument extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "agreement_version_id", nullable = false)
    private AgreementVersion agreementVersion;

    @Enumerated(EnumType.STRING)
    @Column(name = "document_type", nullable = false, length = 30)
    private DocumentType documentType;

    @Column(name = "file_url", nullable = false, length = 500)
    private String fileUrl;

    @Column(name = "file_path", nullable = false, length = 500)
    private String filePath;

    @Column(name = "file_name", nullable = false, length = 255)
    private String fileName;

    @Column(name = "original_file_name", nullable = false, length = 255)
    private String originalFileName;

    @Column(name = "thumbnail_url", length = 500)
    private String thumbnailUrl;

    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private boolean isActive = true;

    @PrePersist
    @PreUpdate
    void syncLegacyColumns() {
        if (!StringUtils.hasText(fileName) && StringUtils.hasText(originalFileName)) {
            fileName = originalFileName;
        }
        if (!StringUtils.hasText(originalFileName) && StringUtils.hasText(fileName)) {
            originalFileName = fileName;
        }
        if (!StringUtils.hasText(filePath) && StringUtils.hasText(fileUrl)) {
            filePath = fileUrl;
        }
    }
}
