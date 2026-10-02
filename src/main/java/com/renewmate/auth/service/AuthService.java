package com.renewmate.auth.service;

import java.util.UUID;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import com.renewmate.auth.dto.LoginRequest;
import com.renewmate.auth.dto.LoginResponse;
import com.renewmate.auth.dto.SignupRequest;
import com.renewmate.auth.google.VerifiedGoogleIdentity;
import com.renewmate.global.exception.BusinessException;
import com.renewmate.global.exception.ErrorCode;
import com.renewmate.global.security.JwtProvider;
import com.renewmate.user.entity.User;
import com.renewmate.user.entity.UserStatus;
import com.renewmate.user.repository.UserRepository;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AuthService {
	
	private final UserRepository userRepository;
	private final PasswordEncoder passwordEncoder;
	
	private final JwtProvider jwtProvider;
	
	@Transactional
	public void signup(SignupRequest request) {
		
		// 이메일 중복 확인
		String normalizedEmail = request.email().trim().toLowerCase();

		if(userRepository.existsByEmail(normalizedEmail)) {
			throw new BusinessException(ErrorCode.DUPLICATE_EMAIL);
		}
		// 비밀번호 중복 확인
		if(!request.password().equals(request.passwordConfirm())) {
			throw new BusinessException(ErrorCode.PASSWORD_MISMATCH); 
		}
		// 비밀번호 암호화
		String encodedPassword = passwordEncoder.encode(request.password());
		// User 객체 생성
		User user = User.create(request.name(), normalizedEmail, encodedPassword);
		// DB 저장
		userRepository.save(user);
		
	}
	
	public LoginResponse login(LoginRequest request) {
		
		User user = userRepository.findByEmailIgnoreCase(request.email().trim())
				.orElseThrow(() -> 
						new BusinessException(ErrorCode.INVALID_LOGIN)
					);
		
		if (user.getStatus() != UserStatus.ACTIVE
				|| !passwordEncoder.matches(request.password(), user.getPassword())) {
			throw new BusinessException(ErrorCode.INVALID_LOGIN);
		}

		return createLoginResponse(user);
	}

	@Transactional
	User resolveGoogleUser(VerifiedGoogleIdentity identity) {
		if (!identity.emailVerified()) {
			throw new BusinessException(ErrorCode.INVALID_GOOGLE_TOKEN);
		}

		User user = userRepository.findByGoogleSubject(identity.subject())
				.orElseGet(() -> findOrCreateGoogleUser(identity));

		if (user.getStatus() != UserStatus.ACTIVE) {
			throw new BusinessException(ErrorCode.INVALID_LOGIN);
		}

		return user;
	}

	private User findOrCreateGoogleUser(VerifiedGoogleIdentity identity) {
		return userRepository.findByEmailIgnoreCase(identity.email())
				.map(existingUser -> {
					if (existingUser.getGoogleSubject() != null
							&& !existingUser.getGoogleSubject().equals(identity.subject())) {
						throw new BusinessException(ErrorCode.GOOGLE_ACCOUNT_CONFLICT);
					}
					existingUser.linkGoogleSubject(identity.subject());
					return existingUser;
				})
				.orElseGet(() -> {
					String fallbackName = identity.email().substring(0, identity.email().indexOf('@'));
					String name = identity.name() == null || identity.name().isBlank()
							? fallbackName
							: identity.name();
					User newUser = User.createGoogle(
							name,
							identity.email(),
							passwordEncoder.encode(UUID.randomUUID().toString()),
							identity.subject()
					);
					return userRepository.save(newUser);
				});
	}

	LoginResponse createLoginResponse(User user) {
		String accessToken = jwtProvider.createAccessToken(
				user.getUserId(),
				user.getEmail()
			);
		
		return new LoginResponse(
				user.getUserId(),
				user.getName(),
				user.getEmail(),
				accessToken
			);
	}
}
