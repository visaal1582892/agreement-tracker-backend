package com.medplus.agreement_tracker_backend.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "price_off_location_master", indexes = {
        @Index(name = "idx_price_off_location_code", columnList = "code"),
        @Index(name = "idx_price_off_location_active", columnList = "is_active")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PriceOffLocationMaster {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "name", nullable = false, length = 255)
    private String name;

    @Column(name = "code", nullable = false, unique = true, length = 50)
    private String code;

    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private boolean isActive = true;
}
