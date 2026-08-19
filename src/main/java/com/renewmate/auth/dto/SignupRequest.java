package com.renewmate.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SignupRequest(
		
		@NotBlank(message = "이름은 필수입니다.")
		@Size(max = 50, message = "이름은 50자 이하여야 합니다.")
		String name,
		
		@NotBlank(message = "이메일은 필수입니다.")
		@Size(message = "올바른 이메일 형태가 아닙니다.")
		String email,
		
		@NotBlank(message = "비밀번호는 필수입니다.")
		@Size(min = 8, message = "비밀번호는 최소 8자 이상이어야 합니다.")
		String password,
		
		@NotBlank(message = "비밀번호 확인은 필수입니다.")
		String passwordConfirm
		
		) {

}
