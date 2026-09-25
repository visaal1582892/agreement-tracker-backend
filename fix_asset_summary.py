import os
import re

BASE_DIR = "/home/developer/rohitworkspace/prod/agreement-tracker/agreement-tracker-backend/src/main/java/com/medplus/agreement_tracker_backend/service/impl"
AGREEMENT_SERVICE_IMPL = os.path.join(BASE_DIR, "AgreementServiceImpl.java")
AGREEMENT_MAPPER_SERVICE = os.path.join(BASE_DIR, "AgreementMapperService.java")

with open(AGREEMENT_SERVICE_IMPL, 'r') as f:
    lines = f.readlines()

start_idx = -1
for i, line in enumerate(lines):
    if "agreementMapperService.toAssetSummary" in line:
        start_idx = i
        break

if start_idx != -1:
    end_idx = -1
    braces = 0
    found = False
    for i in range(start_idx, len(lines)):
        braces += lines[i].count("{")
        braces -= lines[i].count("}")
        if lines[i].count("{") > 0:
            found = True
        if found and braces == 0:
            end_idx = i
            break
            
    method_lines = lines[start_idx:end_idx+1]
    
    # Remove from impl
    del lines[start_idx:end_idx+1]
    
    with open(AGREEMENT_SERVICE_IMPL, 'w') as f:
        f.writelines(lines)
        
    # Fix method signature
    method_str = "".join(method_lines)
    method_str = method_str.replace("private AgreementVersionResponse.AssetSummary agreementMapperService.toAssetSummary", "public AgreementVersionResponse.AssetSummary toAssetSummary")
    
    # Append to mapper
    with open(AGREEMENT_MAPPER_SERVICE, 'r') as f:
        mapper_lines = f.readlines()
        
    for i in range(len(mapper_lines)-1, -1, -1):
        if mapper_lines[i].strip() == "}":
            mapper_lines.insert(i, "\n" + method_str + "\n")
            break
            
    with open(AGREEMENT_MAPPER_SERVICE, 'w') as f:
        f.writelines(mapper_lines)

print("Fixed toAssetSummary")
