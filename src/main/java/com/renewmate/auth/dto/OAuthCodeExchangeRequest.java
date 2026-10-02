package com.renewmate.auth.dto;

import jakarta.validation.constraints.NotBlank;

public record OAuthCodeExchangeRequest(
        @NotBlank(message = "소셜 로그인 코드는 필수입니다.")
        String code
) {
}
