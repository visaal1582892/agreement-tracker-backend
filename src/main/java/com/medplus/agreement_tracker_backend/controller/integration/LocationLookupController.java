package com.medplus.agreement_tracker_backend.controller.integration;

import com.medplus.agreement_tracker_backend.integration.LocationLookupService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static com.medplus.agreement_tracker_backend.security.RightExpressions.MASTER_OR_AGREEMENT_READ;

/**
 * Exposes POS DB location data for the agreement wizard's location picker.
 *
 * <pre>
 *   GET /api/integration/locations/countries?searchKey=India
 *   GET /api/integration/locations/states?countryCodes=IN&searchKey=Andhra
 *   GET /api/integration/locations/cities?stateCodes=AP&searchKey=Vizianagaram
 * </pre>
 */
@RestController
@RequestMapping("/integration/locations")
@RequiredArgsConstructor
@PreAuthorize(MASTER_OR_AGREEMENT_READ)
public class LocationLookupController {

    private final LocationLookupService locationLookupService;

    @GetMapping("/countries")
    public ResponseEntity<List<Map<String, Object>>> getCountries(
            @RequestParam(required = false) String searchKey,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String query,
            @RequestParam(required = false) String search) {
        String key = firstNonBlank(searchKey, q, query, search);
        return ResponseEntity.ok(locationLookupService.getCountries(key));
    }

    @GetMapping("/states")
    public ResponseEntity<List<Map<String, Object>>> getStates(
            @RequestParam(required = false) List<String> countryCodes,
            @RequestParam(required = false) String countryCode,
            @RequestParam(required = false) String searchKey,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String query,
            @RequestParam(required = false) String search) {
        List<String> codes = extractCodes(countryCodes, countryCode);
        String key = firstNonBlank(searchKey, q, query, search);
        return ResponseEntity.ok(locationLookupService.getStatesByCountryCodes(codes, key));
    }

    @GetMapping("/cities")
    public ResponseEntity<List<Map<String, Object>>> getCities(
            @RequestParam(required = false) List<String> stateCodes,
            @RequestParam(required = false) String stateCode,
            @RequestParam(required = false) String searchKey,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String query,
            @RequestParam(required = false) String search) {
        List<String> codes = extractCodes(stateCodes, stateCode);
        String key = firstNonBlank(searchKey, q, query, search);
        return ResponseEntity.ok(locationLookupService.getCitiesByStateCodes(codes, key));
    }

    private String firstNonBlank(String... values) {
        for (String val : values) {
            if (val != null && !val.isBlank()) {
                return val.trim();
            }
        }
        return "";
    }

    private List<String> extractCodes(List<String> codeList, String singleCodeStr) {
        if (codeList != null && !codeList.isEmpty()) {
            return codeList.stream()
                    .flatMap(c -> Arrays.stream(c.split(",")))
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .toList();
        }
        if (singleCodeStr != null && !singleCodeStr.isBlank()) {
            return Arrays.stream(singleCodeStr.split(","))
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .toList();
        }
        return List.of();
    }
}
