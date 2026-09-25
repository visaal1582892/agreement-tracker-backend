package com.medplus.agreement_tracker_backend.repository;

import com.medplus.agreement_tracker_backend.entity.AgreementComputedProduct;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;

public class AgreementComputedProductSpecification {

    public static Specification<AgreementComputedProduct> filterBy(Long agreementVersionId, String productId, String productName, String divisionName, String manufacturerName) {
        return filterBy(agreementVersionId, productId, productName, divisionName, manufacturerName, null, null);
    }

    public static Specification<AgreementComputedProduct> filterBy(
            Long agreementVersionId,
            String productId,
            String productName,
            String divisionName,
            String manufacturerName,
            String manufacturerId,
            String divisionId) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            // Always filter by agreement version id
            if (agreementVersionId != null) {
                predicates.add(cb.equal(root.get("agreementVersion").get("id"), agreementVersionId));
            }

            // Exact match for productId if numeric or an exact code
            if (productId != null && !productId.trim().isEmpty()) {
                predicates.add(cb.equal(root.get("productId"), productId.trim()));
            }

            if (productName != null && !productName.trim().isEmpty()) {
                predicates.add(cb.like(cb.lower(root.get("productNameSnapshot")), "%" + productName.trim().toLowerCase() + "%"));
            }

            if (divisionName != null && !divisionName.trim().isEmpty()) {
                predicates.add(cb.like(cb.lower(root.get("divisionNameSnapshot")), "%" + divisionName.trim().toLowerCase() + "%"));
            }

            if (manufacturerName != null && !manufacturerName.trim().isEmpty()) {
                predicates.add(cb.like(cb.lower(root.get("manufacturerNameSnapshot")), "%" + manufacturerName.trim().toLowerCase() + "%"));
            }

            if (manufacturerId != null && !manufacturerId.trim().isEmpty()) {
                predicates.add(cb.equal(root.get("manufacturerId"), manufacturerId.trim()));
            }

            if (divisionId != null && !divisionId.trim().isEmpty()) {
                predicates.add(cb.equal(root.get("divisionId"), divisionId.trim()));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}
