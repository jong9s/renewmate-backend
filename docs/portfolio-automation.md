# RenewMate 저비용 장애 알림

## 현재 구현 범위

- `portfolio-alerts.yml`: 기존 Backend CI 실패/시간 초과를 Slack으로 전달. 기존 CI/CD는 변경하지 않는다.
- 6시간마다 헬스 체크. 정상일 때 조용히 종료하고, 5초 간격 3회 실패하면 알림을 보낸다.
- 수동 `test` 모드로 가상 장애 알림을 보낸다. 실제 서비스를 중단하지 않는다.
- `ec2-log-alerts.yml`: 기존 OIDC/SSM 역할로 최근 6시간의 Docker 로그를 최대 5,000줄 검사한다.
  원문은 전송하지 않고 ERROR 개수와 예외 유형만 Slack에 보낸다. 기본 OFF다.
- `ai-autofix.yml`: 사용자 수동 실행 전용 AI 수정 workflow다. 기본 OFF이며 preflight 모드는 무료다.
  autofix 모드는 Codex가 읽기 전용으로 패치를 제안하고 별도 job이 경로 검사와 Gradle build 후 Draft PR만 만든다.
- 모든 활성화 변수는 미설정이면 OFF. 이 파일들을 추가하는 것만으로 Slack/API에 연결되지 않는다.
- 외부 연결, Git Commit/Push, AWS 리소스 생성은 사용자 승인 후 진행한다.

## 비용

새 AWS 리소스, CloudWatch 로그 수집, Lambda, EventBridge, NAT Gateway를 만들지 않는다.
이번 단계의 추가 AWS 리소스 비용은 없다. 기존 EC2/IPv4/스토리지/트래픽 요금은 계속 적용된다.
헬스 요청으로 소량의 기존 서버 트래픽이 발생하며 완전 무료를 보장하지 않는다.

GitHub 표준 호스팅 runner는 공개 저장소에서 무료이며, 비공개 저장소는 플랜 포함 사용량을 공유한다.
GitHub Free는 월 2,000분과 저장 공간 500MB를 포함한다. 다른 저장소와 기존 빌드/배포도 같은 한도를 사용한다.
헬스 체크 6시간 주기는 월 120~124회다. EC2 로그 검사도 활성화하면 월 120~124회가 추가된다.
각 작업을 1분으로 보면 두 감시를 합쳐 약 240~248분 규모이며 실제 과금 단위와 기존 CI 사용량을 확인해야 한다.
큐 대기, 과금 반올림, 실패 알림/수동 실행은 별도이며 실제 사용량을 확인해야 한다.
추가 artifact/cache는 사용하지 않는다. 포함 사용량을 초과하지 않도록 Actions 유료 사용 중단 예산을 먼저 설정한다.
GitHub Billing에서 대상 Actions budget의 `Stop usage when budget limit is reached`를 활성화하고,
추가 지출 $0 정책이 적용되는지 계정 UI에서 확인한다. 플랜/기존 예산은 아직 조회하지 않았다.

공식 근거 (2026-09-21 확인):
- https://docs.github.com/en/billing/reference/product-usage-included
- https://docs.github.com/en/billing/concepts/product-billing/github-actions

## 승인 후 Slack 활성화

1. 비공개 `renewmate-alarm` 채널(C0C350YRH2R)에 접근 가능한 계정으로 Slack Incoming Webhook을 설치한다.
2. 해당 채널에 연결된 URL을 GitHub repository secret `SLACK_WEBHOOK_URL`에 저장한다. 채팅/코드에 붙이지 않는다.
3. 이 추가 파일들만 검토하고 저장소 기본 브랜치에 반영한다. 기존 미커밋 인증 작업을 통째로 포함하지 않는다.
4. Repository variable `MONITORING_ENABLED=true` 설정 후 Portfolio Alerts를 기본 브랜치에서 `test`로 수동 실행한다.
5. Slack 메시지와 링크를 확인한다. 이 테스트는 서버를 중지하거나 OpenAI를 호출하지 않는다.
6. 이후 Backend CI 실패가 자동 알림으로 오는지 승인된 테스트 PR에서 확인한다. `develop`에 장애 코드를 Push하지 않는다.

