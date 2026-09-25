package com.medplus.agreement_tracker_backend.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.medplus.agreement_tracker_backend.enums.ProductMasterStatusEnum;
import com.medplus.agreement_tracker_backend.integration.dto.external.ExternalGetManufacturerRequest;
import com.medplus.agreement_tracker_backend.integration.dto.external.ExternalProductSearchRequest;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

class ExternalRequestSerializationTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void getManufacturerRequest_serializesIsDivisionRequiredFieldName() throws Exception {
        ExternalGetManufacturerRequest request = ExternalGetManufacturerRequest.builder()
                .manufacturerIds(List.of(122L))
                .manufacturerStatus(ProductMasterStatusEnum.ACTIVE)
                .isDivisionRequired(true)
                .build();

        String json = objectMapper.writeValueAsString(request);
        assertTrue(json.contains("\"isDivisionRequired\":true"), "Expected isDivisionRequired in JSON but got: " + json);
    }

    @Test
    void productSearchRequest_serializesBooleanFlagFieldNames() throws Exception {
        ExternalProductSearchRequest request = ExternalProductSearchRequest.builder()
                .manufacturerIdList(List.of(122L))
                .manufacturerDivisionId(List.of(1178L))
                .searchKey("")
                .isRunOnDb(false)
                .isDetailsRequired(true)
                .productStatus(ProductMasterStatusEnum.ACTIVE)
                .build();

        String json = objectMapper.writeValueAsString(request);
        assertTrue(json.contains("\"isRunOnDb\":false"), "Expected isRunOnDb in JSON but got: " + json);
        assertTrue(json.contains("\"isDetailsRequired\":true"), "Expected isDetailsRequired in JSON but got: " + json);
    }
}
