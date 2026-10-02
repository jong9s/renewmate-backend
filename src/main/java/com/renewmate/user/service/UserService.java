package com.renewmate.user.service;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.renewmate.auth.dto.UserResponse;
import com.renewmate.auth.repository.PasswordResetTokenRepository;
import com.renewmate.auth.repository.OAuthExchangeCodeRepository;
import com.renewmate.global.exception.BusinessException;
import com.renewmate.global.exception.ErrorCode;
import com.renewmate.notification.repository.NotificationRepository;
import com.renewmate.settings.repository.UserSettingsRepository;
import com.renewmate.subscription.repository.SubscriptionRepository;
import com.renewmate.user.dto.UserPasswordUpdateRequest;
import com.renewmate.user.dto.UserUpdateRequest;
import com.renewmate.user.dto.UserWithdrawalRequest;
import com.renewmate.user.entity.User;
import com.renewmate.user.repository.UserRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class UserService {
	
	private final UserRepository userRepository;
	private final PasswordEncoder passwordEncoder;
	private final NotificationRepository notificationRepository;
	private final SubscriptionRepository subscriptionRepository;
	private final UserSettingsRepository userSettingsRepository;
	private final PasswordResetTokenRepository passwordResetTokenRepository;
	private final OAuthExchangeCodeRepository oauthExchangeCodeRepository;
	
	@Transactional(readOnly = true)
	public UserResponse getMyInfo(Long userId) {
		
		User user = userRepository.findById(userId)
				.orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
		
		return new UserResponse(
					user.getUserId(),
					user.getName(),
					user.getEmail()
		);
	}
	
	@Transactional
	public void updateMyInfo(
	        Long userId,
	        UserUpdateRequest request
	) {
	    User user = userRepository.findById(userId)
	            .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

	    user.updateName(request.name());
	}
	
	@Transactional
	public void updatePassword(Long userId, UserPasswordUpdateRequest request) {
	    User user = userRepository.findById(userId)
	            .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

	    if (!passwordEncoder.matches(
	            request.currentPassword(),
	            user.getPassword()
	    )) {
	        throw new BusinessException(ErrorCode.INVALID_CURRENT_PASSWORD);
	    }

	    if (!request.newPassword()
	            .equals(request.newPasswordConfirm())) {

	        throw new BusinessException(ErrorCode.PASSWORD_CONFIRM_MISMATCH);
	    }

	    String encodedPassword = passwordEncoder.encode(request.newPassword());

	    user.updatePassword(encodedPassword);
	}

	@Transactional
	public void withdraw(Long userId, UserWithdrawalRequest request) {
	    User user = userRepository.findById(userId)
	            .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

	    if (!passwordEncoder.matches(request.currentPassword(), user.getPassword())) {
	        throw new BusinessException(ErrorCode.INVALID_CURRENT_PASSWORD);
	    }

	    passwordResetTokenRepository.deleteAllByUser_UserId(userId);
	    oauthExchangeCodeRepository.deleteAllByUser_UserId(userId);
	    notificationRepository.deleteAllByUser_UserId(userId);
	    subscriptionRepository.deleteAllByUser_UserId(userId);
	    userSettingsRepository.deleteByUser_UserId(userId);
	    userRepository.delete(user);
	}
}
