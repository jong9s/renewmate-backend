package com.renewmate.auth.service;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import com.renewmate.auth.dto.LoginRequest;
import com.renewmate.auth.dto.LoginResponse;
import com.renewmate.auth.dto.SignupRequest;
import com.renewmate.global.security.JwtProvider;
import com.renewmate.user.entity.User;
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
		if(userRepository.existsByEmail(request.email())) {
			throw new IllegalArgumentException("이미 가입된 이메일입니다.");
		}
		// 비밀번호 중복 확인
		if(!request.password().equals(request.passwordConfirm())) {
			throw new IllegalArgumentException("비밀번호가 일치하지 않습니다"); 
		}
		// 비밀번호 암호화
		String encodedPassword = passwordEncoder.encode(request.password());
		// User 객체 생성
		User user = User.create(request.name(), request.email(), encodedPassword);
		// DB 저장
		userRepository.save(user);
		
	}
	
	public LoginResponse login(LoginRequest request) {
		
		User user = userRepository.findByEmail(request.email())
				.orElseThrow(() -> 
						new IllegalArgumentException("이메일 또는 비밀번호가 일치하지 않습니다")
					);
		
		if (!passwordEncoder.matches(request.password(), user.getPassword())) {
			throw new IllegalArgumentException("이메일 또는 비밀번호가 일치하지 않습니다");
		}
		
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
