package com.renewmate.global.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.renewmate.global.security.ConcurrencyLimitedPasswordEncoder;

@Configuration
public class PasswordEncoderConfig {

	// 해싱 강도(strength 10)는 보안을 위해 유지하고, 동시 실행 수만 제한한다
	@Bean
	public PasswordEncoder passwordEncoder() {
		return new ConcurrencyLimitedPasswordEncoder(new BCryptPasswordEncoder());
	}
}
