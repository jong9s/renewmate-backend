package com.renewmate.auth.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.renewmate.auth.dto.UserResponse;
import com.renewmate.global.exception.BusinessException;
import com.renewmate.global.exception.ErrorCode;
import com.renewmate.user.entity.User;
import com.renewmate.user.repository.UserRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class UserService {
	
	private final UserRepository userRepository;
	
	@Transactional(readOnly = true)
	public UserResponse getMyInfo(Long userId) {
		
		User user = userRepository.findById(userId)
				.orElseThrow(() -> 
						new BusinessException(ErrorCode.USER_NOT_FOUND) 
				);
		
		return new UserResponse(
					user.getUserId(),
					user.getName(),
					user.getEmail()
		);
	}
}
