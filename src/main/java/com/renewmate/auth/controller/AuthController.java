package com.renewmate.auth.controller;

import java.io.IOException;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.renewmate.auth.dto.LoginRequest;
import com.renewmate.auth.dto.LoginResponse;
import com.renewmate.auth.dto.SignupRequest;
import com.renewmate.auth.dto.OAuthCodeExchangeRequest;
import com.renewmate.auth.dto.PasswordResetConfirmRequest;
import com.renewmate.auth.dto.PasswordResetRequest;
import com.renewmate.auth.service.AuthService;
import com.renewmate.auth.service.OAuthLoginService;
import com.renewmate.auth.service.PasswordResetService;

import jakarta.validation.Valid;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {
	private final AuthService authService;
	private final PasswordResetService passwordResetService;
	private final OAuthLoginService oauthLoginService;
	
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

	@GetMapping("/google/start")
	public void startGoogleLogin(HttpServletResponse response) throws IOException {
		response.setHeader("Cache-Control", "no-store");
		response.sendRedirect("/oauth2/authorization/google");
	}

	@PostMapping(value = "/google/exchange", consumes = MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<LoginResponse> exchangeGoogleCode(
			@Valid @RequestBody OAuthCodeExchangeRequest request
	) {
		return ResponseEntity.ok(oauthLoginService.exchange(request.code()));
	}

	@PostMapping("/password-reset/request")
	public ResponseEntity<Void> requestPasswordReset(
			@Valid @RequestBody PasswordResetRequest request
	) {
		passwordResetService.requestReset(request);
		return ResponseEntity.noContent().build();
	}

	@PostMapping("/password-reset/confirm")
	public ResponseEntity<Void> confirmPasswordReset(
			@Valid @RequestBody PasswordResetConfirmRequest request
	) {
		passwordResetService.confirmReset(request);
		return ResponseEntity.noContent().build();
	}
	
}