`workflow_run`과 스케줄은 기본 브랜치에 workflow가 있어야 동작한다. PR 대상인 develop과 기본 브랜치가 같다고 가정하지 않는다.
develop 반영은 기존 자동 배포를 유발하므로 사용자 확인 없이 Push하지 않는다.
실패한 PR 코드/아티팩트를 알림 job에서 실행하지 않고 기본 브랜치의 스크립트만 사용한다.
알림에는 감지/종료 시각(UTC), 서비스명, 오류 종류, 로그를 볼 수 있는 실행 링크만 포함한다.
토큰/이메일/비밀번호가 섞일 수 있는 원시 로그와 HTTP 응답 본문은 전송하지 않는다.
CI 재실행(run_attempt > 1)은 중복 알림을 생략한다.
Slack 전송 실패 시 job이 실패하며 무한 재시도하지 않는다. GitHub 실패 메일 알림도 켜 두는 것을 권장한다.

Slack 공식 문서: https://docs.slack.dev/messaging/sending-messages-using-incoming-webhooks/

## 외부 헬스 체크

- GitHub runner의 `127.0.0.1`은 EC2가 아니다. 내부 주소를 HEALTHCHECK_URL로 쓰지 않는다.
- `http://54.180.102.20/actuator/health`에서 2026-09-22 HTTP 200과 `status: UP`을 확인했다.
- `HEALTHCHECK_URL` 등록과 `HEALTHCHECK_ENABLED=true` 설정 후 수동 health workflow도 성공했다.
- Actuator 전체를 공개하지 않는다. UP/DOWN만 노출하고 상세 DB 정보는 숨긴다. 401/404도 실패로 처리한다.
- 장애가 계속되면 최대 하루 4회 정기 알림. 영속 중복 제거/복구 알림은 이번 단계에 포함하지 않는다.
- 스케줄 지연/비활성화와 포함 사용량 소진으로 감지가 늦거나 누락될 수 있다. 운영 SLA용 설계가 아니다.

## EC2 Spring 로그 감시

- 새 AWS 리소스를 만들지 않고 기존 GitHub OIDC 역할, EC2 SSM 역할과 Docker 로그만 사용한다.
- `EC2_LOG_MONITORING_ENABLED`가 `true`일 때만 6시간마다 실행한다. 초기에는 false 또는 미설정이다.
- ERROR 줄 수와 최대 5개 예외 클래스 이름만 전달한다. 이메일, 토큰, 요청 본문과 원문 로그는 전송하지 않는다.
- 로그 원문은 EC2 임시 파일에서만 읽고 즉시 제거한다. 스캔 실패 시 Portfolio Alerts가 workflow 실패만 알린다.
- 6시간 경계가 겹치거나 Docker 로그가 회전되면 중복 또는 누락될 수 있으므로 운영 SLA용은 아니다.

## AI 실행 제한과 이후 검증

`AI_AUTOFIX_ENABLED`는 false 또는 미설정으로 유지한다. 전용 OpenAI 프로젝트의 강제 한도와
`OPENAI_API_KEY` GitHub Secret을 준비한 후에만 잠시 true로 전환한다.
UTC 기준 하루 1회/월 3회, 동시 실행 1개, 재실행 차단. 실패/건너뛴 사전 점검도 보수적으로 횟수에 포함한다.
GitHub 기록 조회 실패/불완전 응답은 차단한다. 관리자가 실행 기록을 삭제하면 이 카운터는 약해질 수 있으므로
실제 API 연결 전 계정의 강제 지출 한도도 함께 설정해야 한다. 재시작/시간 경계는 UTC 기준이다.

