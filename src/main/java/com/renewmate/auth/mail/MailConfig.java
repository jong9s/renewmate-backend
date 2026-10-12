package com.renewmate.auth.mail;

import java.util.Properties;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;

@Configuration
public class MailConfig {

    @Bean
    public JavaMailSender javaMailSender(
            @Value("${spring.mail.host:smtp.gmail.com}") String host,
            @Value("${spring.mail.port:587}") int port,
            @Value("${spring.mail.username:}") String username,
            @Value("${spring.mail.password:}") String password,
            @Value("${spring.mail.properties.mail.smtp.auth:true}") boolean auth,
            @Value("${spring.mail.properties.mail.smtp.starttls.enable:true}") boolean startTls,
            @Value("${app.mail.connection-timeout-ms:5000}") int connectionTimeoutMs,
            @Value("${app.mail.timeout-ms:10000}") int timeoutMs
    ) {
        JavaMailSenderImpl mailSender = new JavaMailSenderImpl();
        mailSender.setHost(host);
        mailSender.setPort(port);
        mailSender.setUsername(username);
        mailSender.setPassword(password);
        mailSender.setDefaultEncoding("UTF-8");

        Properties properties = mailSender.getJavaMailProperties();
        properties.put("mail.smtp.auth", Boolean.toString(auth));
        properties.put("mail.smtp.starttls.enable", Boolean.toString(startTls));
        // 기본값은 무한 대기라 SMTP 서버가 응답하지 않으면 발송 스레드가 영원히 묶인다
        properties.put("mail.smtp.connectiontimeout", Integer.toString(connectionTimeoutMs));
        properties.put("mail.smtp.timeout", Integer.toString(timeoutMs));
        properties.put("mail.smtp.writetimeout", Integer.toString(timeoutMs));

        return mailSender;
    }
}
