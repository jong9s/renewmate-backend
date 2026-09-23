package com.renewmate.global.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import com.renewmate.auth.oauth.GoogleOAuthFailureHandler;
import com.renewmate.auth.oauth.GoogleOAuthSuccessHandler;
import com.renewmate.user.repository.UserRepository;

@Configuration
public class SecurityConfig {
	
	private final JwtProvider jwtProvider;
	private final UserRepository userRepository;
	private final GoogleOAuthSuccessHandler googleOAuthSuccessHandler;
	private final GoogleOAuthFailureHandler googleOAuthFailureHandler;
	
	public SecurityConfig(
			JwtProvider jwtProvider,
			UserRepository userRepository,
			GoogleOAuthSuccessHandler googleOAuthSuccessHandler,
			GoogleOAuthFailureHandler googleOAuthFailureHandler
	) {
		this.jwtProvider = jwtProvider;
		this.userRepository = userRepository;
		this.googleOAuthSuccessHandler = googleOAuthSuccessHandler;
		this.googleOAuthFailureHandler = googleOAuthFailureHandler;
	}
	
	@Bean
	public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
		
		JwtAuthenticationFilter jwtAuthenticationFilter = new JwtAuthenticationFilter(jwtProvider, userRepository);
		
		http
			.csrf(csrf -> csrf.disable())
			.formLogin(form -> form.disable())
			.httpBasic(basic -> basic.disable())
			
			.authorizeHttpRequests(auth -> auth
				.requestMatchers(
						"/api/auth/**",
						"/oauth2/**",
						"/login/oauth2/**",
						"/swagger-ui/**",
						"/v3/api-docs/**",
						"/error",
						"/actuator/health"
				).permitAll()
				.anyRequest().authenticated()
				
			)
			
			.exceptionHandling(exception -> exception
		            .authenticationEntryPoint(authenticationEntryPoint())
		            .accessDeniedHandler(accessDeniedHandler())
		    )

			.oauth2Login(oauth -> oauth
					.successHandler(googleOAuthSuccessHandler)
					.failureHandler(googleOAuthFailureHandler)
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
