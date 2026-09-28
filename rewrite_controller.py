import re

file_path = "src/main/java/com/medplus/agreement_tracker_backend/controller/AgreementController.java"
with open(file_path, "r") as f:
    content = f.read()

import_statement = "import com.medplus.agreement_tracker_backend.dto.response.AgreementVersionSummaryResponse;\n"
if "AgreementVersionSummaryResponse" not in content:
    content = content.replace("import com.medplus.agreement_tracker_backend.dto.response.AgreementVersionResponse;",
                              "import com.medplus.agreement_tracker_backend.dto.response.AgreementVersionResponse;\n" + import_statement)

# Wait, `getVersionsByAgreementId` might be in AgreementController, not AgreementVersionController.
# I grep'd it earlier and found it in `AgreementController.java`. Let's replace the return type.
content = content.replace("public ResponseEntity<List<AgreementVersionResponse>> getVersionsByAgreementId",
                          "public ResponseEntity<List<AgreementVersionSummaryResponse>> getVersionsByAgreementId")

with open(file_path, "w") as f:
    f.write(content)
