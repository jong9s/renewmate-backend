package com.renewmate.auth.mail;

/**
 * 재설정 토큰이 저장된 뒤(커밋 후) 메일을 보내기 위한 이벤트.
 * resetLink에는 원문 토큰이 들어 있으므로 toString()에서 제외해 로그에 남지 않게 한다.
 */
public record PasswordResetMailRequestedEvent(
        Long tokenId,
        Long userId,
        String recipientEmail,
        String recipientName,
        String resetLink,
        long expirationMinutes
) {

    @Override
    public String toString() {
        return "PasswordResetMailRequestedEvent[tokenId=" + tokenId + ", userId=" + userId + "]";
    }
}
