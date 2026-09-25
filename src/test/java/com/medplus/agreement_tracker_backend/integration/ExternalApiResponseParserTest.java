package com.medplus.agreement_tracker_backend.integration;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.medplus.agreement_tracker_backend.integration.dto.external.ExternalApiResponseWrapper;
import com.medplus.agreement_tracker_backend.integration.dto.external.ExternalDivisionItem;
import com.medplus.agreement_tracker_backend.integration.dto.external.ExternalManufacturerSearchItem;
import com.medplus.agreement_tracker_backend.integration.dto.external.ExternalManufacturerWithDivisions;
import com.medplus.agreement_tracker_backend.integration.dto.external.ExternalProductItem;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class ExternalApiResponseParserTest {

    private ExternalApiResponseParser parser;

    @BeforeEach
    void setUp() {
        parser = new ExternalApiResponseParser(new ObjectMapper());
    }

    @Test
    void parseManufacturerSearchPayload_fromStringifiedArray() {
        ExternalApiResponseWrapper wrapper = new ExternalApiResponseWrapper();
        wrapper.setResponseStatus("SUCCESS");
        wrapper.setResponse("[{\"manufacturerId\":12,\"manufacturerName\":\"ADAMS\",\"status\":\"ACTIVE\"}]");

        List<ExternalManufacturerSearchItem> items = parser.parsePayloadList(
                wrapper, new TypeReference<>() {});

        assertFalse(items.isEmpty());
        assertEquals(12L, items.get(0).getManufacturerId());
        assertEquals("ADAMS", items.get(0).getManufacturerName());
        assertEquals("ACTIVE", items.get(0).getStatus());
    }

    @Test
    void parseManufacturerDivisionsPayload_fromWrappedObject() {
        ExternalApiResponseWrapper wrapper = new ExternalApiResponseWrapper();
        wrapper.setResponseStatus("SUCCESS");
        wrapper.setResponse(
                "{\"manufacturers\":[{\"manufacturerId\":122,\"manfacturer\":\"CADILA PHARMACEUTICALS LTD\","
                        + "\"manufacturerDivisions\":[{\"manufacturerDivisionId\":1178,\"division\":\"CK\",\"status\":\"ACTIVE\"}]}]}");

        List<ExternalManufacturerWithDivisions> items = parser.parseManufacturerDivisionsList(wrapper);

        assertEquals(1, items.size());
        assertEquals(122L, items.get(0).getManufacturerId());
        assertEquals("CADILA PHARMACEUTICALS LTD", items.get(0).resolvedManufacturerName());
        List<ExternalDivisionItem> divisions = items.get(0).resolvedDivisions();
        assertEquals(1, divisions.size());
        assertEquals(1178L, divisions.get(0).resolvedDivisionId());
        assertEquals("CK", divisions.get(0).resolvedDivisionName());
    }

    @Test
    void parseProductPayload_fromStringifiedArray() {
        ExternalApiResponseWrapper wrapper = new ExternalApiResponseWrapper();
        wrapper.setResponseStatus("SUCCESS");
        wrapper.setResponse("[{\"productId\":\"IBR001\",\"productName\":\"Test Product\",\"status\":\"ACTIVE\"}]");

        List<ExternalProductItem> items = parser.parseProductList(wrapper);

        assertEquals(1, items.size());
        assertEquals("IBR001", items.get(0).getProductId());
        assertEquals("Test Product", items.get(0).getProductName());
    }

    @Test
    void parseProductPayload_fromWrappedObject() {
        ExternalApiResponseWrapper wrapper = new ExternalApiResponseWrapper();
        wrapper.setResponseStatus("SUCCESS");
        wrapper.setResponse("{\"productList\":[{\"productId\":\"CADI0003\",\"name\":\"CADITOR 10MG 30S TAB\","
                + "\"manufacturerId\":122,\"manufacturerDivisionId\":1178,\"manufacturer\":\"CADILA PHARMACEUTICALS LTD\","
                + "\"manufactureDivisionName\":\"CK\",\"status\":\"ACTIVE\"}]}");

        List<ExternalProductItem> items = parser.parseProductList(wrapper);

        assertEquals(1, items.size());
        assertEquals("CADI0003", items.get(0).getProductId());
        assertEquals("CADITOR 10MG 30S TAB", items.get(0).resolvedProductName());
        assertEquals(1178L, items.get(0).resolvedDivisionId());
        assertEquals("CK", items.get(0).resolvedDivisionName());
    }
}
