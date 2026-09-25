import re
import os

BASE_DIR = "/home/developer/rohitworkspace/prod/agreement-tracker/agreement-tracker-backend/src/main/java/com/medplus/agreement_tracker_backend"
IMPL_DIR = os.path.join(BASE_DIR, "service/impl")
SERVICE_DIR = os.path.join(BASE_DIR, "service")
AGREEMENT_SERVICE_IMPL = os.path.join(IMPL_DIR, "AgreementServiceImpl.java")

with open(AGREEMENT_SERVICE_IMPL, 'r') as f:
    lines = f.readlines()

def get_method_boundaries(method_name):
    start_idx = -1
    for i, line in enumerate(lines):
        # Allow any return type, and method_name followed by (
        if re.search(r'\b(?:public|private|protected)\s+(?:[\w<>,\[\]]+\s+)+' + method_name + r'\s*\(', line):
            if not line.strip().endswith(';'):
                start_idx = i
                # walk back to get annotations
                while start_idx > 0 and (lines[start_idx-1].strip().startswith('@') or lines[start_idx-1].strip().startswith('//')):
                    start_idx -= 1
                break
    
    if start_idx == -1:
        return -1, -1

    # Find the opening brace
    brace_start = start_idx
    while brace_start < len(lines) and '{' not in lines[brace_start]:
        brace_start += 1
        
    if brace_start == len(lines):
        return -1, -1

    # Find end line
    braces = 0
    found_start = False
    for i in range(start_idx, len(lines)):
        braces += lines[i].count('{')
        braces -= lines[i].count('}')
        if lines[i].count('{') > 0:
            found_start = True
        if found_start and braces == 0:
            return start_idx, i
    return -1, -1

clone_methods = [
    "copyAssetPayoutPeriods", "maybeCopyAssetPayoutPeriods", "copyDocuments",
    "copyJbpCommercialPeriods", "copyVendors", "copyRulesAndComputed", "copyPartnerLocation",
    "copySlabs", "copyJbpConfigurations"
]

val_methods = [
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

utility_methods = [
    "validationFailure", "isAssetRentalIncomeType", "isCommercialContractsIncomeType", "sumEntityPeriodMonths",
    "isDataFeeIncomeType", "isAdHocIncomeType", "requirePartnerLocation", "resolveVendorSnapshots",
    "sumDtoPeriodMonths", "resolveEnableFlatBaseline", "resolveEnableSlabIncentives",
    "resolveAgreementDisplayName", "assertScheduledMonthsWithinAgreementDuration"
]

constants = """
    private static final String RENEW_WINDOW_MSG = "Agreement cannot be renewed outside the allowed window.";
    private static final int RENEW_WINDOW_DAYS = 90;
    private static final String RENEW_DATES_MSG = "Renewal start date must be after the current expiration date.";
    private static final String COMMERCIAL_DATE_CHANGE_MSG = "A new commercial structure must be provided because the agreement dates have been modified.";
"""

def extract_methods(method_names, replace_access=True):
    extracted = []
    indices_to_remove = set()
    for m in method_names:
        start, end = get_method_boundaries(m)
        if start != -1:
            method_lines = list(lines[start:end+1])
            if replace_access:
                for i, ml in enumerate(method_lines):
                    if re.search(r'\bprivate\b', ml):
                        method_lines[i] = re.sub(r'\bprivate\b', 'public', ml, count=1)
                        break
            extracted.append("".join(method_lines))
            indices_to_remove.update(range(start, end+1))
        else:
            print(f"Warning: method {m} not found!")
    return extracted, indices_to_remove

clone_extracted, clone_remove = extract_methods(clone_methods, replace_access=True)
val_extracted, val_remove = extract_methods(val_methods, replace_access=True)
# Do not remove utility methods from AgreementServiceImpl, just copy them
utils_extracted, _ = extract_methods(utility_methods, replace_access=False)

imports = []
for line in lines:
    if line.startswith("import "):
        imports.append(line)

package_decl = "package com.medplus.agreement_tracker_backend.service.impl;\n\n"

fields = []
in_fields = False
for line in lines:
    if "public class AgreementServiceImpl" in line:
        in_fields = True
        continue
    if in_fields:
        if "public AgreementServiceImpl(" in line:
            break
        if "private final" in line:
            fields.append(line)

clone_service_code = package_decl + "".join(imports) + """
import org.springframework.stereotype.Service;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AgreementCloneService {
""" + "".join(fields) + "\n\n" + "\n\n".join(clone_extracted) + "\n}\n"

with open(os.path.join(IMPL_DIR, "AgreementCloneService.java"), "w") as f:
    f.write(clone_service_code)

val_service_code = package_decl + "".join(imports) + """
import org.springframework.stereotype.Service;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AgreementValidationService {
""" + "".join(fields) + "\n\n" + constants + "\n\n" + "\n\n".join(val_extracted) + "\n\n" + "\n\n".join(utils_extracted) + "\n}\n"

with open(os.path.join(IMPL_DIR, "AgreementValidationService.java"), "w") as f:
    f.write(val_service_code)

new_lines = []
for i, line in enumerate(lines):
    if i not in clone_remove and i not in val_remove:
        new_lines.append(line)

final_lines = []
added_fields = False
added_params = False
added_assignments = False

for line in new_lines:
    if "private static final DateTimeFormatter" in line and not added_fields:
        final_lines.append("    private final AgreementCloneService agreementCloneService;\n")
        final_lines.append("    private final AgreementValidationService agreementValidationService;\n\n")
        added_fields = True
    
    if "PlatformTransactionManager transactionManager," in line and not added_params:
        final_lines.append("            AgreementCloneService agreementCloneService,\n")
        final_lines.append("            AgreementValidationService agreementValidationService,\n")
        added_params = True

    if "this.groupSubmitTransactionTemplate" in line and not added_assignments:
        final_lines.append("        this.agreementCloneService = agreementCloneService;\n")
        final_lines.append("        this.agreementValidationService = agreementValidationService;\n")
        added_assignments = True

    final_lines.append(line)

impl_code = "".join(final_lines)

# Only replace method calls, NOT method declarations or inside other strings.
# We do this by ensuring the match is a method call: preceded by non-alphanumeric (like space or dot), followed by (
for m in clone_methods:
    impl_code = re.sub(r'(?<!\w)' + m + r'\s*\(', f'agreementCloneService.{m}(', impl_code)

for m in val_methods:
    impl_code = re.sub(r'(?<!\w)' + m + r'\s*\(', f'agreementValidationService.{m}(', impl_code)

with open(AGREEMENT_SERVICE_IMPL, "w") as f:
    f.write(impl_code)

print("Refactoring complete.")
