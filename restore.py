import os
import re

BASE_DIR = "/home/developer/rohitworkspace/prod/agreement-tracker/agreement-tracker-backend/src/main/java/com/medplus/agreement_tracker_backend/service/impl"
AGREEMENT_SERVICE = os.path.join(BASE_DIR, "AgreementServiceImpl.java")
CLONE_SERVICE = os.path.join(BASE_DIR, "AgreementCloneService.java")
VAL_SERVICE = os.path.join(BASE_DIR, "AgreementValidationService.java")

def extract_methods_from_class(filepath):
    if not os.path.exists(filepath):
        return ""
    with open(filepath, 'r') as f:
        lines = f.readlines()
    
    # find the start of the class
    start = 0
    for i, line in enumerate(lines):
        if "public class " in line:
            start = i + 1
            break
            
    # find the end of the class
    end = len(lines) - 1
    while end > start:
        if lines[end].strip() == "}":
            break
        end -= 1
        
    # extract everything inside, skipping fields
    methods_lines = []
    in_method = False
    for line in lines[start:end]:
        if "private final" in line or "@RequiredArgsConstructor" in line:
            continue
        methods_lines.append(line)
        
    # Replace public with private for the restored methods
    content = "".join(methods_lines)
    return content

clone_methods_content = extract_methods_from_class(CLONE_SERVICE)
val_methods_content = extract_methods_from_class(VAL_SERVICE)

with open(AGREEMENT_SERVICE, 'r') as f:
    impl_content = f.read()

# Revert broken method signatures
impl_content = re.sub(r'private\s+([a-zA-Z0-9_<>]+)\s+agreementValidationService\.', r'private \1 ', impl_content)
impl_content = re.sub(r'private\s+([a-zA-Z0-9_<>]+)\s+agreementCloneService\.', r'private \1 ', impl_content)
impl_content = re.sub(r'public\s+([a-zA-Z0-9_<>]+)\s+agreementValidationService\.', r'public \1 ', impl_content)
impl_content = re.sub(r'public\s+([a-zA-Z0-9_<>]+)\s+agreementCloneService\.', r'public \1 ', impl_content)

# Revert method calls
impl_content = impl_content.replace('agreementValidationService.', '')
impl_content = impl_content.replace('agreementCloneService.', '')

# Remove constructor injections
impl_content = re.sub(r'\s*private final AgreementCloneService agreementCloneService;\n', '', impl_content)
impl_content = re.sub(r'\s*private final AgreementValidationService agreementValidationService;\n', '', impl_content)
impl_content = re.sub(r'\s*AgreementCloneService agreementCloneService,\n', '', impl_content)
impl_content = re.sub(r'\s*AgreementValidationService agreementValidationService,\n', '', impl_content)
impl_content = re.sub(r'\s*this\.agreementCloneService = agreementCloneService;\n', '', impl_content)
impl_content = re.sub(r'\s*this\.agreementValidationService = agreementValidationService;\n', '', impl_content)

# Inject the extracted methods back before the last closing brace
last_brace_idx = impl_content.rfind('}')
if last_brace_idx != -1:
    impl_content = impl_content[:last_brace_idx] + clone_methods_content + "\n" + val_methods_content + "\n" + impl_content[last_brace_idx:]

# Ensure all restored public methods become private if they should be.
# Since we replaced public with private in the extract function, it's mostly handled.
# Just to be safe, replace 'public ' with 'private ' for the known validation/clone methods.
methods = [
    "copyAssetPayoutPeriods", "maybeCopyAssetPayoutPeriods", "copyDocuments",
    "copyJbpCommercialPeriods", "copyVendors", "copyRulesAndComputed", "copyPartnerLocation",
    "copySlabs", "copyJbpConfigurations",
    "validateStep1Fields", "validateStep2Fields", "validateDocumentsPresent", 
    "validateAssetRentalStep2", "validateDataFeeStep2", "validateCommercialContractsStep2",
    "validateProductsAndVendorsStep2", "validateSettlementRouting", "validateAssetRentalConfiguration",
    "validateAssetRentalPayout", "validateCommercialStructureFields", "validateCommercialContractsCommercials",
    "validateDataFeeCommercials", "validateHybridCommercials", "validateJbpMatrixPresent",
    "validateLegacySlabStructureForStep", "validateSlabStructureForSubmit", "validateQpsOneTimeFrequency",
    "validateAssetRentalPayload", "validateAdHocPayload", "validateCompleteAgreement", "validateCompleteAssetRental",
    "assertNotTerminated", "assertWithinRenewWindow", "assertRenewDates", "assertRevisionBaseVersionLock",
    "assertCommercialOverrideWhenDatesChange", "requiresExcelCommercialOverride", "hasRequiredCommercialOverride",
    "validateAgreementOwnership"
]

for m in methods:
    impl_content = re.sub(r'\bpublic\s+([a-zA-Z0-9_<>\[\]]+)\s+' + m + r'\s*\(', r'private \1 ' + m + '(', impl_content)

with open(AGREEMENT_SERVICE, 'w') as f:
    f.write(impl_content)

print("Restoration script done.")
