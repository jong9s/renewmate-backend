package com.renewmate.auth.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.renewmate.auth.dto.LoginRequest;
import com.renewmate.auth.dto.LoginResponse;
import com.renewmate.auth.dto.SignupRequest;
import com.renewmate.auth.service.AuthService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {
	private final AuthService authService;
	
	@PostMapping("/signup")
	public ResponseEntity<Void> signup(
			@Valid @RequestBody SignupRequest request
	){
		authService.signup(request);
		
		return ResponseEntity.status(HttpStatus.CREATED).build();
	}
	
	@PostMapping("/login")
	public ResponseEntity<LoginResponse> login(
			@Valid @RequestBody LoginRequest request
		){
		
		LoginResponse response = authService.login(request);
		
		return ResponseEntity.ok(response);
	}
	
}
