package com.renewmate.auth.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.renewmate.auth.dto.UserResponse;
import com.renewmate.auth.service.UserService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {
	
	private final UserService userService;
	
	@GetMapping("/me")
	public ResponseEntity<UserResponse> getMyInfo(
			Authentication authentication
		) {
			Long userId = (Long) authentication.getPrincipal();
			
			UserResponse response = userService.getMyInfo(userId);
			
			return ResponseEntity.ok(response);
	}
}
