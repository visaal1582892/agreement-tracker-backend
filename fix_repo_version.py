import re

file_path = "src/main/java/com/medplus/agreement_tracker_backend/repository/AgreementVersionRepository.java"
with open(file_path, "r") as f:
    content = f.read()

query_method = """    @Query(value = \"\"\"
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
    Optional<Long> findLatestFallbackVersionIdWithRules(@Param("agreementId") Long agreementId);

}"""

content = content.replace("}", query_method)
# The above replace will replace the last } which closes the interface

# Since there might be other } in the file, let's just do a specific replacement.
# Let's replace the last `\n}\n` with `\n` + query_method + `\n}\n`
content = content.rstrip()
if content.endswith("}"):
    content = content[:-1] + query_method + "\n"

with open(file_path, "w") as f:
    f.write(content)
