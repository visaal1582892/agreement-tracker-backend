package com.medplus.agreement_tracker_backend.integration;

import com.medplus.agreement_tracker_backend.integration.dto.StoreDetails;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class CompanyDatabaseService {

    private final CompanyDatabaseRepository companyDatabaseRepository;

    public Optional<StoreDetails> getStoreDetails(String storeId) {
        return companyDatabaseRepository.findStoreById(storeId);
    }

    public List<StoreDetails> getAllStores() {
        return companyDatabaseRepository.findAllStores();
    }
}
