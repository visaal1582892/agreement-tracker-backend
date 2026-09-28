import re

file_path = "src/main/java/com/medplus/agreement_tracker_backend/service/impl/AgreementMapperService.java"
with open(file_path, "r") as f:
    content = f.read()

if "import java.util.Optional;" not in content:
    content = content.replace("import java.util.List;", "import java.util.List;\nimport java.util.Optional;")

if "import java.util.ArrayList;" not in content:
    content = content.replace("import java.util.List;", "import java.util.List;\nimport java.util.ArrayList;")

with open(file_path, "w") as f:
    f.write(content)
