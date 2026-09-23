package com.renewmate.auth.mail;

import java.nio.charset.StandardCharsets;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class SmtpPasswordResetMailSender implements PasswordResetMailSender {

    private final JavaMailSender javaMailSender;

    @Value("${app.mail.from:no-reply@renewmate.com}")
    private String from;

    @Override
    public void send(String recipientEmail, String recipientName, String resetLink, long expirationMinutes) {
        MimeMessage message = javaMailSender.createMimeMessage();

        try {
            MimeMessageHelper helper = new MimeMessageHelper(message, false, StandardCharsets.UTF_8.name());
            helper.setFrom(from);
            helper.setTo(recipientEmail);
            helper.setSubject("[RenewMate] 비밀번호를 재설정해주세요");
            helper.setText(createHtml(recipientName, resetLink, expirationMinutes), true);
            javaMailSender.send(message);
        } catch (MessagingException exception) {
            throw new IllegalStateException("비밀번호 재설정 메일을 생성하지 못했습니다.", exception);
        }
    }

    private String createHtml(String recipientName, String resetLink, long expirationMinutes) {
        return """
                <!doctype html>
                <html lang="ko">
                  <body style="margin:0;padding:32px;background:#f5f7f6;font-family:Arial,sans-serif;color:#17211d">
                    <div style="max-width:560px;margin:0 auto;background:#ffffff;border-radius:16px;padding:32px">
                      <h1 style="margin:0 0 16px;font-size:24px">RenewMate 비밀번호 재설정</h1>
                      <p style="line-height:1.7">%s님, 비밀번호 재설정을 요청하셨습니다.</p>
                      <p style="line-height:1.7">아래 버튼을 눌러 새 비밀번호를 설정해주세요. 이 링크는 %d분 동안 한 번만 사용할 수 있습니다.</p>
                      <p style="margin:28px 0">
                        <a href="%s" style="display:inline-block;padding:14px 22px;border-radius:10px;background:#16805c;color:#ffffff;text-decoration:none;font-weight:700">비밀번호 재설정</a>
                      </p>
                      <p style="font-size:13px;line-height:1.6;color:#66736d">본인이 요청하지 않았다면 이 메일을 무시해주세요. 비밀번호는 변경되지 않습니다.</p>
                    </div>
                  </body>
                </html>
                """.formatted(escapeHtml(recipientName), expirationMinutes, resetLink);
    }

    private String escapeHtml(String value) {
        return value
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }
}
