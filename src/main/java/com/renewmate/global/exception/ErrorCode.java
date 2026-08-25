package com.renewmate.global.exception;

import org.springframework.http.HttpStatus;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum ErrorCode {
	
	DUPLICATE_EMAIL(
			HttpStatus.CONFLICT,
			"이미 가입된 이메일입니다."
	),
	
	INVALID_LOGIN(
			HttpStatus.UNAUTHORIZED,
			"이메일 또는 비밀번호가 일치하지 않습니다."
	),
	
	PASSWORD_MISMATCH(
			HttpStatus.BAD_REQUEST,
			"비밀번호가 일치하지 않습니다."
	),
	
	USER_NOT_FOUND(
			HttpStatus.NOT_FOUND,
			"사용자를 찾을 수 없습니다."
	),
	
	SUBSCRIPTION_NOT_FOUND(
	        HttpStatus.NOT_FOUND,
	        "구독 정보를 찾을 수 없습니다."
	);
	
	private final HttpStatus status;
	private final String message;
}
