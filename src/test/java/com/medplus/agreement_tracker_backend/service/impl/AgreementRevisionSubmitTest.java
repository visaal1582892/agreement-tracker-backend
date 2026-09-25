package com.medplus.agreement_tracker_backend.service.impl;

import com.medplus.agreement_tracker_backend.dto.request.AgreementRevisionSubmitRequest;
import com.medplus.agreement_tracker_backend.dto.request.CommercialDataPayload;
import com.medplus.agreement_tracker_backend.dto.response.JbpStagedWorkbookDto;
import com.medplus.agreement_tracker_backend.entity.Agreement;
import com.medplus.agreement_tracker_backend.entity.AgreementAssetPayoutPeriod;
import com.medplus.agreement_tracker_backend.entity.AgreementGroup;
import com.medplus.agreement_tracker_backend.entity.AgreementVersion;
import com.medplus.agreement_tracker_backend.entity.IncomeType;
import com.medplus.agreement_tracker_backend.entity.User;
import com.medplus.agreement_tracker_backend.enums.ApprovalStatus;
import com.medplus.agreement_tracker_backend.exception.BusinessException;
import com.medplus.agreement_tracker_backend.exception.ConflictValidationException;
import com.medplus.agreement_tracker_backend.integration.ProductMasterIntegrationService;
import com.medplus.agreement_tracker_backend.repository.AgreementActionRequestRepository;
import com.medplus.agreement_tracker_backend.repository.AgreementApprovalRepository;
import com.medplus.agreement_tracker_backend.repository.AgreementAssetPayoutPeriodRepository;
import com.medplus.agreement_tracker_backend.repository.AgreementAuditRepository;
import com.medplus.agreement_tracker_backend.repository.AgreementComputedProductRepository;
import com.medplus.agreement_tracker_backend.repository.AgreementDivisionRuleRepository;
import com.medplus.agreement_tracker_backend.repository.AgreementDocumentRepository;
import com.medplus.agreement_tracker_backend.repository.AgreementGroupRepository;
import com.medplus.agreement_tracker_backend.repository.AgreementJbpCommercialPeriodRepository;
import com.medplus.agreement_tracker_backend.repository.AgreementJbpConfigurationRepository;
import com.medplus.agreement_tracker_backend.repository.AgreementManufacturerRepository;
import com.medplus.agreement_tracker_backend.repository.AgreementProductRuleRepository;
import com.medplus.agreement_tracker_backend.repository.AgreementReminderRepository;
import com.medplus.agreement_tracker_backend.repository.AgreementRepository;
import com.medplus.agreement_tracker_backend.repository.AgreementSlabRepository;
import com.medplus.agreement_tracker_backend.repository.AgreementStoreMappingRepository;
import com.medplus.agreement_tracker_backend.repository.AgreementTimePeriodRepository;
import com.medplus.agreement_tracker_backend.repository.AgreementTypeRepository;
import com.medplus.agreement_tracker_backend.repository.AgreementVendorRepository;
import com.medplus.agreement_tracker_backend.repository.AgreementVersionRepository;
import com.medplus.agreement_tracker_backend.repository.IncomeTypeRepository;
import com.medplus.agreement_tracker_backend.repository.UserRepository;
import com.medplus.agreement_tracker_backend.service.AgreementGroupService;
import com.medplus.agreement_tracker_backend.service.StoreMappingService;
import com.medplus.agreement_tracker_backend.service.impl.AgreementProductScopeComputeService;
import com.medplus.agreement_tracker_backend.util.AgreementStatusResolver;
import jakarta.validation.Validator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.PlatformTransactionManager;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AgreementRevisionSubmitTest {

    private static final Long AGREEMENT_ID = 100L;
    private static final Long USER_ID = 7L;
    private static final Long CURRENT_VERSION_ID = 50L;

    @Mock AgreementRepository agreementRepository;
    @Mock AgreementVersionRepository agreementVersionRepository;
    @Mock AgreementGroupRepository agreementGroupRepository;
    @Mock AgreementGroupService agreementGroupService;
    @Mock AgreementVendorRepository vendorRepository;
    @Mock AgreementManufacturerRepository manufacturerRuleRepository;
    @Mock AgreementDivisionRuleRepository divisionRuleRepository;
    @Mock AgreementProductRuleRepository productRuleRepository;
    @Mock AgreementSlabRepository slabRepository;
    @Mock AgreementJbpCommercialPeriodRepository jbpCommercialPeriodRepository;
    @Mock AgreementJbpConfigurationRepository jbpConfigurationRepository;
    @Mock AgreementComputedProductRepository computedProductRepository;
    @Mock AgreementApprovalRepository approvalRepository;
    @Mock AgreementAuditRepository auditRepository;
    @Mock AgreementActionRequestRepository actionRequestRepository;
    @Mock AgreementReminderRepository reminderRepository;
    @Mock AgreementDocumentRepository documentRepository;
    @Mock AgreementAssetPayoutPeriodRepository assetPayoutPeriodRepository;
    @Mock AgreementStoreMappingRepository storeMappingRepository;
    @Mock StoreMappingService storeMappingService;
    @Mock AgreementTimePeriodRepository timePeriodRepository;
    @Mock UserRepository userRepository;
    @Mock IncomeTypeRepository incomeTypeRepository;
    @Mock AgreementTypeRepository agreementTypeRepository;
    @Mock ProductMasterIntegrationService productMasterIntegrationService;
    @Mock AgreementProductScopeComputeService agreementProductScopeComputeService;
    @Mock AgreementStatusResolver statusResolver;
    @Mock PlatformTransactionManager transactionManager;
    @Mock Validator validator;

    @InjectMocks
    AgreementServiceImpl agreementService;

    private Agreement parent;
    private User owner;

    @BeforeEach
    void setUp() {
        owner = User.builder().id(USER_ID).build();
        AgreementGroup group = AgreementGroup.builder().id(1L).build();
        parent = Agreement.builder()
                .id(AGREEMENT_ID)
                .owner(owner)
                .agreementGroup(group)
                .currentVersionId(CURRENT_VERSION_ID)
                .agreementName("Test Agreement")
                .build();
    }







    @Test
    void applyCommercialChildren_jbpOverrideWithoutBlueprint_throws() {
        AgreementVersion source = AgreementVersion.builder()
                .id(10L)
                .agreement(parent)
                .versionNumber(1)
                .approvalStatus(ApprovalStatus.APPROVED)
                .build();
        AgreementVersion target = AgreementVersion.builder()
                .id(11L)
                .agreement(parent)
                .versionNumber(2)
                .approvalStatus(ApprovalStatus.DRAFT)
                .build();

        when(assetPayoutPeriodRepository.findByAgreementVersionIdOrderByPeriodMonthsAscIdAsc(11L))
                .thenReturn(List.of());
        when(agreementVersionRepository.findById(11L)).thenReturn(Optional.of(target));
        when(assetPayoutPeriodRepository.findByAgreementVersionIdOrderByPeriodMonthsAscIdAsc(10L))
                .thenReturn(List.of());
        when(slabRepository.findByAgreementVersionIdOrderByMinCapAsc(10L)).thenReturn(List.of());

        JbpStagedWorkbookDto jbp = new JbpStagedWorkbookDto(
                List.of(new JbpStagedWorkbookDto.StagedSheet(
                        "cfg-1", "Config 1", "Master_YEARLY", "YEARLY", true, List.of())),
                false,
                null
        );
        CommercialDataPayload commercialData = new CommercialDataPayload(null, jbp, null, null);

        BusinessException ex = assertThrows(
                BusinessException.class,
                () -> agreementService.applyCommercialChildrenForRevision(source, target, USER_ID, commercialData));

        assertEquals(
                "JBP blueprint is required when submitting JBP commercial data on Edit/Renew.",
                ex.getMessage());
    }

    @Test
    void applyCommercialChildren_emptyStoreMappings_isExplicitOverride() {
        AgreementVersion source = AgreementVersion.builder()
                .id(10L)
                .agreement(parent)
                .versionNumber(1)
                .approvalStatus(ApprovalStatus.APPROVED)
                .build();
        AgreementVersion target = AgreementVersion.builder()
                .id(11L)
                .agreement(parent)
                .versionNumber(2)
                .approvalStatus(ApprovalStatus.DRAFT)
                .build();

        when(assetPayoutPeriodRepository.findByAgreementVersionIdOrderByPeriodMonthsAscIdAsc(11L))
                .thenReturn(List.of());
        when(agreementVersionRepository.findById(11L)).thenReturn(Optional.of(target));
        when(assetPayoutPeriodRepository.findByAgreementVersionIdOrderByPeriodMonthsAscIdAsc(10L))
                .thenReturn(List.of());
        when(slabRepository.findByAgreementVersionIdOrderByMinCapAsc(10L)).thenReturn(List.of());

        when(jbpConfigurationRepository.findHydratedByAgreementVersionId(10L)).thenReturn(List.of());

        CommercialDataPayload commercialData = new CommercialDataPayload(
                null,
                null,
                List.of(),
                null
        );

        agreementService.applyCommercialChildrenForRevision(source, target, USER_ID, commercialData);

        verify(storeMappingService).replaceMappingsFromStoreIds(eq(11L), eq(List.of()));
        verify(storeMappingService, never()).copyMappings(anyLong(), anyLong());
    }

    @Test
    void applyCommercialChildren_nullCommercialData_deepCopiesStores() {
        AgreementVersion source = AgreementVersion.builder()
                .id(10L)
                .agreement(parent)
                .versionNumber(1)
                .approvalStatus(ApprovalStatus.APPROVED)
                .build();
        AgreementVersion target = AgreementVersion.builder()
                .id(11L)
                .agreement(parent)
                .versionNumber(2)
                .approvalStatus(ApprovalStatus.DRAFT)
                .build();

        when(assetPayoutPeriodRepository.findByAgreementVersionIdOrderByPeriodMonthsAscIdAsc(11L))
                .thenReturn(List.of());
        when(agreementVersionRepository.findById(11L)).thenReturn(Optional.of(target));
        when(assetPayoutPeriodRepository.findByAgreementVersionIdOrderByPeriodMonthsAscIdAsc(10L))
                .thenReturn(List.of());
        when(slabRepository.findByAgreementVersionIdOrderByMinCapAsc(10L)).thenReturn(List.of());

        when(jbpConfigurationRepository.findHydratedByAgreementVersionId(10L)).thenReturn(List.of());

        agreementService.applyCommercialChildrenForRevision(source, target, USER_ID, null);

        verify(storeMappingService).copyMappings(10L, 11L);
        verify(storeMappingService, never()).replaceMappingsFromStoreIds(anyLong(), anyList());
        assertEquals(CURRENT_VERSION_ID, parent.getCurrentVersionId());
    }

    @Test
    void applyCommercialChildren_deepCopy_copiesStoreMappingsWithoutStoreCountGate() {
        IncomeType assetIncome = IncomeType.builder()
                .id(9L)
                .name("Asset Rentals")
                .build();
        AgreementVersion source = AgreementVersion.builder()
                .id(10L)
                .agreement(parent)
                .versionNumber(1)
                .approvalStatus(ApprovalStatus.APPROVED)
                .incomeType(assetIncome)
                .build();
        AgreementVersion target = AgreementVersion.builder()
                .id(11L)
                .agreement(parent)
                .versionNumber(2)
                .approvalStatus(ApprovalStatus.DRAFT)
                .incomeType(assetIncome)
                .build();

        when(assetPayoutPeriodRepository.findByAgreementVersionIdOrderByPeriodMonthsAscIdAsc(11L))
                .thenReturn(List.of());
        when(agreementVersionRepository.findById(11L)).thenReturn(Optional.of(target));
        when(assetPayoutPeriodRepository.findByAgreementVersionIdOrderByPeriodMonthsAscIdAsc(10L))
                .thenReturn(List.of());
        when(slabRepository.findByAgreementVersionIdOrderByMinCapAsc(10L)).thenReturn(List.of());

        when(jbpConfigurationRepository.findHydratedByAgreementVersionId(10L)).thenReturn(List.of());

        agreementService.applyCommercialChildrenForRevision(source, target, USER_ID, null);

        verify(storeMappingService).copyMappings(10L, 11L);
        verify(storeMappingService, never()).replaceMappingsFromStoreIds(anyLong(), anyList());
    }

    @Test
    void applyCommercialChildren_storeOverride_replacesStores() {
        AgreementVersion source = AgreementVersion.builder()
                .id(10L)
                .agreement(parent)
                .versionNumber(1)
                .approvalStatus(ApprovalStatus.APPROVED)
                .build();
        AgreementVersion target = AgreementVersion.builder()
                .id(11L)
                .agreement(parent)
                .versionNumber(2)
                .approvalStatus(ApprovalStatus.DRAFT)
                .build();

        when(assetPayoutPeriodRepository.findByAgreementVersionIdOrderByPeriodMonthsAscIdAsc(11L))
                .thenReturn(List.of());
        when(agreementVersionRepository.findById(11L)).thenReturn(Optional.of(target));
        when(assetPayoutPeriodRepository.findByAgreementVersionIdOrderByPeriodMonthsAscIdAsc(10L))
                .thenReturn(List.of());
        when(slabRepository.findByAgreementVersionIdOrderByMinCapAsc(10L)).thenReturn(List.of());

        when(jbpConfigurationRepository.findHydratedByAgreementVersionId(10L)).thenReturn(List.of());

        CommercialDataPayload.StoreMappingSubmitDto dto = new CommercialDataPayload.StoreMappingSubmitDto("S1", "Store 1", "Address", 500001, "TS", "HYD", "HYD", false);
        CommercialDataPayload commercialData = new CommercialDataPayload(
                null,
                null,
                List.of(dto),
                null
        );

        agreementService.applyCommercialChildrenForRevision(source, target, USER_ID, commercialData);

        verify(storeMappingService).replaceMappingsFromStoreIds(eq(11L), eq(List.of(dto)));
        verify(storeMappingService, never()).copyMappings(anyLong(), anyLong());
        assertEquals(CURRENT_VERSION_ID, parent.getCurrentVersionId());
    }

    @Test
    void applyCommercialChildren_keepsTargetAssetPeriods_skipsCopy() {
        AgreementVersion source = AgreementVersion.builder()
                .id(10L)
                .agreement(parent)
                .versionNumber(1)
                .approvalStatus(ApprovalStatus.APPROVED)
                .build();
        AgreementVersion target = AgreementVersion.builder()
                .id(11L)
                .agreement(parent)
                .versionNumber(2)
                .approvalStatus(ApprovalStatus.DRAFT)
                .build();

        AgreementAssetPayoutPeriod existing = AgreementAssetPayoutPeriod.builder()
                .periodMonths(3)
                .payoutPerStore(java.math.BigDecimal.TEN)
                .build();
        when(assetPayoutPeriodRepository.findByAgreementVersionIdOrderByPeriodMonthsAscIdAsc(11L))
                .thenReturn(List.of(existing));
        when(slabRepository.findByAgreementVersionIdOrderByMinCapAsc(10L)).thenReturn(List.of());

        when(jbpConfigurationRepository.findHydratedByAgreementVersionId(10L)).thenReturn(List.of());

        agreementService.applyCommercialChildrenForRevision(source, target, USER_ID, null);

        verify(agreementVersionRepository, never()).findById(11L);
        verify(assetPayoutPeriodRepository, never())
                .findByAgreementVersionIdOrderByPeriodMonthsAscIdAsc(10L);
    }


}
