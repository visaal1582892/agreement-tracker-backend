package com.medplus.agreement_tracker_backend.service.impl;

import com.medplus.agreement_tracker_backend.dto.request.GroupDeletionRequest;
import com.medplus.agreement_tracker_backend.dto.request.UpdateAgreementGroupRequest;
import com.medplus.agreement_tracker_backend.dto.response.AgreementGroupResponse;
import com.medplus.agreement_tracker_backend.dto.response.GroupDeletionStatusResponse;
import com.medplus.agreement_tracker_backend.entity.Agreement;
import com.medplus.agreement_tracker_backend.entity.AgreementActionRequest;
import com.medplus.agreement_tracker_backend.entity.AgreementAudit;
import com.medplus.agreement_tracker_backend.entity.AgreementGroup;
import com.medplus.agreement_tracker_backend.entity.AgreementVersion;
import com.medplus.agreement_tracker_backend.entity.User;
import com.medplus.agreement_tracker_backend.enums.ActionRequestStatus;
import com.medplus.agreement_tracker_backend.enums.ActionRequestType;
import com.medplus.agreement_tracker_backend.enums.AgreementStatus;
// import com.medplus.agreement_tracker_backend.enums.ApprovalStatus;
import com.medplus.agreement_tracker_backend.enums.GroupDeletionStatus;
import com.medplus.agreement_tracker_backend.exception.BusinessException;
import com.medplus.agreement_tracker_backend.exception.ResourceNotFoundException;
import com.medplus.agreement_tracker_backend.repository.AgreementActionRequestRepository;
import com.medplus.agreement_tracker_backend.repository.AgreementAuditRepository;
import com.medplus.agreement_tracker_backend.repository.AgreementGroupRepository;
import com.medplus.agreement_tracker_backend.repository.AgreementGroupSpec;
import com.medplus.agreement_tracker_backend.repository.AgreementRepository;
import com.medplus.agreement_tracker_backend.repository.AgreementVersionRepository;
import com.medplus.agreement_tracker_backend.repository.UserRepository;
import com.medplus.agreement_tracker_backend.service.AgreementGroupService;
import com.medplus.agreement_tracker_backend.util.AgreementStatusResolver;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;

@Service
@RequiredArgsConstructor
public class AgreementGroupServiceImpl implements AgreementGroupService {

    private final AgreementGroupRepository agreementGroupRepository;
    private final AgreementRepository agreementRepository;
    private final AgreementVersionRepository agreementVersionRepository;
    private final AgreementActionRequestRepository actionRequestRepository;
    private final AgreementAuditRepository auditRepository;
    private final UserRepository userRepository;
    private final AgreementStatusResolver agreementStatusResolver;

    @Override
    @Transactional(readOnly = true)
    public Page<AgreementGroupResponse> listAll(
            Pageable pageable, Boolean isActive, String groupName,
            String lastModifiedBy, String createdBy, Long currentUserId, boolean canViewAll,
            boolean isApprover, boolean isAccountManager) {
        Specification<AgreementGroup> spec = AgreementGroupSpec.withFilters(
                isActive, groupName, lastModifiedBy, createdBy);
        if (!canViewAll) {
            spec = spec.and(AgreementGroupSpec.visibleTo(currentUserId));
        }
        return agreementGroupRepository.findAll(spec, pageable)
                .map(group -> toResponse(group, currentUserId, isApprover, isAccountManager));
    }

    @Override
    @Transactional(readOnly = true)
    public AgreementGroupResponse getById(Long groupId, Long currentUserId, boolean canViewAll,
            boolean isApprover, boolean isAccountManager) {
        AgreementGroup group = loadGroup(groupId);
        if (!canViewAll) {
            enforceGroupVisibility(group, currentUserId);
        }
        return toResponse(group, currentUserId, isApprover, isAccountManager);
    }

    @Override
    @Transactional
    public AgreementGroupResponse resolveOrCreate(Long groupId, String newName, Long userId) {
        if (groupId != null) {
            AgreementGroup existing = loadGroup(groupId);
            if (!existing.isActive()) {
                throw new BusinessException("Cannot add agreements to an inactive group.");
            }
            return toResponse(existing, userId, false, false);
        }

        if (!StringUtils.hasText(newName)) {
            throw new BusinessException("Either agreementGroupId or newAgreementGroupName is required");
        }

        String trimmedName = newName.trim();
        return agreementGroupRepository.findByNameIgnoreCase(trimmedName)
                .map(group -> toResponse(group, userId, false, false))
                .orElseGet(() -> {
                    if (agreementGroupRepository.existsByNameIgnoreCase(trimmedName)) {
                        throw new BusinessException("Agreement group name already exists");
                    }
                    AgreementGroup created = AgreementGroup.builder()
                            .name(trimmedName)
                            .isActive(true)
                            .build();
                    created.setCreatedByUserId(userId);
                    return toResponse(agreementGroupRepository.save(created), userId, false, false);
                });
    }

