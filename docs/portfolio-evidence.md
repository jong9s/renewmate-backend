# 포트폴리오 증빙 자료 정리

이 문서는 저장소에서 확인할 수 있는 자동화 근거와 포트폴리오 제출 전에 별도로 추가해야 하는 외부 화면 증빙을 구분합니다.

## CI/CD 흐름

1. `backend-ci.yml`이 `main`·`develop` push와 pull request에서 Java 21 `clean build`를 실행합니다.
2. `develop` push이고 빌드가 성공한 경우에만 배포 작업을 실행합니다.
3. GitHub Actions가 AWS OIDC로 임시 자격 증명을 발급받습니다. 장기 AWS Access Key는 저장하지 않습니다.
4. SSM이 EC2 저장소를 해당 commit SHA로 fast-forward한 뒤 Docker Compose를 다시 빌드하고 실행합니다.
5. EC2 내부 Actuator 상태가 `UP`이 될 때까지 정해진 횟수만 재시도합니다.

저장소 내부 근거:

- `.github/workflows/backend-ci.yml`
- `Dockerfile`
- `docker-compose.yml`
- `src/main/resources/application-prod.properties`

별도로 추가할 외부 증빙:

- CI 성공 실행 URL: **추가 필요**
- SSM 배포 성공 실행 URL: **추가 필요**
- Actuator 확인 단계 화면: **추가 필요**

## 장애 감지와 Slack 알림

`portfolio-alerts.yml`은 Backend CI 실패를 감지하고 예약 또는 수동 헬스 체크를 실행할 수 있습니다. `ec2-log-alerts.yml`은 별도 활성화 변수가 있을 때만 기존 OIDC·SSM 경로로 제한된 시간 범위의 컨테이너 로그를 검사합니다.

Slack에는 정제된 오류 요약, 예외 클래스, 시각, GitHub 실행 링크만 전송합니다. 컨테이너 원본 로그, 요청 본문, 토큰, 이메일, 비밀번호는 전송하지 않습니다.

저장소 내부 근거:

- `.github/workflows/portfolio-alerts.yml`
- `.github/workflows/ec2-log-alerts.yml`
- `ops/automation/notify.py`
- `ops/automation/check_ec2_logs.py`
- `ops/automation/sanitize_logs.py`
- `ops/automation/test_automation.py`

별도로 추가할 외부 증빙:

- Slack 테스트 알림 화면: **추가 필요**
- CI 실패 알림 화면과 실행 URL: **추가 필요**
- EC2 로그 알림 실행 URL: **기능 활성화 후 추가 필요**

## AI 수정과 Draft PR 흐름

AI 수정은 Slack 장애 알림만으로 자동 실행되지 않습니다. 관리자가 실패한 Backend CI 실행 ID를 입력하고 사전 점검 또는 자동 수정 모드를 수동으로 선택해야 합니다.

1. `ai_gate.py`가 기능 활성화 여부, 실행 정보, 재시도 여부, 일·월 사용 제한, 동시 실행을 검사합니다.
2. Codex는 읽기 전용 환경에서 수정 patch만 제안합니다.
3. 별도 작업이 patch를 정제하고 변경 경로를 허용된 Java 소스·테스트 경로로 제한합니다.
4. AI API Key가 없는 검증 작업이 patch를 적용하고 `./gradlew clean build`를 실행합니다.
5. 검증을 통과한 patch만 `codex/auto-fix-*` 브랜치에 올리고 `develop` 대상 Draft PR을 만듭니다.

저장소 내부 근거:

- `.github/workflows/ai-autofix.yml`
- `ops/automation/ai_gate.py`
- `ops/automation/prepare_incident.py`
- `ops/automation/apply_autofix_patch.py`
- `docs/portfolio-automation.md`

별도로 추가할 외부 증빙:

- 사전 점검 성공 실행 URL: **추가 필요**
- AI Draft PR URL: **유료 호출 승인 후 추가 필요**
- OpenAI 프로젝트 예산·한도 화면: **추가 필요**

## 자동화가 수행하지 않는 작업

- `main` 또는 `develop` 직접 push
- 자동 merge
- 운영 DB 직접 변경이나 자동 마이그레이션 명령 실행
- `.env`, GitHub Secrets, SMTP 비밀번호, Google Client Secret, Slack Webhook, JWT Secret, OpenAI API Key 변경
- 자동 운영 롤백이나 Docker volume 삭제
- 원본 운영 로그를 AI prompt, PR 본문, Slack 메시지에 포함
- 기능 변수, 수동 실행, 사용 횟수, 승인 조건을 통과하지 않은 AI 실행

## 성능 개선 증빙

- 개선 전 원본 5회: `performance/results/before-run-01.json`부터 `before-run-05.json`
- 개선 후 원본 5회: `performance/results/after-run-01.json`부터 `after-run-05.json`
- 집계 결과: `performance/results/summary.md`
- MySQL 실행 계획: `performance/sql/explain-before.txt`, `performance/sql/explain-after.txt`
- 재현 조건과 한계: `performance/README.md`

성능 측정은 로컬 MySQL에서만 수행했으며 운영 데이터와 운영 EC2에는 부하를 주지 않았습니다.
