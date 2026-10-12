package com.renewmate.global.security;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.resilience.annotation.EnableResilientMethods;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

@SpringJUnitConfig
@TestPropertySource(properties = "app.security.password-hashing.max-concurrency=2")
class ConcurrencyLimitedPasswordEncoderTest {

    private static final AtomicInteger running = new AtomicInteger();
    private static final AtomicInteger maxRunning = new AtomicInteger();

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Test
    @DisplayName("encode와 matches를 합쳐 동시에 설정한 개수까지만 해싱한다")
    void shouldLimitConcurrentHashingAcrossMethods() throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(8);
        List<Future<?>> futures = new ArrayList<>();

        for (int i = 0; i < 16; i++) {
            boolean encode = i % 2 == 0;
            futures.add(executor.submit(() -> encode
                    ? passwordEncoder.encode("password")
                    : passwordEncoder.matches("password", "hash")));
        }
        for (Future<?> future : futures) {
            future.get(10, TimeUnit.SECONDS);
        }
        executor.shutdown();

        assertEquals(2, maxRunning.get());
    }

    @Configuration
    @EnableResilientMethods
    static class Config {

        @Bean
        PasswordEncoder passwordEncoder() {
            return new ConcurrencyLimitedPasswordEncoder(new SlowPasswordEncoder());
        }
    }

    // 동시에 실행 중인 해싱 개수의 최댓값을 기록하는 느린 인코더
    static class SlowPasswordEncoder implements PasswordEncoder {

        @Override
        public String encode(CharSequence rawPassword) {
            hash();
            return "hash";
        }

        @Override
        public boolean matches(CharSequence rawPassword, String encodedPassword) {
            hash();
            return true;
        }

        private void hash() {
            maxRunning.accumulateAndGet(running.incrementAndGet(), Math::max);
            try {
                Thread.sleep(50);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                running.decrementAndGet();
            }
        }
    }
}
