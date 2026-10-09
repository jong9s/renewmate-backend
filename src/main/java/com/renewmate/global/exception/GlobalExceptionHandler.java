package com.renewmate.global.exception;

import java.util.HashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.ErrorResponse;

import com.renewmate.global.incident.IncidentReporter;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import lombok.RequiredArgsConstructor;

@RestControllerAdvice
@RequiredArgsConstructor
public class GlobalExceptionHandler {

	private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

	private final IncidentReporter incidentReporter;

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
	
	// @RequestParam·@PathVariable 제약(@Min, @Max 등) 위반
	@ExceptionHandler(ConstraintViolationException.class)
	public ResponseEntity<Map<String, Object>> handleConstraintViolationException(ConstraintViolationException e){
			String message = e.getConstraintViolations()
					.stream()
					.findFirst()
					.map(ConstraintViolation::getMessage)
					.orElse("잘못된 요청입니다.");

			return errorResponse(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", message);
	}

	@ExceptionHandler(Exception.class)
	public ResponseEntity<Map<String, Object>> handleUnexpectedException(Exception e, HttpServletRequest request) {

	    // 없는 경로·지원하지 않는 메서드·읽을 수 없는 본문은 서버 오류가 아니므로 4xx로 응답
	    if (IncidentReporter.isClientError(e)) {
	        log.warn("Client error handled as fallback: {} {} {}", request.getMethod(), request.getRequestURI(), e.getClass().getSimpleName());
	        return clientErrorResponse(e);
	    }

	    log.error("Unhandled exception: {} {}", request.getMethod(), request.getRequestURI(), e);
	    incidentReporter.report(e, request.getMethod(), request.getRequestURI());

	    // 상세 예외 메시지와 스택 트레이스는 클라이언트에 노출하지 않음
	    return errorResponse(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_SERVER_ERROR", "서버 내부 오류가 발생했습니다.");
	}

	private ResponseEntity<Map<String, Object>> clientErrorResponse(Exception e) {
	    HttpStatus status = e instanceof ErrorResponse errorResponse
	            ? HttpStatus.valueOf(errorResponse.getStatusCode().value())
	            : HttpStatus.BAD_REQUEST;

	    String message = switch (status) {
	        case NOT_FOUND -> "요청한 리소스를 찾을 수 없습니다.";
	        case METHOD_NOT_ALLOWED -> "지원하지 않는 요청 메서드입니다.";
	        default -> "잘못된 요청입니다.";
	    };

	    return errorResponse(status, status.name(), message);
	}

	private ResponseEntity<Map<String, Object>> errorResponse(HttpStatus status, String errorCode, String message) {
	    Map<String, Object> body = new HashMap<>();

	    body.put("success", false);
	    body.put("errorCode", errorCode);
	    body.put("message", message);

	    return ResponseEntity
	            .status(status)
	            .body(body);
	}
}
