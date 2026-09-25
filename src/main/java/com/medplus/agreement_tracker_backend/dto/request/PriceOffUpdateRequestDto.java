package com.medplus.agreement_tracker_backend.dto.request;

import com.medplus.agreement_tracker_backend.enums.PriceOffDiscountType;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;

@Data
public class PriceOffUpdateRequestDto {

    @NotNull(message = "Start date is required")
    private LocalDate startDate;

    @NotNull(message = "End date is required")
    private LocalDate endDate;

    private Integer maxUnitCap;

    private Integer fromQty;

    @Size(max = 1000, message = "Remarks must not exceed 1000 characters")
    private String remarks;

    @NotBlank(message = "Location label is required")
    @Size(max = 255, message = "Location label must not exceed 255 characters")
    private String locationLabel;

    @NotBlank(message = "Channel label is required")
    @Size(max = 255, message = "Channel label must not exceed 255 characters")
    private String channelLabel;

    @NotNull(message = "Discount type is required")
    private PriceOffDiscountType discountType;

    @NotNull(message = "CP is required")
    @DecimalMin(value = "0.0001", message = "CP must be greater than zero")
    private BigDecimal cp;

    @NotNull(message = "MRP is required")
    @DecimalMin(value = "0.0001", message = "MRP must be greater than zero")
    private BigDecimal mrp;

    @DecimalMin(value = "0", message = "Base offer cannot be negative")
    private BigDecimal baseOffer;

    @DecimalMin(value = "0", message = "Medplus contribution cannot be negative")
    private BigDecimal medplusContribution;

    @NotEmpty(message = "At least one location allocation is required")
    private Map<String, Integer> locationAllocations;

    private BigDecimal marginPercent;

    private BigDecimal finalOffer;

    private BigDecimal percentOff;

    private BigDecimal finalMarginPercent;

    private BigDecimal creditNote;

    private Integer totalQty;
}
