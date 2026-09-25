package com.medplus.agreement_tracker_backend.dto.request;

import com.medplus.agreement_tracker_backend.dto.common.AgreementLocationDto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record DraftDetailsPayload(
        Long incomeTypeId,
        Long agreementTypeId,
        LocalDate startDate,
        LocalDate expiryDate,
        String notes,
        List<AgreementLocationDto> locations,
        String adhocSubType,
        BigDecimal quantityCap,
        Long invoiceVendorId,
        String invoiceVendorNameSnapshot,
        Integer payoutBufferDays,
        String leadTimeBasis,
        Integer invoiceGenerationLeadTime,
        String calculationBasis,
        String paymentRealizationType,
        List<AgreementDocumentDTO> documents
) {}