    @Override
    @Transactional(readOnly = true)
    public GroupDeletionStatusResponse getDeletionStatus(Long groupId, Long currentUserId,
            boolean isApprover, boolean isAccountManager) {
        AgreementGroup group = loadGroup(groupId);
        enforceDeleteAuthorization(group, currentUserId, isApprover, isAccountManager);

        if (!areAllAgreementsDeletable(groupId)) {
            return new GroupDeletionStatusResponse(GroupDeletionStatus.HAS_ACTIVE);
        }

        if (isApprover) {
            return new GroupDeletionStatusResponse(GroupDeletionStatus.READY);
        }
        if (isAccountManager && isCreator(group, currentUserId)) {
            if (agreementRepository.countByAgreementGroupId(groupId) == 0) {
                return new GroupDeletionStatusResponse(GroupDeletionStatus.READY);
            }
            return new GroupDeletionStatusResponse(GroupDeletionStatus.REQUIRES_APPROVAL);
        }

        throw new AccessDeniedException(
                "Only an approver or the account manager who created this group can delete it");
    }

    @Override
    @Transactional
    public void deleteGroupImmediately(Long groupId, String reason, Long currentUserId,
            boolean isApprover, boolean isAccountManager) {
        AgreementGroup group = loadGroup(groupId);
        enforceDeleteAuthorization(group, currentUserId, isApprover, isAccountManager);

        if (!computeCanDelete(group, currentUserId, isApprover, isAccountManager)) {
            throw new BusinessException("Cannot delete group: Active agreements exist.");
        }

        GroupDeletionStatus status = getDeletionStatus(groupId, currentUserId, isApprover, isAccountManager).status();
        if (status == GroupDeletionStatus.REQUIRES_APPROVAL) {
            throw new BusinessException("Group deletion requires approval. Submit a deletion request instead.");
        }
        if (status == GroupDeletionStatus.HAS_ACTIVE) {
            throw new BusinessException("Cannot delete group: Active agreements exist.");
        }

        validateReason(reason);
        performGroupDeletion(group, reason.trim(), currentUserId);
    }

    @Override
    @Transactional
    public void submitDeletionRequest(Long groupId, GroupDeletionRequest request, Long currentUserId,
            boolean isApprover, boolean isAccountManager) {
        AgreementGroup group = loadGroup(groupId);
        enforceCreatorForDeletionRequest(group, currentUserId);

        GroupDeletionStatus status = getDeletionStatus(
                groupId, currentUserId, isApprover, isAccountManager).status();
        if (status != GroupDeletionStatus.REQUIRES_APPROVAL) {
            throw new BusinessException("This group does not require a deletion approval request");
        }

        validateNoPendingGroupDeletionRequest(groupId);
        validateReason(request.reason());

        User requestedBy = userRepository.findById(currentUserId)
                .orElseThrow(() -> new ResourceNotFoundException("User", currentUserId));

        AgreementActionRequest actionRequest = AgreementActionRequest.builder()
                .agreementGroup(group)
                .actionType(ActionRequestType.DELETE_GROUP)
                .status(ActionRequestStatus.PENDING)
                .requestedBy(requestedBy)
                .reasonComments(request.reason().trim())
                .build();
        actionRequestRepository.save(actionRequest);
    }

    @Override
    @Transactional
    public void executeApprovedGroupDeletion(Long groupId, Long approverId, String reason) {
        AgreementGroup group = loadGroup(groupId);
        if (!areAllAgreementsDeletable(groupId)) {
            throw new BusinessException("Cannot delete group: Active agreements exist.");
        }

        String auditReason = StringUtils.hasText(reason) ? reason.trim() : "Approved group deletion";
        performGroupDeletion(group, auditReason, approverId);
    }

    @Override
    @Transactional
    public AgreementGroupResponse renameGroup(Long groupId, UpdateAgreementGroupRequest request, Long currentUserId) {
        AgreementGroup group = loadGroup(groupId);
        String trimmedName = request.name().trim();

        if (!group.getName().equalsIgnoreCase(trimmedName)
                && agreementGroupRepository.existsByNameIgnoreCase(trimmedName)) {
            throw new BusinessException("Agreement group name already exists");
        }

        group.setName(trimmedName);
        group.setUpdatedByUserId(currentUserId);
        return toResponse(agreementGroupRepository.save(group), currentUserId, false, false);
    }

    private void performGroupDeletion(AgreementGroup group, String reason, Long performedByUserId) {
        Long groupId = group.getId();
        if (!areAllAgreementsDeletable(groupId)) {
            throw new BusinessException("Cannot delete group: Active agreements exist.");
        }

        group.setActive(false);
        group.setUpdatedByUserId(performedByUserId);
        agreementGroupRepository.save(group);

        recordGroupAudit(groupId, reason, performedByUserId);
    }

