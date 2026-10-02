package com.renewmate.auth.dto;

public record UserResponse(
		
		Long userId,
		String name,
		String email
	) {

}
