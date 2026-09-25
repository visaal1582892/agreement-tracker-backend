package com.medplus.agreement_tracker_backend.integration;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class LocationLookupService {

    private final PosLocationRepository posLocationRepository;

    public List<Map<String, Object>> getCountries(String searchKey) {
        return posLocationRepository.getCountries(searchKey);
    }

    public List<Map<String, Object>> getStatesByCountryCodes(List<String> countryCodes, String searchKey) {
        if (countryCodes == null || countryCodes.isEmpty()) return List.of();
        return posLocationRepository.getStatesByCountryCodes(countryCodes, searchKey);
    }

    public List<Map<String, Object>> getCitiesByStateCodes(List<String> stateCodes, String searchKey) {
        if (stateCodes == null || stateCodes.isEmpty()) return List.of();
        return posLocationRepository.getCitiesByStateCodes(stateCodes, searchKey);
    }
}
