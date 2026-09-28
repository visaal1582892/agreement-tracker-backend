import re

file_path = "src/main/java/com/medplus/agreement_tracker_backend/repository/AgreementVersionRepository.java"
with open(file_path, "r") as f:
    content = f.read()

# Replace the duplicate method
query = """    @Query(value = \"\"\"
                    SELECT v.id FROM agreement_versions v
                    WHERE v.agreement_id = :agreementId
                    AND (
                        EXISTS (SELECT 1 FROM agreement_manufacturer_rules r WHERE r.agreement_version_id = v.id) OR
                        EXISTS (SELECT 1 FROM agreement_division_rules r WHERE r.agreement_version_id = v.id) OR
                        EXISTS (SELECT 1 FROM agreement_product_rules r WHERE r.agreement_version_id = v.id) OR
                        EXISTS (SELECT 1 FROM agreement_computed_products c WHERE c.agreement_version_id = v.id)
                    )
                    ORDER BY v.version_number DESC
                    LIMIT 1
                    \"\"\", nativeQuery = true)
    Optional<Long> findLatestFallbackVersionIdWithRules(@Param("agreementId") Long agreementId);"""

# Let's count how many times it appears
print(f"Count: {content.count(query)}")

if content.count(query) > 1:
    # Remove the last occurrence
    idx = content.rfind(query)
    content = content[:idx] + content[idx+len(query):]

with open(file_path, "w") as f:
    f.write(content)
