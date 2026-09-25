package com.medplus.agreement_tracker_backend.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "consumer_price_off_campaign_location_allocation", indexes = {
        @Index(name = "idx_cpo_loc_alloc_campaign", columnList = "campaign_id")
}, uniqueConstraints = {
        @UniqueConstraint(name = "uk_cpo_loc_alloc_campaign_code", columnNames = {"campaign_id", "location_code"})
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ConsumerPriceOffCampaignLocationAllocation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "campaign_id", nullable = false)
    private ConsumerPriceOffCampaign campaign;

    @Column(name = "location_code", nullable = false, length = 50)
    private String locationCode;

    @Column(name = "allocated_qty", nullable = false)
    private Integer allocatedQty;
}
