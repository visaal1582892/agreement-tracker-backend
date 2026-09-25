package com.medplus.agreement_tracker_backend.service.impl;

import com.medplus.agreement_tracker_backend.dto.request.ProductRuleDTO;
import com.medplus.agreement_tracker_backend.dto.request.ProductScopeCombinationDto;
import com.medplus.agreement_tracker_backend.dto.request.RuleDTO;
import com.medplus.agreement_tracker_backend.entity.AgreementComputedProduct;
import com.medplus.agreement_tracker_backend.entity.AgreementVersion;
import com.medplus.agreement_tracker_backend.enums.ProductMasterStatusEnum;
import com.medplus.agreement_tracker_backend.enums.RuleType;
import com.medplus.agreement_tracker_backend.exception.BusinessException;
import com.medplus.agreement_tracker_backend.integration.ProductMasterIntegrationService;
import com.medplus.agreement_tracker_backend.integration.dto.IntegrationProductResponse;
import com.medplus.agreement_tracker_backend.integration.dto.external.ExternalProductSearchRequest;
import com.medplus.agreement_tracker_backend.repository.AgreementComputedProductRepository;
import com.medplus.agreement_tracker_backend.repository.AgreementDivisionRuleRepository;
import com.medplus.agreement_tracker_backend.repository.AgreementManufacturerRepository;
import com.medplus.agreement_tracker_backend.repository.AgreementProductRuleRepository;
import com.medplus.agreement_tracker_backend.repository.AgreementVersionRepository;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Slf4j
public class AgreementProductScopeComputeService {

    private final AgreementVersionRepository agreementVersionRepository;
    private final AgreementManufacturerRepository manufacturerRuleRepository;
    private final AgreementDivisionRuleRepository divisionRuleRepository;
    private final AgreementProductRuleRepository productRuleRepository;
    private final AgreementComputedProductRepository computedProductRepository;
    private final ProductMasterIntegrationService productMasterIntegrationService;
    private final EntityManager entityManager;

    @Transactional
    public void computeAndLinkProducts(Long agreementVersionId, Long userId) {
        AgreementVersion version = agreementVersionRepository.findById(agreementVersionId)
                .orElseThrow(() -> new BusinessException("Agreement version not found for product scope compute"));

        List<Long> manufacturerIds = manufacturerRuleRepository.findByAgreementVersionId(agreementVersionId)
                .stream()
                .map(manufacturer -> manufacturer.getManufacturerId())
                .toList();

        if (manufacturerIds.isEmpty()) {
            log.debug("No manufacturers for agreementVersionId={}; clearing computed products", agreementVersionId);
            computedProductRepository.deleteByAgreementVersionId(agreementVersionId);
            return;
        }

        List<RuleDTO> divisionRules = divisionRuleRepository.findByAgreementVersionId(agreementVersionId)
                .stream()
                .map(rule -> new RuleDTO(rule.getDivisionId(), rule.getRuleType().name(), rule.getManufacturerId()))
                .toList();

        List<ProductRuleDTO> productRules = productRuleRepository.findByAgreementVersionId(agreementVersionId)
                .stream()
                .map(rule -> new ProductRuleDTO(rule.getProductId(), rule.getRuleType().name(),
                        rule.getManufacturerId()))
                .toList();

        List<IntegrationProductResponse> finalProducts = new ArrayList<>();
        for (Long mfrId : manufacturerIds) {
            List<RuleDTO> mfrDivRules = divisionRules.stream()
                    .filter(r -> mfrId.equals(r.manufacturerId()))
                    .toList();
            List<ProductRuleDTO> mfrProdRules = productRules.stream()
                    .filter(r -> mfrId.equals(r.manufacturerId()))
                    .toList();

            finalProducts.addAll(resolveScopedProducts(List.of(mfrId), mfrDivRules, mfrProdRules));
        }

        computedProductRepository.deleteByAgreementVersionId(agreementVersionId);

        // Batch insert in chunks of 500 to avoid Hibernate L1 cache growth
        final int batchSize = 500;
        List<AgreementComputedProduct> batch = new ArrayList<>(batchSize);
        for (IntegrationProductResponse product : finalProducts) {
            if (product == null) {
                continue;
            }
            String mfrIdStr = product.getManufacturerId() != null ? String.valueOf(product.getManufacturerId()) : null;
            String divIdStr = product.getDivisionId() != null ? String.valueOf(product.getDivisionId()) : null;

            AgreementComputedProduct computed = AgreementComputedProduct.builder()
                    .agreementVersion(version)
                    .productId(product.getId())
                    .productNameSnapshot(product.getProductName())
                    .divisionNameSnapshot(product.getDivisionName())
                    .manufacturerNameSnapshot(product.getManufacturerName())
                    .manufacturerId(mfrIdStr)
                    .divisionId(divIdStr)
                    .build();
            computed.setCreatedByUserId(userId);
            batch.add(computed);

            if (batch.size() >= batchSize) {
                computedProductRepository.saveAll(batch);
                computedProductRepository.flush();
                entityManager.clear();
                batch.clear();
            }
        }
        if (!batch.isEmpty()) {
            computedProductRepository.saveAll(batch);
        }

        log.info("Computed {} products for agreementVersionId={}", finalProducts.size(), agreementVersionId);
    }

