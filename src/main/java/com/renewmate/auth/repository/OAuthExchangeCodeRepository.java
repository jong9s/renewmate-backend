package com.renewmate.auth.repository;

import java.util.Optional;
import java.time.LocalDateTime;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

import com.renewmate.auth.entity.OAuthExchangeCode;

import jakarta.persistence.LockModeType;

public interface OAuthExchangeCodeRepository extends JpaRepository<OAuthExchangeCode, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<OAuthExchangeCode> findByCodeHash(String codeHash);

    void deleteAllByUser_UserId(Long userId);

    long deleteByExpiresAtBefore(LocalDateTime expiresAt);
}
