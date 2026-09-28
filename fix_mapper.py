import re

file_path = "src/main/java/com/medplus/agreement_tracker_backend/service/impl/AgreementMapperService.java"
with open(file_path, "r") as f:
    content = f.read()

# Replace the slow logic with the optimized logic
old_block_start = "                Long versionIdForRules = version.getId();"
old_block_end_marker = "                List<AgreementVersionResponse.ProductRuleSummary> productRules ="
# We'll replace from old_block_start up to right before productRules definition

new_block = """                Long versionIdForRules = version.getId();
                boolean isAssetRental = isAssetRentalIncomeType(
                                version.getIncomeType() != null ? version.getIncomeType().getId() : null);

                List<AgreementManufacturerRule> rawManufacturerRules = manufacturerRuleRepository.findByAgreementVersionId(versionIdForRules);
                List<AgreementDivisionRule> rawDivisionRules = divisionRuleRepository.findByAgreementVersionId(versionIdForRules);
                List<AgreementProductRule> rawProductRules = productRuleRepository.findByAgreementVersionId(versionIdForRules);
                boolean hasComputedProducts = computedProductRepository.countByAgreementVersionId(versionIdForRules) > 0;

                if (!isAssetRental
                                && rawManufacturerRules.isEmpty()
                                && rawDivisionRules.isEmpty()
                                && rawProductRules.isEmpty()
                                && !hasComputedProducts) {
                        
                        Optional<Long> fallbackVersionIdOpt = agreementVersionRepository.findLatestFallbackVersionIdWithRules(parent.getId());
                        if (fallbackVersionIdOpt.isPresent()) {
                            versionIdForRules = fallbackVersionIdOpt.get();
                            rawManufacturerRules = manufacturerRuleRepository.findByAgreementVersionId(versionIdForRules);
                            rawDivisionRules = divisionRuleRepository.findByAgreementVersionId(versionIdForRules);
                            rawProductRules = productRuleRepository.findByAgreementVersionId(versionIdForRules);
                        }
                }

                List<Long> manufacturerIds = rawManufacturerRules.stream().map(AgreementManufacturer::getManufacturerId).toList();

                List<AgreementVersionResponse.ManufacturerSummary> manufacturers = new ArrayList<>();
                if (!manufacturerIds.isEmpty()) {
                        try {
                                Map<Long, IntegrationManufacturerResponse> manufacturerByIdMap = productMasterIntegrationService
                                                .getManufacturersByIds(manufacturerIds);
                                manufacturers = manufacturerIds.stream()
                                                .map(id -> {
                                                        IntegrationManufacturerResponse hydrated = manufacturerByIdMap.get(id);
                                                        return new AgreementVersionResponse.ManufacturerSummary(
                                                                        id,
                                                                        hydrated != null ? hydrated.getManufacturerName() : null);
                                                })
                                                .toList();
                        } catch (Exception ex) {
                                log.warn("Could not hydrate manufacturer names from Product Microservice for ids={}: {}",
                                                manufacturerIds, ex.getMessage());
                        }
                } else {
                        // Fallback to computed products distinct manufacturers
                        try {
                                List<Object[]> distinctMfrs = computedProductRepository.findDistinctManufacturersByVersionId(versionIdForRules);
                                manufacturers = distinctMfrs.stream()
                                                .map(row -> {
                                                        String mfrIdStr = (String) row[0];
                                                        String mfrName = (String) row[1];
                                                        Long mfrId = mfrIdStr != null ? Long.parseLong(mfrIdStr) : null;
                                                        return new AgreementVersionResponse.ManufacturerSummary(mfrId, mfrName);
                                                })
                                                .filter(m -> m.id() != null)
                                                .toList();
                        } catch (Exception ex) {
                                log.warn("Could not extract distinct manufacturers from computed products: {}", ex.getMessage());
                        }
                }

                List<Long> divisionIds = rawDivisionRules.stream()
                                .map(AgreementDivisionRule::getDivisionId)
                                .toList();
                Map<Long, String> divisionNamesByIdMap = Map.of();
                if (!divisionIds.isEmpty()) {
                        try {
                                divisionNamesByIdMap = productMasterIntegrationService.getDivisionNamesByIds(
                                                manufacturerIds,
                                                divisionIds);
                        } catch (Exception ex) {
                                log.warn("Could not hydrate division names from Product Microservice for divisionIds={}: {}",
                                                divisionIds, ex.getMessage());
                        }
                }

                final Map<Long, String> finalDivisionNamesById = divisionNamesByIdMap;
                List<AgreementVersionResponse.DivisionRuleSummary> divisionRules = rawDivisionRules.stream()
                                .map(dr -> new AgreementVersionResponse.DivisionRuleSummary(
                                                dr.getDivisionId(),
                                                dr.getRuleType().name(),
                                                finalDivisionNamesById.get(dr.getDivisionId()),
                                                dr.getManufacturerId()))
                                .toList();

                List<String> productIds = rawProductRules.stream()
                                .map(AgreementProductRule::getProductId)
                                .toList();
                Map<String, IntegrationProductResponse> productsByCodeMap = Map.of();
                if (!productIds.isEmpty()) {
                        try {
                                productsByCodeMap = productMasterIntegrationService.getProductsByCodes(productIds);
                        } catch (Exception ex) {
                                log.warn("Could not hydrate product details from Product Microservice for productIds={}: {}",
                                                productIds, ex.getMessage());
                        }
                }

                final Map<String, IntegrationProductResponse> finalProductsByCode = productsByCodeMap;
"""

# Now we need to slice and dice
idx_start = content.find(old_block_start)
idx_end = content.find(old_block_end_marker)

if idx_start != -1 and idx_end != -1:
    content = content[:idx_start] + new_block + content[idx_end:]
else:
    print("Could not find blocks")

# Also, update `rawProductRules.stream()` mapping for productRules
# Look for:
#                 List<AgreementVersionResponse.ProductRuleSummary> productRules = productRuleRepository
#                                 .findByAgreementVersionId(versionIdForRules)
#                                 .stream()
# Replace with:
#                 List<AgreementVersionResponse.ProductRuleSummary> productRules = rawProductRules.stream()
content = content.replace("List<AgreementVersionResponse.ProductRuleSummary> productRules = productRuleRepository\n                                .findByAgreementVersionId(versionIdForRules)\n                                .stream()", 
                          "List<AgreementVersionResponse.ProductRuleSummary> productRules = rawProductRules.stream()")

with open(file_path, "w") as f:
    f.write(content)
