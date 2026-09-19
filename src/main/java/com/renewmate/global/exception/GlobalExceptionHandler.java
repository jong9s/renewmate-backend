package com.renewmate.global.exception;

import java.util.HashMap;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {
	@ExceptionHandler(BusinessException.class)
	public ResponseEntity<Map<String, Object>> handleBusinessException(BusinessException e){
			ErrorCode errorCode = e.getErrorCode();
			
			Map<String, Object> body = new HashMap<>();
			body.put("success", false);
			body.put("errorCode", errorCode.name());
			body.put("message", errorCode.getMessage());
			
			return ResponseEntity
					.status(errorCode.getStatus())
					.body(body);
	}
	
	@ExceptionHandler(MethodArgumentNotValidException.class)
	public ResponseEntity<Map<String, Object>> handleValidationException(MethodArgumentNotValidException e){
			String message = e.getBindingResult()
					.getFieldErrors()
					.get(0)
					.getDefaultMessage();
			
			Map<String, Object> body = new HashMap<>();
			body.put("success", false);
			body.put("errorCode", "VALIDATION_ERROR");
			body.put("message", message);
			
			return ResponseEntity
					.badRequest()
					.body(body);
	}
	
	@ExceptionHandler(Exception.class)
	public ResponseEntity<Map<String, Object>> handleUnexpectedException(Exception e) {

	    // 상세 예외 메시지와 스택 트레이스는 클라이언트에 노출하지 않음
	    Map<String, Object> body = new HashMap<>();

	    body.put("success", false);
	    body.put("errorCode", "INTERNAL_SERVER_ERROR");
	    body.put("message", "서버 내부 오류가 발생했습니다.");

	    return ResponseEntity
	            .status(HttpStatus.INTERNAL_SERVER_ERROR)
	            .body(body);
	}
}
