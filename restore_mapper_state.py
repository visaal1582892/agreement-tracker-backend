import os
import re

BASE_DIR = "/home/developer/rohitworkspace/prod/agreement-tracker/agreement-tracker-backend/src/main/java/com/medplus/agreement_tracker_backend/service/impl"
AGREEMENT_SERVICE = os.path.join(BASE_DIR, "AgreementServiceImpl.java")
MAPPER_SERVICE = os.path.join(BASE_DIR, "AgreementMapperService.java")
STATE_SERVICE = os.path.join(BASE_DIR, "AgreementStateService.java")

def extract_methods_from_class(filepath):
    if not os.path.exists(filepath):
        return ""
    with open(filepath, 'r') as f:
        lines = f.readlines()
    
    start = 0
    for i, line in enumerate(lines):
        if "public class " in line:
            start = i + 1
            break
            
    end = len(lines) - 1
    while end > start:
        if lines[end].strip() == "}":
            break
        end -= 1
        
    methods_lines = []
    for line in lines[start:end]:
        if "private final" in line or "@RequiredArgsConstructor" in line:
            continue
        methods_lines.append(line)
        
    return "".join(methods_lines)

mapper_methods_content = extract_methods_from_class(MAPPER_SERVICE)
state_methods_content = extract_methods_from_class(STATE_SERVICE)

with open(AGREEMENT_SERVICE, 'r') as f:
    impl_content = f.read()

impl_content = impl_content.replace('agreementMapperService.', '')
impl_content = impl_content.replace('agreementStateService.', '')

impl_content = re.sub(r'\s*private final AgreementMapperService agreementMapperService;\n', '', impl_content)
impl_content = re.sub(r'\s*private final AgreementStateService agreementStateService;\n', '', impl_content)
impl_content = re.sub(r'\s*AgreementMapperService agreementMapperService,\n', '', impl_content)
impl_content = re.sub(r'\s*AgreementStateService agreementStateService,\n', '', impl_content)
impl_content = re.sub(r'\s*this\.agreementMapperService = agreementMapperService;\n', '', impl_content)
impl_content = re.sub(r'\s*this\.agreementStateService = agreementStateService;\n', '', impl_content)

last_brace_idx = impl_content.rfind('}')
if last_brace_idx != -1:
    impl_content = impl_content[:last_brace_idx] + mapper_methods_content + "\n" + state_methods_content + "\n" + impl_content[last_brace_idx:]

methods = [
    "toVersionResponse", "toParentResponse", "mapActionRequestToTimeline", "mapVendorsToResponse", "mapStoresToResponse",
    "applyDraftFields", "replaceAsset", "replaceAssetStores", "replaceDocuments", "saveRulesAndComputeProducts",
    "scrubRequestForIncomeType", "syncIncomeTypeSpecificData", "clearDownstreamDraftData", "clearAssetForVersion",
    "clearProductRulesForVersion", "clearCommercialStructureForVersion", "replaceVendors", "buildDraftVersion",
    "applyPartnerLocation", "replaceMappingsFromStoreIds"
]

for m in methods:
    impl_content = re.sub(r'\bpublic\s+([a-zA-Z0-9_<>\[\]]+)\s+' + m + r'\s*\(', r'private \1 ' + m + '(', impl_content)

with open(AGREEMENT_SERVICE, 'w') as f:
    f.write(impl_content)

os.remove(MAPPER_SERVICE)
os.remove(STATE_SERVICE)

print("Restoration script done.")
