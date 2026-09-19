package com.renewmate.global.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
public class SecurityConfig {
	
	private final JwtProvider jwtProvider;
	
	public SecurityConfig(JwtProvider jwtProvider) {
		this.jwtProvider = jwtProvider;
	}
	
	@Bean
	public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
		
		JwtAuthenticationFilter jwtAuthenticationFilter = new JwtAuthenticationFilter(jwtProvider);
		
		http
			.csrf(csrf -> csrf.disable())
			.formLogin(form -> form.disable())
			.httpBasic(basic -> basic.disable())
			
			.authorizeHttpRequests(auth -> auth
				.requestMatchers(
						"/api/auth/**",
						"/swagger-ui/**",
						"/v3/api-docs/**",
						"/error"
				).permitAll()
				.anyRequest().authenticated()
				
			)
			
		    .exceptionHandling(exception -> exception
		            .authenticationEntryPoint(authenticationEntryPoint())
		            .accessDeniedHandler(accessDeniedHandler())
		    )
			
			.addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);
		
		return http.build();
	}
	
	private AuthenticationEntryPoint authenticationEntryPoint() {
	    return (request, response, authException) -> {
	        response.setStatus(401);
	        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
	        response.setCharacterEncoding("UTF-8");

	        response.getWriter().write("""
	                {
	                  "success": false,
	                  "errorCode": "UNAUTHORIZED",
	                  "message": "로그인이 필요합니다."
	                }
	                """);
	    };
	}

	private AccessDeniedHandler accessDeniedHandler() {
	    return (request, response, accessDeniedException) -> {
	        response.setStatus(403);
	        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
	        response.setCharacterEncoding("UTF-8");

	        response.getWriter().write("""
	                {
	                  "success": false,
	                  "errorCode": "FORBIDDEN",
	                  "message": "접근 권한이 없습니다."
	                }
	                """);
	    };
	}
}