    /**
     * Live preview of final applicable-product count.
     * Uses a lightweight Solr numFound query (noOfRow=0) + mathematical adjustment
     * for product rules — avoids fetching all product documents.
     */
    public long countScopedProducts(List<ProductScopeCombinationDto> combinations) {
        if (combinations == null || combinations.isEmpty()) {
            return 0;
        }

        long totalCount = 0;

        for (ProductScopeCombinationDto combo : combinations) {
            if (combo.manufacturerId() == null)
                continue;

            List<Long> manufacturerIds = List.of(combo.manufacturerId());
            List<RuleDTO> safeDivisionRules = combo.divisionRules() != null ? combo.divisionRules() : List.of();
            List<ProductRuleDTO> safeProductRules = combo.productRules() != null ? combo.productRules() : List.of();

            // Step 1: Get base scope count from Solr (numFound, no document fetch)
            ExternalProductSearchRequest request = buildSearchRequest(
                    manufacturerIds, safeDivisionRules, safeProductRules);
            long baseCount = productMasterIntegrationService.countProducts(request);

            // Step 2: Apply product rules mathematically (handle mixed INCLUDE/EXCLUDE)
            if (safeProductRules.isEmpty()) {
                totalCount += baseCount;
                continue;
            }

            long includeCount = safeProductRules.stream()
                    .filter(r -> "INCLUDE".equals(r.ruleType()))
                    .count();
            long excludeCount = safeProductRules.stream()
                    .filter(r -> "EXCLUDE".equals(r.ruleType()))
                    .count();

            if (includeCount > 0) {
                // INCLUDE rules narrow the scope; excludes are a subset of includes
                totalCount += Math.min(includeCount, baseCount);
            } else {
                // EXCLUDE only
                totalCount += Math.max(0, baseCount - excludeCount);
            }
        }

        return totalCount;
    }

    public long countScopedProducts(Long agreementVersionId) {
        List<Long> manufacturerIds = manufacturerRuleRepository.findByAgreementVersionId(agreementVersionId)
                .stream()
                .map(m -> m.getManufacturerId())
                .toList();

        if (manufacturerIds.isEmpty())
            return 0;

        List<RuleDTO> divisionRules = divisionRuleRepository.findByAgreementVersionId(agreementVersionId)
                .stream()
                .map(rule -> new RuleDTO(rule.getDivisionId(), rule.getRuleType().name(), rule.getManufacturerId()))
                .toList();

        List<ProductRuleDTO> productRules = productRuleRepository.findByAgreementVersionId(agreementVersionId)
                .stream()
                .map(rule -> new ProductRuleDTO(rule.getProductId(), rule.getRuleType().name(),
                        rule.getManufacturerId()))
                .toList();

        return countScopedProducts(manufacturerIds, divisionRules, productRules);
    }

    public long countScopedProducts(
            List<Long> manufacturerIds,
            List<RuleDTO> divisionRules,
            List<ProductRuleDTO> productRules) {
        if (manufacturerIds == null || manufacturerIds.isEmpty())
            return 0;

        List<RuleDTO> safeDivisionRules = divisionRules != null ? divisionRules : List.of();
        List<ProductRuleDTO> safeProductRules = productRules != null ? productRules : List.of();

        long totalCount = 0;
        for (Long mfrId : manufacturerIds) {
            List<RuleDTO> mfrDivRules = safeDivisionRules.stream()
                    .filter(r -> mfrId.equals(r.manufacturerId()))
                    .toList();
            List<ProductRuleDTO> mfrProdRules = safeProductRules.stream()
                    .filter(r -> mfrId.equals(r.manufacturerId()))
                    .toList();
            totalCount += countScopedProducts(List.of(
                    new ProductScopeCombinationDto(mfrId, mfrDivRules, mfrProdRules)));
        }
        return totalCount;
    }

    /**
     * Manufacturer pool → division INCLUDE/EXCLUDE → product INCLUDE/EXCLUDE.
     * Uses native Solr exclusion flags (manufacturerDivisionNotIn, productNotIn)
     * so Solr handles filtering instead of fetching everything and filtering
     * in-memory.
     */
    public List<IntegrationProductResponse> resolveScopedProducts(
            List<Long> manufacturerIds,
            List<RuleDTO> divisionRules,
            List<ProductRuleDTO> productRules) {
        if (manufacturerIds == null || manufacturerIds.isEmpty()) {
            return List.of();
        }

        List<RuleDTO> safeDivisionRules = divisionRules != null ? divisionRules : List.of();
        List<ProductRuleDTO> safeProductRules = productRules != null ? productRules : List.of();

        // Build Solr request with inclusion/exclusion lists
        ExternalProductSearchRequest request = buildSearchRequest(
                manufacturerIds, safeDivisionRules, safeProductRules);

        // Fetch via paginated chunks (500 at a time, not Integer.MAX_VALUE)
        List<IntegrationProductResponse> baseProducts = productMasterIntegrationService.searchProductsFromSolr(request);

        // In-memory filters are now a safety net only
        List<IntegrationProductResponse> afterDivisionFilter = applyDivisionRules(baseProducts, safeDivisionRules);
        return applyProductRules(afterDivisionFilter, safeProductRules);
    }