실행 단계:
1. 전용 OpenAI 프로젝트와 서비스 계정 키를 준비하고 `ai-autofix` environment의 승인자를 지정한다.
2. 공식 Codex GitHub Action을 Linux runner의 `drop-sudo` 및 read-only sandbox로 실행한다.
3. 실패한 Backend CI run ID를 입력하고 먼저 `preflight` 모드로 실행한다. 이 모드는 OpenAI를 호출하지 않는다.
4. `autofix` 모드만 OpenAI API를 호출한다. Slack 장애 알림에서 자동 시작하지 않는다.
5. 모델 응답은 `src/main/java`와 `src/test/java` 아래 Java patch만 허용한다.
6. Java 21과 `./gradlew clean build`를 API 키가 없는 별도 job에서 검증한다.
7. 검증된 patch만 `codex/auto-fix-*` 브랜치와 develop 대상 Draft PR로 발행한다.
8. 자동 Merge, develop 직접 Push, 운영 배포는 금지한다. 검증 후 즉시 AI 변수를 다시 OFF로 바꾼다.

공식 Action: https://learn.chatgpt.com/docs/github-action

## API 예산 알림 (아직 계정에 적용하지 않음)

초기 제안은 전용 프로젝트 월 $3, $1/$2/$3 지출 알림이다. 사용자 승인 전 키 생성/유료 호출을 하지 않는다.
Project Settings > Limits > Spend에서 월 한도와 `Enforce a hard limit`을 설정하고 지출 알림 수신 이메일을 지정한다.
알림만 설정하면 API는 계속 호출된다. 강제 한도는 별도이며 적용 지연으로 소액 초과할 수 있다.
조직 전체 한도를 임의 변경하면 다른 프로젝트가 중단될 수 있으므로 전용 프로젝트 한도를 우선 사용한다.
현재 비용 $0은 이번 자동화의 OpenAI 호출이 없다는 뜻이지 기존 계정 전체 요금이 0이라는 뜻은 아니다.
관리자 키를 GitHub AI job에 넣지 않는다. 초기에는 제공자 이메일 예산 알림을 사용한다.
Slack으로 API 비용 알림까지 받는 연동은 아직 없으며, 관리 API를 통한 별도 집계는 권한 검토 후 추가한다.

공식 근거:
- https://developers.openai.com/api/docs/guides/spend-limits
- https://developers.openai.com/api/docs/guides/admin-apis

## 현재 필요한 권한

| 용도 | Secret / 권한 |
| --- | --- |
| Slack 알림 | SLACK_WEBHOOK_URL, GitHub contents:read |
| AI 사전 점검/분석 | GitHub contents:read + actions:read, autofix일 때만 OPENAI_API_KEY |
| EC2 로그 스캔 | 기존 AWS_ROLE_ARN, EC2_INSTANCE_ID, OIDC id-token:write |
| AWS | 추가 권한 없음. 기존 OIDC/SSM 역할 사용/변경 안 함 |
| AI 실행 | 전용 OpenAI 프로젝트의 OPENAI_API_KEY. autofix job에만 전달 |
| Draft PR 발행 | 검증 후 별도 publisher job에만 contents:write + pull-requests:write |

중단: MONITORING_ENABLED=false, HEALTHCHECK_ENABLED=false,
EC2_LOG_MONITORING_ENABLED=false, AI_AUTOFIX_ENABLED=false.
즉시 중단이 필요하면 실행 중인 workflow도 취소한다. 변수 변경은 이미 실행 중인 job을 중단하지 않는다.
Webhook 노출 시 Slack에서 폐기/재발급한다.

## 로컬 검증

```powershell
python -B -m unittest discover -s ops/automation -p 'test_*.py' -v
```

테스트는 네트워크를 mock 처리한다. 실제 Slack 수신, AWS 접속, GitHub 실행 제한 API 및 유료 AI 품질은 검증하지 않는다.
