# RenewMate Backend

반복되는 정기결제와 구독 정보를 한곳에서 관리하기 위한 Spring Boot REST API입니다.

회원은 이메일 또는 Google 계정으로 로그인해 구독을 등록하고, 결제 예정 내역과 지출 통계를 확인할 수 있습니다. 기능 구현에 그치지 않고 인증 보안, 테스트, 성능 병목 개선, AWS 배포와 장애 대응 자동화까지 백엔드 서비스의 운영 흐름을 함께 다뤘습니다.

## 핵심 기능

| 영역 | 구현 내용 |
| --- | --- |
| 인증과 계정 | 이메일 회원가입·로그인, JWT 인증, Google OAuth2/OIDC 로그인, 비밀번호 재설정 메일, 회원 정보 수정과 탈퇴 |
| 구독 관리 | 구독 등록·조회·수정·삭제, 활성 상태 변경, 결제 주기별 다음 결제일 계산 |
| 조회와 분석 | 결제 예정 구독, 대시보드 요약, 서비스·카테고리별 지출 통계 |
| 사용자 편의 | 결제 알림 조회·읽음 처리, 사용자별 통화 및 알림 설정 |
| 운영 | Docker Compose 기반 실행, GitHub Actions CI/CD, 배포 후 Actuator 상태 확인 |
| 장애 대응 | CI 실패·헬스 체크·EC2 로그 이상 Slack 알림, 제한된 AI 수정안 검증과 Draft PR 생성 |

## 기술 스택

| 구분 | 기술 |
| --- | --- |
| Backend | Java 21, Spring Boot 4.1.0, Spring MVC, Spring Data JPA |
| Security | Spring Security, JWT, OAuth2 Client, OpenID Connect |
| Database | MySQL 8.4, H2(test) |
| API | REST API, Bean Validation, Springdoc OpenAPI |
| Test | JUnit 5, Mockito, Spring Boot Test |
| Infra | Docker, Docker Compose, AWS EC2, AWS Systems Manager |
| CI/CD·운영 | GitHub Actions, AWS OIDC, Spring Boot Actuator, Slack Incoming Webhook |
| Performance | k6, MySQL `EXPLAIN ANALYZE` |

## 배포와 장애 대응

`develop` 브랜치에 코드가 반영되면 GitHub Actions가 Java 21 환경에서 테스트와 빌드를 수행합니다. 성공한 커밋은 장기 AWS 키 대신 GitHub OIDC로 권한을 얻어 Systems Manager 명령으로 EC2에 배포하며, 마지막 단계에서 `/actuator/health`의 `UP` 응답을 확인합니다.

장애 대응 자동화는 비용과 권한을 통제할 수 있도록 기능별 Repository variable로 활성화합니다.

- CI 실패와 시간 초과, 외부 헬스 체크 실패를 Slack으로 알립니다.
- 선택적으로 EC2의 최근 Docker 로그를 검사하고, 원문 대신 오류 개수와 예외 유형만 전달합니다.
- AI 자동 수정은 수동 실행 전용이며 기본값은 OFF입니다. Codex가 제안한 Java 패치만 별도 작업에서 경로 검사와 전체 빌드를 통과한 경우 `develop` 대상 Draft PR로 생성합니다. 자동 병합이나 운영 배포는 수행하지 않습니다.

상세한 활성화 조건, 권한과 비용 제한은 [`docs/portfolio-automation.md`](docs/portfolio-automation.md), Google 로그인 흐름은 [`docs/google-oauth.md`](docs/google-oauth.md)에서 확인할 수 있습니다.

## 목차

