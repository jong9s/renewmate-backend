# Google OAuth2 로그인

RenewMate 백엔드는 Spring Security OAuth2 Client의 Authorization Code + OIDC 흐름을 사용한다.
브라우저나 프론트엔드가 Google ID 토큰을 직접 검증하거나 RenewMate 사용자 정보를 결정하지 않는다.

## API 흐름

### 1. 로그인 시작

```http
GET /api/auth/google/start
```

백엔드는 `/oauth2/authorization/google`을 거쳐 Google authorization endpoint로 리디렉션한다.
Spring Security가 세션에 authorization request를 저장하고 callback의 `state`를 검증한다.

Google Console에 등록할 로컬 callback:

```text
http://localhost:8081/login/oauth2/code/google
```

### 2. 백엔드 callback

Google은 authorization code를 다음 Spring Security endpoint로 전달한다.

```http
GET /login/oauth2/code/google
```

백엔드는 code를 Google token endpoint에서 교환하고 서명, issuer, audience, 만료와 OIDC claims를
검증한다. 검증된 `sub`와 이메일만 RenewMate 사용자 연결에 사용한다.

성공하면 60초짜리 일회용 코드를 발급해 다음 주소로 리디렉션한다.

```text
http://localhost:5173/auth/google/callback#code=<one-time-code>
```

URL fragment는 HTTP 요청과 Referer에 포함되지 않는다. 프론트는 코드를 읽은 즉시 주소에서 제거한다.
일회용 코드 원문은 DB에 저장하지 않고 SHA-256 해시, 만료 시각, 사용 시각만 저장한다.

### 3. RenewMate JWT 교환

```http
POST /api/auth/google/exchange
Content-Type: application/json

{
  "code": "<one-time-code>"
}
```

정상 응답은 기존 이메일 로그인과 같은 `LoginResponse`다. 교환 과정은 pessimistic write lock으로
직렬화되며 사용, 만료, 비활성 계정, 알 수 없는 코드는 모두 거부된다.

## 환경변수

```properties
GOOGLE_CLIENT_ID=...
GOOGLE_CLIENT_SECRET=...
FRONTEND_BASE_URL=http://localhost:5173
OAUTH_EXCHANGE_CODE_EXPIRATION_SECONDS=60
```

Client Secret은 저장소나 프론트에 넣지 않는다. 설정이 누락되면 애플리케이션 시작을 실패시켜
잘못 구성된 소셜 로그인을 조기에 발견한다.

## 데이터베이스

로컬/운영 DB에는 다음 수동 마이그레이션을 먼저 적용한다.

```text
src/main/resources/db/manual/V20260922__google_oauth_authorization_code.sql
```

로컬 DB에는 2026-09-22 적용 완료했다. 운영 DB 적용은 배포 전 별도 승인과 백업이 필요하다.
