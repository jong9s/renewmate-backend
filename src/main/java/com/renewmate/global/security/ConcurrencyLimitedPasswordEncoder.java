package com.renewmate.global.security;

import org.springframework.resilience.annotation.ConcurrencyLimit;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * BCrypt 해싱의 동시 실행 수를 제한하는 PasswordEncoder.
 *
 * BCrypt(strength 10)는 요청당 수십 ms의 CPU를 쓰므로, 로그인·회원가입이 한꺼번에 몰리면
 * 해싱 스레드가 CPU를 모두 차지해 인증과 무관한 API까지 수 초씩 지연된다.
 * 동시에 해싱하는 스레드를 제한해 나머지 요청이 CPU를 쓸 수 있게 한다.
 * 한도를 넘은 요청은 거절하지 않고 차례를 기다린다(BLOCK).
 * 클래스 단위로 선언해 encode·matches가 하나의 한도를 공유한다.
 */
@ConcurrencyLimit(limitString = "${app.security.password-hashing.max-concurrency:2}")
public class ConcurrencyLimitedPasswordEncoder implements PasswordEncoder {

    private final PasswordEncoder delegate;

    public ConcurrencyLimitedPasswordEncoder(PasswordEncoder delegate) {
        this.delegate = delegate;
    }

    @Override
    public String encode(CharSequence rawPassword) {
        return delegate.encode(rawPassword);
    }

    @Override
    public boolean matches(CharSequence rawPassword, String encodedPassword) {
        return delegate.matches(rawPassword, encodedPassword);
    }

    @Override
    public boolean upgradeEncoding(String encodedPassword) {
        return delegate.upgradeEncoding(encodedPassword);
    }
}
