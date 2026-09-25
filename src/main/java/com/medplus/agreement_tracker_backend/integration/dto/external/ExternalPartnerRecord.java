package com.medplus.agreement_tracker_backend.integration.dto.external;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class ExternalPartnerRecord {
    private Long accountId;
    private String name;
    private String accountType;
    private String state;
    private Company company;

    public boolean isSupplierAccount() {
        return "SUPPLIER_ACCOUNT_TYPE".equals(accountType);
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Company {
        private String state;
        private String city;
    }
}
