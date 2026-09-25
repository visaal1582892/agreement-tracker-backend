sed -i '/public ResponseEntity<List<IntegrationManufacturerResponse>> searchManufacturers/i \
    @GetMapping("/manufacturers/by-ids")\
    @PreAuthorize(MASTER_OR_AGREEMENT_READ)\
    public ResponseEntity<List<IntegrationManufacturerResponse>> getManufacturersByIds(\
            @RequestParam("ids") String ids) {\
        List<Long> manufacturerIds = java.util.Arrays.stream(ids.split(","))\
                .map(String::trim)\
                .filter(val -> !val.isEmpty())\
                .map(Long::valueOf)\
                .toList();\
        return ResponseEntity.ok(new java.util.ArrayList<>(integrationService.getManufacturersByIds(manufacturerIds).values()));\
    }\
\
    @GetMapping("/divisions/by-ids")\
    @PreAuthorize(MASTER_OR_AGREEMENT_READ)\
    public ResponseEntity<List<IntegrationDivisionResponse>> getDivisionsByIds(\
            @RequestParam("manufacturerIds") String manufacturerIdsStr,\
            @RequestParam("ids") String ids) {\
        List<Long> manufacturerIds = java.util.Arrays.stream(manufacturerIdsStr.split(","))\
                .map(String::trim).filter(v -> !v.isEmpty()).map(Long::valueOf).toList();\
        List<Long> divisionIds = java.util.Arrays.stream(ids.split(","))\
                .map(String::trim).filter(v -> !v.isEmpty()).map(Long::valueOf).toList();\
        List<IntegrationDivisionResponse> allDivisions = integrationService.getDivisionsByManufacturerIds(manufacturerIds);\
        List<IntegrationDivisionResponse> filtered = allDivisions.stream()\
                .filter(d -> divisionIds.contains(d.getId()))\
                .toList();\
        return ResponseEntity.ok(filtered);\
    }\
' src/main/java/com/medplus/agreement_tracker_backend/controller/integration/ProductIntegrationController.java
bash patch_controller.sh