    private ExternalProductSearchRequest buildSearchRequest(
            List<Long> manufacturerIds,
            List<RuleDTO> divisionRules,
            List<ProductRuleDTO> productRules) {

        ExternalProductSearchRequest request = ExternalProductSearchRequest.builder()
                .manufacturerIdList(manufacturerIds)
                .isRunOnDb(false)
                .isDetailsRequired(true)
                .productStatus(ProductMasterStatusEnum.ACTIVE)
                .build();

        // --- Division rules ---
        List<Long> includeDivisions = divisionRules.stream()
                .filter(r -> RuleType.INCLUDE.name().equals(r.ruleType()))
                .map(RuleDTO::id)
                .toList();
        List<Long> excludeDivisions = divisionRules.stream()
                .filter(r -> RuleType.EXCLUDE.name().equals(r.ruleType()))
                .map(RuleDTO::id)
                .toList();

        if (!includeDivisions.isEmpty()) {
            request.setManufacturerDivisionId(includeDivisions);
            // Solr includes these divisions only
        } else if (!excludeDivisions.isEmpty()) {
            request.setManufacturerDivisionId(excludeDivisions);
            request.setManufacturerDivisionNotIn(true);
            // Solr excludes these divisions
        }
        // If neither: Solr returns all divisions for the manufacturers

        // --- Product rules ---
        List<String> includeProducts = productRules.stream()
                .filter(r -> RuleType.INCLUDE.name().equals(r.ruleType()))
                .map(ProductRuleDTO::id)
                .toList();
        List<String> excludeProducts = productRules.stream()
                .filter(r -> RuleType.EXCLUDE.name().equals(r.ruleType()))
                .map(ProductRuleDTO::id)
                .toList();

        if (!includeProducts.isEmpty()) {
            request.setProductIdList(new HashSet<>(includeProducts));
        } else if (!excludeProducts.isEmpty()) {
            request.setProductIdList(new HashSet<>(excludeProducts));
            request.setProductNotIn(true);
        }

        return request;
    }

    private List<IntegrationProductResponse> applyDivisionRules(
            List<IntegrationProductResponse> products,
            List<RuleDTO> rules) {
        if (rules.isEmpty()) {
            return products;
        }

        // Handle mixed INCLUDE/EXCLUDE rules
        List<Long> includeDivisions = rules.stream()
                .filter(r -> RuleType.INCLUDE.name().equals(r.ruleType()))
                .map(RuleDTO::id)
                .toList();
        List<Long> excludeDivisions = rules.stream()
                .filter(r -> RuleType.EXCLUDE.name().equals(r.ruleType()))
                .map(RuleDTO::id)
                .toList();

        // If there are INCLUDE rules, Solr already filtered — skip
        if (!includeDivisions.isEmpty()) {
            return products;
        }

        // EXCLUDE only: safety net guard against Solr returning excluded divisions
        if (!excludeDivisions.isEmpty()) {
            Set<Long> excludeSet = new HashSet<>(excludeDivisions);
            List<IntegrationProductResponse> filtered = products.stream()
                    .filter(product -> !excludeSet.contains(product.getDivisionId()))
                    .toList();
            if (filtered.size() != products.size()) {
                log.warn(
                        "Solr EXCLUDE returned {} products that should have been excluded (safety net applied, kept {})",
                        products.size() - filtered.size(), filtered.size());
            }
            return filtered;
        }

        return products;
    }

    private List<IntegrationProductResponse> applyProductRules(
            List<IntegrationProductResponse> products,
            List<ProductRuleDTO> rules) {
        if (rules.isEmpty()) {
            return products;
        }

        // Handle mixed INCLUDE/EXCLUDE rules
        List<String> includeProducts = rules.stream()
                .filter(r -> RuleType.INCLUDE.name().equals(r.ruleType()))
                .map(ProductRuleDTO::id)
                .toList();
        List<String> excludeProducts = rules.stream()
                .filter(r -> RuleType.EXCLUDE.name().equals(r.ruleType()))
                .map(ProductRuleDTO::id)
                .toList();

        // If there are INCLUDE rules, Solr already filtered — skip
        if (!includeProducts.isEmpty()) {
            return products;
        }

        // EXCLUDE only: safety net guard against Solr returning excluded products
        if (!excludeProducts.isEmpty()) {
            Set<String> excludeSet = new HashSet<>(excludeProducts);
            List<IntegrationProductResponse> filtered = products.stream()
                    .filter(product -> !excludeSet.contains(product.getId()))
                    .toList();
            if (filtered.size() != products.size()) {
                log.warn(
                        "Solr EXCLUDE returned {} products that should have been excluded (safety net applied, kept {})",
                        products.size() - filtered.size(), filtered.size());
            }
            return filtered;
        }

        return products;
    }
}
