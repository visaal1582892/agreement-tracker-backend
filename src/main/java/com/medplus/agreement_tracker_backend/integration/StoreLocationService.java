package com.medplus.agreement_tracker_backend.integration;

import com.medplus.agreement_tracker_backend.integration.dto.StoreLocationDetails;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class StoreLocationService {

    private final StoreLocationRepository storeLocationRepository;

    public List<StoreLocationDetails> getActiveStoreLocations(Set<String> storeIds) {
        return storeLocationRepository.findActiveStoresByIds(storeIds);
    }

    public Page<StoreLocationDetails> searchActiveStoreLocations(Set<String> storeIds, String search, Pageable pageable) {
        if (storeIds == null || storeIds.isEmpty()) {
            return new PageImpl<>(Collections.emptyList(), pageable, 0);
        }

        long total = storeLocationRepository.countActiveStoresByIds(storeIds, search);
        if (total == 0) {
            return new PageImpl<>(Collections.emptyList(), pageable, 0);
        }

        List<StoreLocationDetails> content = storeLocationRepository.searchActiveStoresByIds(
                storeIds, search, (int) pageable.getOffset(), pageable.getPageSize());
        
        return new PageImpl<>(content, pageable, total);
    }
}
