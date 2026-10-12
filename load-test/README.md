# 부하 시험 (k6)

## 시나리오

| 파일 | 내용 |
| --- | --- |
| `scenarios/smoke.js` | 헬스 체크 3회 |
| `scenarios/subscription-read.js` | 구독 목록 조회를 일정 속도(`TARGET_RPS`)로 실행 |
| `scenarios/auth-concurrency.js` | 회원가입·로그인·정리(`PHASE`)를 `USERS`명이 동시에 1회씩 실행 |
| `scenarios/auth-burst-with-read.js` | 조회를 15 RPS로 보내는 도중 로그인(또는 회원가입) 100건을 한꺼번에 보내, 폭주 구간 안/밖의 조회 응답 시간을 나눠 기록 |

## 실행 예시 (PowerShell, 저장소 루트)

비밀번호는 저장소 파일에 적지 않고 환경변수로만 넘깁니다.

```powershell
$env:BASE_URL = 'http://<server-address>'
$env:TEST_PASSWORD = '<test-only-password>'

# 1) 동시 인증: 계정 생성 → 로그인 → 정리
$env:RUN_ID = 'auth-20261012'; $env:USERS = '100'
$env:PHASE = 'signup';  k6 run --summary-export .\load-test\results\signup.json .\load-test\scenarios\auth-concurrency.js
$env:PHASE = 'login';   k6 run --summary-export .\load-test\results\login.json  .\load-test\scenarios\auth-concurrency.js

# 2) 로그인 폭주 중 조회 응답 시간 (1)에서 만든 계정 사용, 조회용 계정에는 구독 10개 필요
$env:TEST_EMAIL = '<read-account-email>'; $env:READ_RPS = '15'
k6 run --summary-export .\load-test\results\read-with-login-burst.json .\load-test\scenarios\auth-burst-with-read.js
$env:BURST_ACTION = 'signup'   # 회원가입 폭주로 바꿔 실행

# 정리 (계정 삭제)
$env:PHASE = 'cleanup'; k6 run .\load-test\scenarios\auth-concurrency.js
```

- `RUN_ID`는 영문 소문자·숫자·하이픈만, 실행마다 고유하게 정합니다. 계정 이메일은 `loadtest-<RUN_ID>-<번호>@example.com`입니다.
- `--summary-export`로 원본 요약 JSON을 남겨야 결과를 검증할 수 있습니다.

## 결과

| 폴더 | 내용 |
| --- | --- |
| [`results/2026-10-12-local/`](results/2026-10-12-local/) | 동시 인증 지연 원인 분석과 개선 전후 비교 (로컬, 앱 CPU 2코어 고정, 원본 JSON 포함) |
| [`results/2026-10-02-ec2/`](results/2026-10-02-ec2/) | EC2 t3.micro 조회 시험과 동시 인증 시험. 조회 시험은 원본 JSON 없이 터미널 요약값만 기록 |
