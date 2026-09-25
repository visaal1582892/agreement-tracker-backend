package com.medplus.agreement_tracker_backend.integration.dto;

import com.medplus.agreement_tracker_backend.integration.dto.external.ExternalProductItem;

import java.util.List;

public record ProductPageResult(List<ExternalProductItem> items, long totalElements) {
}
