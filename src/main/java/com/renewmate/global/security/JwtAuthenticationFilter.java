package com.renewmate.global.security;

import java.io.IOException;
import java.util.List;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import com.renewmate.user.entity.UserStatus;
import com.renewmate.user.repository.UserRepository;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

public class JwtAuthenticationFilter extends OncePerRequestFilter{
	
	private final JwtProvider jwtProvider;
	private final UserRepository userRepository;
	
	public JwtAuthenticationFilter(JwtProvider jwtprovider, UserRepository userRepository) {
		this.jwtProvider = jwtprovider;
		this.userRepository = userRepository;
	}

	@Override
	protected void doFilterInternal(
			HttpServletRequest request, 
			HttpServletResponse response, 
			FilterChain filterChain
		) throws ServletException, IOException {
			
			String authorization = request.getHeader("Authorization");
			
			if (authorization != null && authorization.startsWith("Bearer ")) {
				String token = authorization.substring(7);
				
				if (jwtProvider.validateToken(token)) {
					
					Long userId = jwtProvider.getUSerId(token);

					if (!userRepository.existsByUserIdAndStatus(userId, UserStatus.ACTIVE)) {
						filterChain.doFilter(request, response);
						return;
					}
					
					UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
							userId,
							null,
							List.of()
						);
					
					SecurityContextHolder.getContext().setAuthentication(authentication);
				}
			}
		
		filterChain.doFilter(request, response);
	}
	
}
