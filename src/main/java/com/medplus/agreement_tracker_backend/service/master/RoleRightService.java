package com.medplus.agreement_tracker_backend.service.master;

import com.medplus.agreement_tracker_backend.dto.response.master.RoleRightsMatrixResponse;
import com.medplus.agreement_tracker_backend.entity.Right;
import com.medplus.agreement_tracker_backend.entity.Role;
import com.medplus.agreement_tracker_backend.entity.RoleRight;
import com.medplus.agreement_tracker_backend.exception.ResourceNotFoundException;
import com.medplus.agreement_tracker_backend.repository.RightRepository;
import com.medplus.agreement_tracker_backend.repository.RoleRepository;
import com.medplus.agreement_tracker_backend.repository.RoleRightRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashSet;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional
public class RoleRightService {

    private final RoleRepository roleRepository;
    private final RightRepository rightRepository;
    private final RoleRightRepository roleRightRepository;

    @Transactional(readOnly = true)
    public List<RoleRightsMatrixResponse> getMatrix() {
        return roleRepository.findByIsActiveTrue().stream()
                .map(role -> new RoleRightsMatrixResponse(
                        role.getId(),
                        role.getName().name(),
                        roleRightRepository.findRightCodesByRoleId(role.getId())))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<String> getRightCodesForRole(Long roleId) {
        findRoleOrThrow(roleId);
        return roleRightRepository.findRightCodesByRoleId(roleId);
    }

    public List<String> updateRoleRights(Long roleId, List<String> rightCodes, Long userId) {
        Role role = findRoleOrThrow(roleId);
        roleRightRepository.deleteByRoleId(roleId);

        if (rightCodes != null && !rightCodes.isEmpty()) {
            LinkedHashSet<String> distinctCodes = new LinkedHashSet<>(rightCodes);
            for (String code : distinctCodes) {
                Right right = rightRepository.findByCode(code)
                        .orElseThrow(() -> new ResourceNotFoundException("Right not found: " + code));
                RoleRight mapping = RoleRight.builder().role(role).right(right).build();
                mapping.setCreatedByUserId(userId);
                roleRightRepository.save(mapping);
            }
        }

        return roleRightRepository.findRightCodesByRoleId(roleId);
    }

    private Role findRoleOrThrow(Long roleId) {
        return roleRepository.findById(roleId)
                .orElseThrow(() -> new ResourceNotFoundException("Role", roleId));
    }
}
