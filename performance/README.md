# 구독 목록 성능 검증

## 측정 대상과 가설

측정 대상은 인증이 필요한 `GET /api/subscriptions`입니다. 개선 전에는 구독 목록을 응답 DTO로 변환하면서 LAZY 관계인 `Category`를 조회해, 구독 목록 SQL 1회와 서로 다른 카테고리 SQL 20회가 발생했습니다. 목록 전용 fetch join을 적용하면 응답에 필요한 데이터를 SQL 1회로 조회할 수 있다고 판단했습니다.

운영 EC2와 운영 데이터는 테스트에 사용하지 않았습니다.

## 고정한 측정 조건

| 항목 | 값 |
| --- | --- |
| 측정일 | 2026-09-23 |
| 실행 환경 | 애플리케이션, MySQL, k6를 같은 로컬 Windows에서 실행 |
| 런타임 | Java 21.0.12, Spring Boot 4.1.0 |
| 데이터베이스 | 로컬 MySQL 8.0.46, 전용 `renewmate_perf` 스키마 |
| 데이터 | 사용자 1명, 카테고리 20개, 구독 1,000건 |
| 응답 크기 | 401,337 bytes |
| 부하 조건 | 10 VU, 워밍업 10초, 측정 30초, 요청 간 대기 없음 |
| 반복 횟수 | 개선 전 5회, 개선 후 5회 |
| 인증 | 같은 로컬 테스트 사용자의 JWT |
| 로그 | k6 측정 중 Hibernate SQL 로그 비활성화 |

애플리케이션 포트, JVM, 스키마, 데이터, 인증 사용자, k6 시나리오, VU, 측정 시간을 모두 동일하게 유지했습니다. 로컬 측정값은 이 환경에서의 개선 근거이며 EC2 처리량을 보장하는 수치는 아닙니다.

## 측정 결과

로컬 환경의 일시적인 변동 영향을 줄이기 위해 5회 측정의 중앙값을 대표값으로 사용했습니다.

| 지표 | 개선 전 중앙값 | 개선 후 중앙값 | 변화 |
| --- | ---: | ---: | ---: |
| 응답 시간 p50 | 28.00 ms | 24.63 ms | 12.05% 감소 |
| 평균 응답 시간 | 28.11 ms | 24.90 ms | 11.41% 감소 |
| 응답 시간 p95 | 34.85 ms | 29.19 ms | 16.26% 감소 |
| 처리량 | 352.99 RPS | 397.97 RPS | 12.74% 증가 |
| HTTP 실패율 | 0% | 0% | 동일 |
| 목록 데이터 SQL | 21회 | 1회 | 95.24% 감소 |

읽기 API이므로 처리량은 DB commit 수가 아닌 초당 HTTP 요청 수인 RPS로 기록했습니다. 회차별 수치와 산술 평균은 [`results/summary.md`](results/summary.md)에 있습니다.

## SQL과 실행 계획 분석

개선 전 동작:

1. 구독 1,000건을 목록 SQL 1회로 조회했습니다.
2. 응답 DTO 변환 과정에서 카테고리 필드에 접근했습니다.
3. 서로 다른 카테고리 20개를 조회하는 SQL이 추가로 실행됐습니다.
4. JWT 필터의 활성 사용자 확인 SQL 1회는 목록 데이터 SQL 횟수에서 제외했습니다.

개선 전 구독 SQL에는 `user_id` 외래 키 인덱스가 있었지만 MySQL은 테이블 스캔을 선택했습니다. 테스트 데이터 1,000건이 모두 한 사용자 소유여서 인덱스 선택도가 없었기 때문입니다. 따라서 같은 인덱스를 중복 추가하는 방식은 실제 병목을 해결하지 못한다고 판단했습니다.

개선 후에는 JPQL `join fetch`로 SQL 1회만 실행했습니다. 실행 계획에서는 카테고리 20건을 스캔하고 기존 구독 카테고리 외래 키 인덱스로 카테고리당 50건을 조회했습니다. join SQL 자체는 개선 전 단일 구독 스캔보다 조금 오래 걸렸지만, 애플리케이션과 DB 사이의 왕복 20회를 제거해 전체 p95와 처리량이 개선됐습니다.

실제 실행 계획 원본:

- [`sql/explain-before.txt`](sql/explain-before.txt)
- [`sql/explain-after.txt`](sql/explain-after.txt)
- [`sql/explain-subscriptions.sql`](sql/explain-subscriptions.sql)

## 재현 방법

시드 데이터는 분리된 로컬 스키마에만 생성해야 하며 운영 DB에서는 실행하지 않습니다.

```powershell
$env:BASE_URL = "http://127.0.0.1:18081"
$env:ACCESS_TOKEN = "<local-performance-test-jwt>"
$env:VUS = "10"
$env:DURATION = "30s"
k6 run --summary-export performance/results/after-run-01.json performance/k6/subscriptions.js
```

같은 코드 버전에서 10초 워밍업 후 명령을 5회 실행합니다. 개선 전과 개선 후의 원본 결과는 `results/`에 있습니다. 다음 명령으로 집계값을 다시 계산할 수 있습니다.

```powershell
python performance/scripts/summarize_results.py performance/results
```

## 측정의 한계

- 모든 구독이 한 사용자 소유이므로 N+1 재현에는 적합하지만 다중 사용자 환경의 인덱스 선택도는 반영하지 못합니다.
- 애플리케이션, 부하 발생기, MySQL이 한 PC를 사용해 AWS의 CPU·네트워크 조건과 다릅니다.
- 30초 읽기 부하 테스트이며 장시간 안정성, 순간 급증, 쓰기 경합을 측정하지 않았습니다.
- 개선 후 SQL 1회는 `SubscriptionRepositoryQueryIntegrationTest`로 회귀 테스트하지만, 개선 전 21회는 당시 측정한 이력 자료입니다.
