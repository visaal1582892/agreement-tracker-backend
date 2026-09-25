package com.medplus.agreement_tracker_backend.service;

import com.medplus.agreement_tracker_backend.dto.request.GroupDeletionRequest;
import com.medplus.agreement_tracker_backend.dto.request.UpdateAgreementGroupRequest;
import com.medplus.agreement_tracker_backend.dto.response.AgreementGroupResponse;
import com.medplus.agreement_tracker_backend.dto.response.GroupDeletionStatusResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface AgreementGroupService {

    Page<AgreementGroupResponse> listAll(
            Pageable pageable, Boolean isActive, String groupName,
            String lastModifiedBy, String createdBy, Long currentUserId, boolean canViewAll,
            boolean isApprover, boolean isAccountManager);

    AgreementGroupResponse getById(Long groupId, Long currentUserId, boolean canViewAll,
                                   boolean isApprover, boolean isAccountManager);

    AgreementGroupResponse resolveOrCreate(Long groupId, String newName, Long userId);

    GroupDeletionStatusResponse getDeletionStatus(Long groupId, Long currentUserId,
                                                  boolean isApprover, boolean isAccountManager);

    void deleteGroupImmediately(Long groupId, String reason, Long currentUserId,
                                boolean isApprover, boolean isAccountManager);

    void submitDeletionRequest(Long groupId, GroupDeletionRequest request, Long currentUserId,
                               boolean isApprover, boolean isAccountManager);

    void executeApprovedGroupDeletion(Long groupId, Long approverId, String reason);

    AgreementGroupResponse renameGroup(Long groupId, UpdateAgreementGroupRequest request, Long currentUserId);
}
