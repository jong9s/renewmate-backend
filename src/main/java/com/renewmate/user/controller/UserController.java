package com.renewmate.user.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.renewmate.auth.dto.UserResponse;
import com.renewmate.user.dto.UserPasswordUpdateRequest;
import com.renewmate.user.dto.UserUpdateRequest;
import com.renewmate.user.dto.UserWithdrawalRequest;
import com.renewmate.user.service.UserService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {
	
	private final UserService userService;
	
	@GetMapping("/me")
	public ResponseEntity<UserResponse> getMyInfo(Authentication authentication) {
		
			Long userId = (Long) authentication.getPrincipal();
			
			UserResponse response = userService.getMyInfo(userId);
			
			return ResponseEntity.ok(response);
	}
	
	@PatchMapping("/me")
	public ResponseEntity<Void> updateMyInfo(Authentication authentication,
	        @Valid @RequestBody UserUpdateRequest request) {
		
	    Long userId = (Long) authentication.getPrincipal();

	    userService.updateMyInfo(userId, request);

	    return ResponseEntity.noContent().build();
	}
	
	@PatchMapping("/me/password")
	public ResponseEntity<Void> updatePassword(Authentication authentication,
			@Valid @RequestBody UserPasswordUpdateRequest request) {
		
	    Long userId = (Long) authentication.getPrincipal();

	    userService.updatePassword(userId, request);

	    return ResponseEntity.noContent().build();
	}

	@DeleteMapping("/me")
	public ResponseEntity<Void> withdraw(Authentication authentication,
	        @Valid @RequestBody UserWithdrawalRequest request) {

	    Long userId = (Long) authentication.getPrincipal();

	    userService.withdraw(userId, request);

	    return ResponseEntity.noContent().build();
	}
}
