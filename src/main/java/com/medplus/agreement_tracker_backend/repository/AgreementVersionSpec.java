package com.medplus.agreement_tracker_backend.repository;

import com.medplus.agreement_tracker_backend.entity.Agreement;
import com.medplus.agreement_tracker_backend.entity.AgreementVendor;
import com.medplus.agreement_tracker_backend.entity.AgreementVersion;
import com.medplus.agreement_tracker_backend.entity.User;
import com.medplus.agreement_tracker_backend.enums.ApprovalStatus;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Subquery;
import jakarta.persistence.criteria.Root;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

public final class AgreementVersionSpec {

    private AgreementVersionSpec() {
    }

    public static Specification<AgreementVersion> withFilters(
            Long currentUserId, boolean canViewAll, String scope,
            Long agreementGroupId, String agreementGroupName,
            String agreementName, String status, String ownerName,
            String vendorName, Long incomeTypeId,
            LocalDate startDateFrom, LocalDate startDateTo,
            LocalDate endDateFrom, LocalDate endDateTo) {

        return (root, query, cb) -> {
            query.distinct(true);

            List<Predicate> predicates = new ArrayList<>();
            Join<AgreementVersion, Agreement> agreement = root.join("agreement", JoinType.INNER);

            // 1. Status Logic
            if (StringUtils.hasText(status)) {
                String upperStatus = status.toUpperCase();
                LocalDate now = LocalDate.now();
                switch (upperStatus) {
                    case "ACTIVE":
                        predicates.add(cb.equal(root.get("approvalStatus"), ApprovalStatus.APPROVED));
                        predicates.add(cb.lessThanOrEqualTo(root.get("startDate"), now));
                        predicates.add(cb.greaterThanOrEqualTo(root.get("expiryDate"), now));
                        predicates.add(cb.isNull(root.get("terminationDate")));
                        predicates.add(cb.isFalse(root.get("inProgressFlag")));
                        break;
                    case "TERMINATED":
                        predicates.add(cb.or(
                                cb.isNotNull(root.get("terminationDate")),
                                cb.equal(root.get("approvalStatus"), ApprovalStatus.TERMINATED)
                        ));
                        break;
                    case "EXPIRED":
                        predicates.add(cb.equal(root.get("approvalStatus"), ApprovalStatus.APPROVED));
                        predicates.add(cb.lessThan(root.get("expiryDate"), now));
                        predicates.add(cb.isNull(root.get("terminationDate")));
                        
                        Subquery<Long> currentVersionSub = query.subquery(Long.class);
                        Root<Agreement> currentVersionRoot = currentVersionSub.from(Agreement.class);
                        currentVersionSub.select(currentVersionRoot.get("currentVersionId"))
                                .where(cb.equal(currentVersionRoot.get("id"), agreement.get("id")));
                        predicates.add(cb.equal(root.get("id"), currentVersionSub));
                        break;
                    case "UPCOMING":
                        predicates.add(cb.equal(root.get("approvalStatus"), ApprovalStatus.APPROVED));
                        predicates.add(cb.greaterThan(root.get("startDate"), now));
                        predicates.add(cb.isNull(root.get("terminationDate")));
                        break;
                    case "IN_PROGRESS":
                        predicates.add(cb.equal(root.get("approvalStatus"), ApprovalStatus.APPROVED));
                        predicates.add(cb.isTrue(root.get("inProgressFlag")));
                        predicates.add(cb.isNull(root.get("terminationDate")));
                        break;
                    default:
                        try {
                            ApprovalStatus parsedStatus = ApprovalStatus.valueOf(upperStatus);
                            predicates.add(cb.equal(root.get("approvalStatus"), parsedStatus));
                        } catch (IllegalArgumentException e) {
                            predicates.add(cb.equal(root.get("id"), -1L)); // Impossible condition
                        }
                        break;
                }
            } else {
                // No status provided: Latest non-draft version
                Subquery<Integer> subquery = query.subquery(Integer.class);
                Root<AgreementVersion> subRoot = subquery.from(AgreementVersion.class);
                
                subquery.select(cb.max(subRoot.get("versionNumber")))
                        .where(
                                cb.equal(subRoot.get("agreement").get("id"), agreement.get("id")),
                                cb.notEqual(subRoot.get("approvalStatus"), ApprovalStatus.DRAFT)
                        );
                
                predicates.add(cb.equal(root.get("versionNumber"), subquery));
                // We also only want non-drafts returned if no status is provided
                predicates.add(cb.notEqual(root.get("approvalStatus"), ApprovalStatus.DRAFT));
            }

            // 2. RBAC Logic
            boolean isDraft = "DRAFT".equalsIgnoreCase(status);
            boolean isMyScope = "MY".equalsIgnoreCase(scope);

            if ((isDraft && !canViewAll) || isMyScope) {
                predicates.add(cb.equal(agreement.get("owner").get("id"), currentUserId));
            }
            
            // 3. Parent Filters (Agreement)
            if (StringUtils.hasText(agreementName)) {
                predicates.add(cb.like(cb.lower(agreement.get("agreementName")), "%" + agreementName.toLowerCase() + "%"));
            }
            if (agreementGroupId != null) {
                predicates.add(cb.equal(agreement.get("agreementGroup").get("id"), agreementGroupId));
            }
            if (StringUtils.hasText(agreementGroupName)) {
                Join<Object, Object> group = agreement.join("agreementGroup", JoinType.INNER);
                predicates.add(cb.like(cb.lower(group.get("name")), "%" + agreementGroupName.toLowerCase() + "%"));
            }
            if (StringUtils.hasText(ownerName)) {
                Join<Agreement, User> owner = agreement.join("owner", JoinType.INNER);
                predicates.add(cb.like(cb.lower(owner.get("fullName")), "%" + ownerName.toLowerCase() + "%"));
            }

            // 4. Version Filters (AgreementVersion)
            if (incomeTypeId != null) {
                predicates.add(cb.equal(root.get("incomeType").get("id"), incomeTypeId));
            }
            if (startDateFrom != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("startDate"), startDateFrom));
            }
            if (startDateTo != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("startDate"), startDateTo));
            }
            if (endDateFrom != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("expiryDate"), endDateFrom));
            }
            if (endDateTo != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("expiryDate"), endDateTo));
            }
            
            // Vendor Filter
            if (StringUtils.hasText(vendorName)) {
                Subquery<Integer> vendorSub = query.subquery(Integer.class);
                Root<AgreementVendor> vendorSubRoot = vendorSub.from(AgreementVendor.class);
                vendorSub.select(cb.literal(1))
                         .where(
                                 cb.equal(vendorSubRoot.get("agreementVersion"), root),
                                 cb.like(cb.lower(vendorSubRoot.get("vendorNameSnapshot")), "%" + vendorName.toLowerCase() + "%")
                         );
                predicates.add(cb.exists(vendorSub));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}
