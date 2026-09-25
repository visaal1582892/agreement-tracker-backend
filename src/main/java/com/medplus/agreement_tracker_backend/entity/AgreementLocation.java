package com.medplus.agreement_tracker_backend.entity;

import com.medplus.agreement_tracker_backend.entity.base.AuditableEntity;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "agreement_locations", indexes = {
        @Index(name = "idx_al_agreement_version_id", columnList = "agreement_version_id")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AgreementLocation extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "agreement_version_id", nullable = false)
    private AgreementVersion agreementVersion;

    /** COUNTRY, STATE, or CITY */
    @Column(name = "location_type", nullable = false, length = 32)
    private String locationType;

    @Column(name = "country_code", length = 32)
    private String countryCode;

    @Column(name = "country_name", length = 128)
    private String countryName;

    @Column(name = "country_sub_name", length = 128)
    private String countrySubName;

    @Column(name = "state_code", length = 32)
    private String stateCode;

    @Column(name = "state_name", length = 128)
    private String stateName;

    @Column(name = "state_sub_name", length = 128)
    private String stateSubName;

    @Column(name = "city_code", length = 32)
    private String cityCode;

    @Column(name = "city_name", length = 128)
    private String cityName;

    @Column(name = "city_sub_name", length = 128)
    private String citySubName;
}
