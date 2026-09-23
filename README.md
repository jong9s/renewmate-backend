# RenewMate Backend

정기결제와 구독을 관리하는 Spring Boot REST API입니다.

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

MySQL `EXPLAIN ANALYZE` 결과, 기존 목록 쿼리는 1,000행 테이블 스캔에 약 0.75ms가 걸렸습니다. `user_id` 외래 키 인덱스는 이미 존재했지만 테스트 데이터의 모든 행이 같은 사용자 소유라 선택도가 100%였고, 옵티마이저가 테이블 스캔을 선택했습니다. 따라서 근거 없이 중복 인덱스를 추가하지 않았습니다.

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

개선 후에는 인증 확인 SQL 1회와 목록 fetch join SQL 1회만 실행됩니다. fetch join의 `EXPLAIN ANALYZE` 실행 시간은 약 1.28ms였지만, 애플리케이션과 DB 사이의 반복 왕복 20회를 제거해 전체 API p95와 처리량이 개선됐습니다.

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

구독 목록 서비스가 카테고리를 함께 가져오는 Repository 메서드를 사용하는지 검증하는 단위 테스트도 추가했습니다.
