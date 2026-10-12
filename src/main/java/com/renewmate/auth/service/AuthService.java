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

	// 어떤 비밀번호와도 일치하지 않는 strength 10 BCrypt 해시 (존재하지 않는 계정의 로그인 비교용)
	private static final String DUMMY_PASSWORD_HASH = "$2a$10$BCCFUFJY5AQ7B89/.qnJZOZmtyZ2rRcFt8PzTfVnrQHVRVcAxQjq.";

	private final UserRepository userRepository;
	private final PasswordEncoder passwordEncoder;
	
	private final JwtProvider jwtProvider;
	
	// 트랜잭션을 걸지 않는다: 트랜잭션 안에서 BCrypt 해싱(수십 ms)을 하면 그동안 DB 커넥션을 붙잡아,
	// 가입이 몰릴 때 커넥션 풀이 고갈되고 다른 API가 커넥션을 기다리게 된다.
	// 저장은 save() 한 번이라 자체 트랜잭션으로 충분하고, 중복 이메일은 DB 유니크 제약이 최종적으로 막는다.
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

		User user = userRepository.findByEmailIgnoreCase(request.email().trim()).orElse(null);

		// 계정이 없거나 비활성이어도 BCrypt 비교를 똑같이 수행해 응답 시간으로 가입 여부를 추측할 수 없게 한다
		String encodedPassword = user != null ? user.getPassword() : DUMMY_PASSWORD_HASH;
		boolean passwordMatches = passwordEncoder.matches(request.password(), encodedPassword);

		if (user == null || user.getStatus() != UserStatus.ACTIVE || !passwordMatches) {
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
