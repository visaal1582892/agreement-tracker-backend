package com.medplus.agreement_tracker_backend.service.impl;

import com.medplus.agreement_tracker_backend.dto.request.ProductRuleDTO;
import com.medplus.agreement_tracker_backend.dto.request.RuleDTO;
import com.medplus.agreement_tracker_backend.integration.ProductMasterIntegrationService;
import com.medplus.agreement_tracker_backend.integration.dto.IntegrationProductResponse;
import com.medplus.agreement_tracker_backend.integration.dto.external.ExternalProductSearchRequest;
import com.medplus.agreement_tracker_backend.repository.AgreementComputedProductRepository;
import com.medplus.agreement_tracker_backend.repository.AgreementDivisionRuleRepository;
import com.medplus.agreement_tracker_backend.repository.AgreementManufacturerRepository;
import com.medplus.agreement_tracker_backend.repository.AgreementProductRuleRepository;
import com.medplus.agreement_tracker_backend.repository.AgreementVersionRepository;
import com.medplus.agreement_tracker_backend.entity.AgreementComputedProduct;
import com.medplus.agreement_tracker_backend.entity.AgreementManufacturer;
import com.medplus.agreement_tracker_backend.entity.AgreementVersion;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AgreementProductScopeComputeServiceTest {

    @Mock AgreementVersionRepository agreementVersionRepository;
    @Mock AgreementManufacturerRepository manufacturerRuleRepository;
    @Mock AgreementDivisionRuleRepository divisionRuleRepository;
    @Mock AgreementProductRuleRepository productRuleRepository;
    @Mock AgreementComputedProductRepository computedProductRepository;
    @Mock ProductMasterIntegrationService productMasterIntegrationService;
    @Mock EntityManager entityManager;

    @InjectMocks
    AgreementProductScopeComputeService service;

    @Test
    void excludeDivisions_usesSolrExclusionFlag() {
        List<Long> manufacturerIds = List.of(122L);
        List<RuleDTO> divisionRules = List.of(
                new RuleDTO(1178L, "EXCLUDE", 122L),
                new RuleDTO(1180L, "EXCLUDE", 122L));
        List<ProductRuleDTO> productRules = List.of(
                new ProductRuleDTO("ACEN0010", "INCLUDE", 122L),
                new ProductRuleDTO("ACEN0011", "INCLUDE", 122L));

        when(productMasterIntegrationService.countProducts(any(ExternalProductSearchRequest.class)))
                .thenReturn(5L);

        long count = service.countScopedProducts(manufacturerIds, divisionRules, productRules);

        ArgumentCaptor<ExternalProductSearchRequest> requestCaptor = ArgumentCaptor.forClass(ExternalProductSearchRequest.class);
        verify(productMasterIntegrationService).countProducts(requestCaptor.capture());

        ExternalProductSearchRequest captured = requestCaptor.getValue();
        assertEquals(List.of(1178L, 1180L), captured.getManufacturerDivisionId());
        assertEquals(true, captured.isManufacturerDivisionNotIn());
        // INCLUDE product rules: count = min(productRules.size(), baseCount) = min(2, 5) = 2
        assertEquals(2L, count);
    }

    @Test
    void includeDivisions_passesDivisionIdsToSolr() {
        List<Long> manufacturerIds = List.of(122L);
        List<RuleDTO> divisionRules = List.of(new RuleDTO(1181L, "INCLUDE", 122L));

        when(productMasterIntegrationService.countProducts(any(ExternalProductSearchRequest.class)))
                .thenReturn(10L);

        long count = service.countScopedProducts(manufacturerIds, divisionRules, List.of());

        ArgumentCaptor<ExternalProductSearchRequest> requestCaptor = ArgumentCaptor.forClass(ExternalProductSearchRequest.class);
        verify(productMasterIntegrationService).countProducts(requestCaptor.capture());

        ExternalProductSearchRequest captured = requestCaptor.getValue();
        assertEquals(List.of(1181L), captured.getManufacturerDivisionId());
        assertEquals(false, captured.isManufacturerDivisionNotIn());
        // No product rules: count = baseCount
        assertEquals(10L, count);
    }

    @Test
    void excludeProducts_usesSolrProductNotInFlag() {
        List<Long> manufacturerIds = List.of(122L);
        List<ProductRuleDTO> productRules = List.of(
                new ProductRuleDTO("ACEN0010", "EXCLUDE", 122L),
                new ProductRuleDTO("ACEN0011", "EXCLUDE", 122L));

        when(productMasterIntegrationService.countProducts(any(ExternalProductSearchRequest.class)))
                .thenReturn(100L);

        long count = service.countScopedProducts(manufacturerIds, List.of(), productRules);

        ArgumentCaptor<ExternalProductSearchRequest> requestCaptor = ArgumentCaptor.forClass(ExternalProductSearchRequest.class);
        verify(productMasterIntegrationService).countProducts(requestCaptor.capture());

        ExternalProductSearchRequest captured = requestCaptor.getValue();
        assertEquals(true, captured.isProductNotIn());
        // EXCLUDE product rules: count = baseCount - productRules.size() = 100 - 2 = 98
        assertEquals(98L, count);
    }

    @Test
    void excludeProducts_returnsZeroWhenExceedsBaseCount() {
        List<Long> manufacturerIds = List.of(122L);
        List<ProductRuleDTO> productRules = List.of(
                new ProductRuleDTO("ACEN0010", "EXCLUDE", 122L),
                new ProductRuleDTO("ACEN0011", "EXCLUDE", 122L),
                new ProductRuleDTO("ACEN0012", "EXCLUDE", 122L));

        when(productMasterIntegrationService.countProducts(any(ExternalProductSearchRequest.class)))
                .thenReturn(2L);

        long count = service.countScopedProducts(manufacturerIds, List.of(), productRules);

        // EXCLUDE 3 from base of 2: max(0, 2 - 3) = 0
        assertEquals(0L, count);
    }

    @Test
    void emptyManufacturers_returnsZero() {
        long count = service.countScopedProducts(List.of(), List.of(), List.of());
        assertEquals(0L, count);
    }

    @Test
    @SuppressWarnings("unchecked")
    void computeAndLinkProducts_setsManufacturerIdAndDivisionIdAsString() {
        Long versionId = 100L;
        AgreementVersion version = new AgreementVersion();
        version.setId(versionId);

        when(agreementVersionRepository.findById(versionId)).thenReturn(Optional.of(version));
        when(manufacturerRuleRepository.findByAgreementVersionId(versionId))
                .thenReturn(List.of(AgreementManufacturer.builder().manufacturerId(122L).build()));
        when(divisionRuleRepository.findByAgreementVersionId(versionId)).thenReturn(List.of());
        when(productRuleRepository.findByAgreementVersionId(versionId)).thenReturn(List.of());

        IntegrationProductResponse item = IntegrationProductResponse.builder()
                .id("P1")
                .productName("Product 1")
                .manufacturerId(122L)
                .manufacturerName("Mfr 122")
                .divisionId(500L)
                .divisionName("Div 500")
                .build();
        when(productMasterIntegrationService.searchProductsFromSolr(any(ExternalProductSearchRequest.class)))
                .thenReturn(List.of(item));

        service.computeAndLinkProducts(versionId, 999L);

        ArgumentCaptor<List<AgreementComputedProduct>> captor = ArgumentCaptor.forClass(List.class);
        verify(computedProductRepository).saveAll(captor.capture());

        List<AgreementComputedProduct> saved = captor.getValue();
        assertEquals(1, saved.size());
        assertEquals("P1", saved.get(0).getProductId());
        assertEquals("122", saved.get(0).getManufacturerId());
        assertEquals("500", saved.get(0).getDivisionId());
    }

    @Test
    @SuppressWarnings("unchecked")
    void computeAndLinkProducts_nullSafeWhenIdsMissing() {
        Long versionId = 100L;
        AgreementVersion version = new AgreementVersion();
        version.setId(versionId);

        when(agreementVersionRepository.findById(versionId)).thenReturn(Optional.of(version));
        when(manufacturerRuleRepository.findByAgreementVersionId(versionId))
                .thenReturn(List.of(AgreementManufacturer.builder().manufacturerId(122L).build()));
        when(divisionRuleRepository.findByAgreementVersionId(versionId)).thenReturn(List.of());
        when(productRuleRepository.findByAgreementVersionId(versionId)).thenReturn(List.of());

        IntegrationProductResponse itemWithoutIds = IntegrationProductResponse.builder()
                .id("P2")
                .productName("Product 2")
                .manufacturerId(null)
                .divisionId(null)
                .build();
        when(productMasterIntegrationService.searchProductsFromSolr(any(ExternalProductSearchRequest.class)))
                .thenReturn(List.of(itemWithoutIds));

        service.computeAndLinkProducts(versionId, 999L);

        ArgumentCaptor<List<AgreementComputedProduct>> captor = ArgumentCaptor.forClass(List.class);
        verify(computedProductRepository).saveAll(captor.capture());

        List<AgreementComputedProduct> saved = captor.getValue();
        assertEquals(1, saved.size());
        assertEquals("P2", saved.get(0).getProductId());
        assertNull(saved.get(0).getManufacturerId());
        assertNull(saved.get(0).getDivisionId());
    }

    private static IntegrationProductResponse product(String id, Long divisionId) {
        return IntegrationProductResponse.builder()
                .id(id)
                .productName(id)
                .divisionId(divisionId)
                .manufacturerId(122L)
                .build();
    }
}
