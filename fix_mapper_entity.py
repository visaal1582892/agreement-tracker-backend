import re

file_path = "src/main/java/com/medplus/agreement_tracker_backend/service/impl/AgreementMapperService.java"
with open(file_path, "r") as f:
    content = f.read()

content = content.replace("List<AgreementManufacturerRule> rawManufacturerRules =", "List<AgreementManufacturer> rawManufacturerRules =")

with open(file_path, "w") as f:
    f.write(content)
