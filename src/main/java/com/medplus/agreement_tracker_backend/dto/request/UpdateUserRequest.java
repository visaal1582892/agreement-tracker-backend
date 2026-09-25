package com.medplus.agreement_tracker_backend.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;

public record UpdateUserRequest(
        @NotBlank(message = "Full name is required")
        @Size(max = 200)
        String fullName,

        @NotBlank(message = "Email is required")
        @Email(message = "Valid email is required")
        String email,

        String employeeId,

        boolean isActive,

        List<String> roles
) {}
