package com.medplus.agreement_tracker_backend.integration.dto.external;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Fields exposed by the Product Microservice Solr API for column selection
 * via the {@code requiredColumns} parameter in {@code /product/get-products}.
 *
 * JSON names use camelCase matching the Solr API field names.
 * Use {@link #getJsonName()} to get the JSON representation.
 */
public enum ProductFieldsEnum {
    productId,
    productName,
    manufacturerId,
    manufacturerName,
    divisionId,
    divisionName,
    categoryId,
    l3Category,
    mrp,
    cp,
    status;

    /**
     * Returns the JSON field name for Solr API requests.
     */
    public String getJsonName() {
        return name();
    }
}
