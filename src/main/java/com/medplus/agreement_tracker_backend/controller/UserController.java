package com.medplus.agreement_tracker_backend.controller;

import com.medplus.agreement_tracker_backend.dto.request.ChangePasswordRequest;
import com.medplus.agreement_tracker_backend.dto.request.CreateUserRequest;
import com.medplus.agreement_tracker_backend.dto.request.UpdateUserRequest;
import com.medplus.agreement_tracker_backend.dto.response.UserResponse;
import com.medplus.agreement_tracker_backend.security.UserPrincipal;
import com.medplus.agreement_tracker_backend.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

import static com.medplus.agreement_tracker_backend.security.RightExpressions.ADMIN_USERS;

@RestController
@RequestMapping("/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<UserResponse> createUser(
            @Valid @RequestBody CreateUserRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(userService.createUser(request, principal.getId()));
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Page<UserResponse>> getAllUsers(@PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(userService.getAllUsers(pageable));
    }

    @GetMapping("/me")
    public ResponseEntity<UserResponse> getCurrentUser(@AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(userService.getUserById(principal.getId()));
    }

    @PostMapping("/change-password")
    public ResponseEntity<Void> changePassword(
            @Valid @RequestBody ChangePasswordRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        userService.changePassword(principal.getId(), request);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/search")
    @PreAuthorize(ADMIN_USERS)
    public ResponseEntity<List<UserResponse>> search(@RequestParam String q) {
        return ResponseEntity.ok(userService.searchUsers(q));
    }

    @GetMapping("/lookup")
    @PreAuthorize("hasAnyAuthority('ADMIN_USERS', 'AGREEMENT_EDIT')")
    public ResponseEntity<List<UserResponse>> lookup(@RequestParam(required = false, defaultValue = "") String q) {
        return ResponseEntity.ok(userService.searchUsers(q));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('ADMIN_USERS') or #id == authentication.principal.id")
    public ResponseEntity<UserResponse> getUser(@PathVariable Long id) {
        return ResponseEntity.ok(userService.getUserById(id));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<UserResponse> updateUser(
            @PathVariable Long id,
            @Valid @RequestBody UpdateUserRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(userService.updateUser(id, request, principal.getId()));
    }

    @PutMapping("/{id}/roles")
    @PreAuthorize(ADMIN_USERS)
    public ResponseEntity<UserResponse> updateRoles(
            @PathVariable Long id,
            @RequestBody List<String> roles,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(userService.updateUserRoles(id, roles, principal.getId()));
    }

    @PostMapping("/{userId}/reset-password")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> resetPassword(
            @PathVariable Long userId,
            @AuthenticationPrincipal UserPrincipal principal) {
        userService.resetPassword(userId, principal.getId());
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> deactivateUser(
            @PathVariable Long id,
            @AuthenticationPrincipal UserPrincipal principal) {
        userService.deactivateUser(id, principal.getId());
        return ResponseEntity.noContent().build();
    }
}
