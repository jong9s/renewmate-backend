package com.renewmate.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UserPasswordUpdateRequest(

        @NotBlank
        String currentPassword,

        @NotBlank
        @Size(min = 8)
        String newPassword,

        @NotBlank
        String newPasswordConfirm

) {
}