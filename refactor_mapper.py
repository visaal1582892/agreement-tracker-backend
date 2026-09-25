import os
import re

BASE_DIR = "/home/developer/rohitworkspace/prod/agreement-tracker/agreement-tracker-backend/src/main/java/com/medplus/agreement_tracker_backend"
IMPL_DIR = os.path.join(BASE_DIR, "service/impl")
AGREEMENT_SERVICE_IMPL = os.path.join(IMPL_DIR, "AgreementServiceImpl.java")

with open(AGREEMENT_SERVICE_IMPL, 'r') as f:
    lines = f.readlines()

def get_method_boundaries(method_name):
    start_idx = -1
    for i, line in enumerate(lines):
        if re.search(r'\b(?:public|private|protected)\s+(?:[\w<>,\[\]]+\s+)+' + method_name + r'\s*\(', line):
            if not line.strip().endswith(';'):
                start_idx = i
                while start_idx > 0 and (lines[start_idx-1].strip().startswith('@') or lines[start_idx-1].strip().startswith('//')):
                    start_idx -= 1
                break
    
    if start_idx == -1:
        return -1, -1

    brace_start = start_idx
    while brace_start < len(lines) and '{' not in lines[brace_start]:
        brace_start += 1
        
    if brace_start == len(lines):
        return -1, -1

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

mapper_methods = [
    "toVersionResponse", "toParentResponse", "mapActionRequestToTimeline",
    "resolvePendingActionRequest", "resolveTerminalStatus", "toAssetSummary",
    "toParentResponseEmpty", "resolveListDisplayVersion", "mapAgreementPageable",
    "resolveVisibleLatest", "resolveUserName"
]

utility_methods = [
    "isAssetRentalIncomeType"
]

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

mapper_extracted, mapper_remove = extract_methods(mapper_methods, replace_access=True)
utils_extracted, _ = extract_methods(utility_methods, replace_access=False)

imports = [line for line in lines if line.startswith("import ")]
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

mapper_service_code = package_decl + "".join(imports) + """
import org.springframework.stereotype.Service;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AgreementMapperService {
""" + "".join(fields) + "\n\n" + "\n\n".join(mapper_extracted) + "\n\n" + "\n\n".join(utils_extracted) + "\n}\n"

with open(os.path.join(IMPL_DIR, "AgreementMapperService.java"), "w") as f:
    f.write(mapper_service_code)

new_lines = []
for i, line in enumerate(lines):
    if i not in mapper_remove:
        new_lines.append(line)

final_lines = []
added_fields = False
added_params = False
added_assignments = False

for line in new_lines:
    if "private static final DateTimeFormatter" in line and not added_fields:
        final_lines.append("    private final AgreementMapperService agreementMapperService;\n")
        added_fields = True
    
    if "PlatformTransactionManager transactionManager," in line and not added_params:
        final_lines.append("            AgreementMapperService agreementMapperService,\n")
        added_params = True

    if "this.groupSubmitTransactionTemplate" in line and not added_assignments:
        final_lines.append("        this.agreementMapperService = agreementMapperService;\n")
        added_assignments = True

    final_lines.append(line)

impl_code = "".join(final_lines)

for m in mapper_methods:
    impl_code = re.sub(r'(?<!\w)' + m + r'\s*\(', f'agreementMapperService.{m}(', impl_code)

with open(AGREEMENT_SERVICE_IMPL, "w") as f:
    f.write(impl_code)

print(f"Extracted {len(mapper_extracted)} mapper methods.")
