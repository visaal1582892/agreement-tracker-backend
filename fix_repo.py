import re

file_path = "src/main/java/com/medplus/agreement_tracker_backend/repository/AgreementComputedProductRepository.java"
with open(file_path, "r") as f:
    content = f.read()

import_statement = "import org.springframework.data.jpa.repository.Query;\nimport org.springframework.data.repository.query.Param;\n"
if "org.springframework.data.jpa.repository.Query" not in content:
    content = content.replace("import org.springframework.stereotype.Repository;",
                              "import org.springframework.stereotype.Repository;\n" + import_statement)

query_method = """    Page<AgreementComputedProduct> findByAgreementVersionId(Long agreementVersionId, Pageable pageable);

    @Query("SELECT DISTINCT c.manufacturerId, c.manufacturerNameSnapshot FROM AgreementComputedProduct c WHERE c.agreementVersion.id = :versionId AND c.manufacturerId IS NOT NULL")
    List<Object[]> findDistinctManufacturersByVersionId(@Param("versionId") Long versionId);
"""
content = content.replace("    Page<AgreementComputedProduct> findByAgreementVersionId(Long agreementVersionId, Pageable pageable);", query_method)

with open(file_path, "w") as f:
    f.write(content)