- [구독 목록 성능 개선](#구독-목록-성능-개선)
- [측정 환경](#측정-환경)
- [측정 결과](#측정-결과)
- [병목 분석](#병목-분석)
- [개선 방법](#개선-방법)
- [재현 파일](#재현-파일)
- [검증](#검증)

## 구독 목록 성능 개선

`GET /api/subscriptions`의 JPA N+1 문제를 로컬 MySQL과 k6로 재현하고 개선했습니다. 운영 EC2와 운영 DB는 테스트에 사용하지 않았습니다.

### 측정 환경

| 항목 | 값 |
| --- | --- |
| 측정일 | 2026-09-23 |
| 애플리케이션 | Spring Boot 4.1.0, Java 21.0.12 |
| 데이터베이스 | 로컬 MySQL 8.0.46, 전용 `renewmate_perf` 스키마 |
| 부하 도구 | k6 2.3.0, 동일 Windows 호스트에서 실행 |
| 데이터 | 사용자 1명, 카테고리 20개, 구독 1,000건 |
| API 응답 크기 | 401,337 bytes |
| 부하 조건 | 10 VU, 워밍업 10초, 측정 30초, think time 없음 |
| 인증 | 사전에 발급한 동일 사용자의 JWT Bearer Token |

전후 테스트는 같은 JAR 실행 방식, 포트, JVM, 데이터베이스와 데이터로 진행했습니다. SQL 확인 시에만 Hibernate SQL 로그를 켰고, k6 측정 시에는 로그를 껐습니다. 각 수치는 워밍업 이후 한 번의 30초 측정 결과이므로 다른 장비나 실제 운영 환경의 성능을 보장하지 않습니다.

### 측정 결과

| 지표 | 개선 전 | 개선 후 | 변화 |
| --- | ---: | ---: | ---: |
| 응답 시간 평균 | 21.97 ms | 19.78 ms | 9.97% 감소 |
| 응답 시간 p95 | 25.71 ms | 23.40 ms | 8.98% 감소 |
| 처리량 | 451.36 RPS | 501.02 RPS | 11.00% 증가 |
| 총 요청 수 | 13,550 | 15,037 | 10.97% 증가 |
| HTTP 오류율 | 0% | 0% | 동일 |
| 목록 데이터 SQL 수 | 21회 | 1회 | 95.24% 감소 |

읽기 API이므로 처리량은 트랜잭션 커밋 수가 아닌 초당 HTTP 요청 수(RPS)로 기록했습니다. 원본 k6 결과는 [`performance/results/before.json`](performance/results/before.json)과 [`performance/results/after.json`](performance/results/after.json)에 있습니다.

### 병목 분석

`Subscription.category`는 `LAZY` 연관관계이지만 응답 DTO 변환 과정에서 모든 구독의 카테고리 ID와 이름을 조회합니다. 개선 전 SQL 로그에서는 구독 목록 쿼리 1회 이후 서로 다른 카테고리 20개를 조회하는 SQL 20회가 추가로 실행됐습니다. 인증 필터의 사용자 상태 확인 SQL까지 포함하면 API 요청당 총 22회였습니다.

```sql
select ... from subscriptions where user_id = ?;
select ... from categories where category_id = ?; -- 서로 다른 카테고리마다 반복
```

MySQL `EXPLAIN ANALYZE`에서 기존 목록 쿼리가 1,000행 테이블 스캔을 수행하는 것을 확인했습니다. `user_id` 외래 키 인덱스는 이미 존재했지만 테스트 데이터의 모든 행이 같은 사용자 소유라 선택도가 100%였고, 옵티마이저가 테이블 스캔을 선택했습니다. 따라서 근거 없이 중복 인덱스를 추가하지 않았습니다. 저장소에는 실행 계획을 다시 확인할 수 있는 SQL을 포함했으며, 당시 출력 원문은 별도로 보관하지 않았습니다.

### 개선 방법

목록 전용 Repository 쿼리에 JPQL `join fetch`를 사용해 구독과 카테고리를 한 번에 조회하도록 변경했습니다.

```java
@Query("""
        select subscription
        from Subscription subscription
        join fetch subscription.category
        where subscription.user.userId = :userId
        """)
List<Subscription> findAllWithCategoryByUserId(@Param("userId") Long userId);
```

개선 후에는 인증 확인 SQL 1회와 목록 fetch join SQL 1회만 실행됩니다. 단일 SQL 자체의 실행 시간보다 애플리케이션과 DB 사이의 반복 왕복 20회를 제거하는 데 초점을 맞췄고, 그 결과 전체 API p95와 처리량이 개선됐습니다.

### 재현 파일

- [`performance/k6/subscriptions.js`](performance/k6/subscriptions.js): 동일 부하 시나리오
- [`performance/sql/seed-subscriptions.sql`](performance/sql/seed-subscriptions.sql): 로컬 전용 고정 데이터 생성
- [`performance/sql/explain-subscriptions.sql`](performance/sql/explain-subscriptions.sql): 개선 전 SQL 실행 계획 확인

테스트용 사용자를 `perf@renewmate.local`로 먼저 가입시키고 전용 `renewmate_perf` 스키마에서만 시드 SQL을 실행해야 합니다. k6 실행에는 토큰을 파일에 저장하지 않고 환경 변수로 전달합니다.

```powershell
$env:BASE_URL = "http://127.0.0.1:18081"
$env:ACCESS_TOKEN = "<local-performance-test-jwt>"
$env:VUS = "10"
$env:DURATION = "30s"
k6 run performance/k6/subscriptions.js
```

### 검증

```text
./gradlew clean test bootJar
BUILD SUCCESSFUL
```

구독 목록 서비스가 카테고리를 함께 가져오는 Repository 메서드를 호출하는지 검증하는 단위 테스트도 추가했습니다.
