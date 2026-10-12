package com.renewmate.global.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.resilience.annotation.EnableResilientMethods;

/**
 * Spring 7의 @ConcurrencyLimit(비밀번호 해싱 동시 실행 제한)과 @Retryable(메일 재시도)을 켠다.
 */
@Configuration
@EnableResilientMethods
public class ResilienceConfig {
}
