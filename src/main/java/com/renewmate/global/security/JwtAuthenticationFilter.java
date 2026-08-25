package com.renewmate.global.security;

import java.io.IOException;
import java.util.List;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

public class JwtAuthenticationFilter extends OncePerRequestFilter{
	
	private final JwtProvider jwtProvider;
	
	public JwtAuthenticationFilter(JwtProvider jwtprovider) {
		this.jwtProvider = jwtprovider;
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
