package com.renewmate.global.security;

import java.nio.charset.StandardCharsets;
import java.util.Date;

import javax.crypto.SecretKey;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

@Component
public class JwtProvider {
	private final SecretKey secretKey;
	private final long accessTokenExpiration;
	
	public JwtProvider(
			@Value("${jwt.secret}") String secret,
			@Value("${jwt.access-token-expiration}") long accessTokenExpiration
		) {
		this.secretKey = Keys.hmacShaKeyFor(
			secret.getBytes(StandardCharsets.UTF_8)
		);
		this.accessTokenExpiration = accessTokenExpiration;
	}
	
	public String createAccessToken(Long userId, String email) {
		
		Date now = new Date();
		Date expiration = new Date(
				now.getTime() + accessTokenExpiration
		);
		
		return Jwts.builder()
				.subject(String.valueOf(userId))
				.claim("email", email)
				.issuedAt(now)
				.expiration(expiration)
				.signWith(secretKey)
				.compact();
	}
	
	public boolean validateToken(String token) {
		try {
			Jwts.parser()
					.verifyWith(secretKey)
					.build()
					.parseSignedClaims(token);
			
			return true;
		} catch (Exception e) {
			return false;
		}
	}
	
	public Long getUSerId(String token) {
		String subject = Jwts.parser()
				.verifyWith(secretKey)
				.build()
				.parseSignedClaims(token)
				.getPayload()
				.getSubject();
		
		return Long.valueOf(subject);
	}
}
