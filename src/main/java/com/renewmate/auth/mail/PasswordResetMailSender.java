package com.renewmate.auth.mail;

public interface PasswordResetMailSender {

    void send(String recipientEmail, String recipientName, String resetLink, long expirationMinutes);
}
