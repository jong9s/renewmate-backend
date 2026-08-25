package com.renewmate.auth.dto;

public record LoginResponse(
		
		Long userId,
		String name,
		String email,
		String accessToken
		
	) {
	
}
