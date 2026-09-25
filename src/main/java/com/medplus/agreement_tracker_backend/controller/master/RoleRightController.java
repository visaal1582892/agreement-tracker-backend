package com.medplus.agreement_tracker_backend.controller.master;

import com.medplus.agreement_tracker_backend.dto.response.master.RoleRightsMatrixResponse;
import com.medplus.agreement_tracker_backend.security.UserPrincipal;
import com.medplus.agreement_tracker_backend.service.master.RoleRightService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/master/role-rights")
@RequiredArgsConstructor
public class RoleRightController {

    private final RoleRightService service;

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<RoleRightsMatrixResponse>> getMatrix() {
        return ResponseEntity.ok(service.getMatrix());
    }

    @GetMapping("/{roleId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<String>> getRoleRights(@PathVariable Long roleId) {
        return ResponseEntity.ok(service.getRightCodesForRole(roleId));
    }

    @PutMapping("/{roleId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<String>> updateRoleRights(
            @PathVariable Long roleId,
            @RequestBody List<String> rightCodes,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(service.updateRoleRights(roleId, rightCodes, principal.getId()));
    }
}
