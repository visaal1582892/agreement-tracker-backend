import os

filepath = "/home/developer/rohitworkspace/prod/agreement-tracker/agreement-tracker-backend/src/main/java/com/medplus/agreement_tracker_backend/service/impl/AgreementServiceImpl.java"

with open(filepath, 'r') as f:
    lines = f.readlines()

new_lines = lines[:3110]
new_lines.append("}\n")

with open(filepath, 'w') as f:
    f.writelines(new_lines)

print("Truncated file successfully.")
