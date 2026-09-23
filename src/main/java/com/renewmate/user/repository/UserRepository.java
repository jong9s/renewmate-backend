package com.renewmate.user.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.renewmate.user.entity.User;
import com.renewmate.user.entity.UserStatus;

public interface UserRepository extends JpaRepository<User, Long> {
	
	Optional<User> findByEmail(String email);

	Optional<User> findByEmailIgnoreCase(String email);

	Optional<User> findByEmailIgnoreCaseAndStatus(String email, UserStatus status);

	Optional<User> findByGoogleSubject(String googleSubject);
	
	boolean existsByEmail(String email);

	boolean existsByUserIdAndStatus(Long userId, UserStatus status);
}