    private void recordGroupAudit(Long groupId, String reason, Long userId) {
        AgreementAudit audit = AgreementAudit.builder()
                .entityType("AgreementGroup")
                .entityId(groupId)
                .action("GROUP_DELETED")
                .newValueJson(reason)
                .createdByUserId(userId)
                .build();
        auditRepository.save(audit);
    }

    private void validateNoPendingGroupDeletionRequest(Long groupId) {
        actionRequestRepository.findFirstByAgreementGroup_IdAndStatus(groupId, ActionRequestStatus.PENDING)
                .ifPresent(existing -> {
                    throw new BusinessException("A pending group deletion request already exists");
                });
    }

    private void validateReason(String reason) {
        if (!StringUtils.hasText(reason)) {
            throw new BusinessException("Reason for deletion is required");
        }
    }

    private boolean computeCanDelete(AgreementGroup group, Long currentUserId,
            boolean isApprover, boolean isAccountManager) {
        if (!hasDeleteRole(group, currentUserId, isApprover, isAccountManager)) {
            return false;
        }
        return areAllAgreementsDeletable(group.getId());
    }

    private boolean hasDeleteRole(AgreementGroup group, Long currentUserId,
            boolean isApprover, boolean isAccountManager) {
        if (isApprover) {
            return true;
        }
        return isAccountManager && isCreator(group, currentUserId);
    }

    private boolean areAllAgreementsDeletable(Long groupId) {
        List<Agreement> agreements = agreementRepository.findByAgreementGroupId(groupId);
        if (agreements.isEmpty()) {
            return true;
        }
        for (Agreement agreement : agreements) {
            if (!isAgreementDeletable(agreement)) {
                return false;
            }
        }
        return true;
    }

    private boolean isAgreementDeletable(Agreement agreement) {
        AgreementVersion version = resolveAgreementVersionForStatus(agreement);
        if (version == null) {
            return true;
        }
        AgreementStatus status = agreementStatusResolver.resolve(version);
        return status == AgreementStatus.EXPIRED || status == AgreementStatus.TERMINATED;
    }

    private AgreementVersion resolveAgreementVersionForStatus(Agreement agreement) {
        if (agreement.getCurrentVersionId() != null) {
            return agreementVersionRepository.findById(agreement.getCurrentVersionId()).orElse(null);
        }
        List<AgreementVersion> versions = agreementVersionRepository.findByAgreementId(agreement.getId());
        if (versions.isEmpty()) {
            return null;
        }
        return versions.stream()
                .max((left, right) -> Integer.compare(left.getVersionNumber(), right.getVersionNumber()))
                .orElse(null);
    }

    private void enforceGroupVisibility(AgreementGroup group, Long currentUserId) {
        if (isCreator(group, currentUserId)) {
            return;
        }
        if (agreementRepository.existsByAgreementGroupIdAndOwner_Id(group.getId(), currentUserId)) {
            return;
        }
        throw new AccessDeniedException("You do not have access to this group");
    }

    private void enforceDeleteAuthorization(AgreementGroup group, Long currentUserId,
            boolean isApprover, boolean isAccountManager) {
        if (!hasDeleteRole(group, currentUserId, isApprover, isAccountManager)) {
            throw new AccessDeniedException(
                    "Only an approver or the account manager who created this group can delete it");
        }
    }

    private void enforceCreatorForDeletionRequest(AgreementGroup group, Long currentUserId) {
        if (!isCreator(group, currentUserId)) {
            throw new AccessDeniedException("Only the group creator can submit a deletion request");
        }
    }

    private boolean isCreator(AgreementGroup group, Long userId) {
        return group.getCreatedByUserId() != null && group.getCreatedByUserId().equals(userId);
    }

    private AgreementGroup loadGroup(Long groupId) {
        return agreementGroupRepository.findById(groupId)
                .orElseThrow(() -> new ResourceNotFoundException("AgreementGroup", groupId));
    }

    private AgreementGroupResponse toResponse(AgreementGroup group, Long currentUserId,
            boolean isApprover, boolean isAccountManager) {
        Long modifierUserId = group.getUpdatedByUserId() != null
                ? group.getUpdatedByUserId()
                : group.getCreatedByUserId();
        String modifierName = resolveUserName(modifierUserId);

        return new AgreementGroupResponse(
                group.getId(),
                group.getName(),
                group.isActive(),
                group.getCreatedByUserId(),
                resolveUserName(group.getCreatedByUserId()),
                group.getUpdatedAt(),
                modifierName,
                computeCanDelete(group, currentUserId, isApprover, isAccountManager));
    }

    private String resolveUserName(Long userId) {
        if (userId == null) {
            return null;
        }
        return userRepository.findById(userId).map(User::getFullName).orElse(null);
    }
}
