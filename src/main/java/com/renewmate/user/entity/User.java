package com.renewmate.user.entity;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "users")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class User {
	
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "user_id")
	private Long userId;
	
	@Column(nullable = false, length = 50)
	private String name;
	
	@Column(nullable = false, unique = true, length = 255)
	private String email;
	
	@Column(nullable = false, length = 255)
	private String password;
	
	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private UserStatus status;
	
	@Column(nullable = false)
	private LocalDateTime createdAt;
	
	@Column(nullable = false)
	private LocalDateTime updatedAt;
	
	
	public static User create(
			String name,
			String email,
			String password
	) {
		User user = new User();
		
		user.name = name;
		user.email = email;
		user.password = password;
		user.status = UserStatus.ACTIVE;
		user.createdAt = LocalDateTime.now();
		user.updatedAt = LocalDateTime.now();
		
		return user;
	}
	
}
