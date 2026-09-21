# RenewMate 저비용 장애 알림

## 현재 구현 범위

- `portfolio-alerts.yml`: 기존 Backend CI 실패/시간 초과를 Slack으로 전달. 기존 CI/CD는 변경하지 않는다.
- 6시간마다 헬스 체크. 정상일 때 조용히 종료하고, 5초 간격 3회 실패하면 알림을 보낸다.
- 수동 `test` 모드로 가상 장애 알림을 보낸다. 실제 서비스를 중단하지 않는다.
- `ai-preflight.yml`: 기본 OFF인 실행 제한 사전 점검. **AI 호출/코드 수정/PR 생성은 아직 연결하지 않았다.**
- 모든 활성화 변수는 미설정이면 OFF. 이 파일들을 추가하는 것만으로 Slack/API에 연결되지 않는다.
- 외부 연결, Git Commit/Push, AWS 리소스 생성은 사용자 승인 후 진행한다.

## 비용

새 AWS 리소스, CloudWatch 로그 수집, Lambda, EventBridge, NAT Gateway를 만들지 않는다.
이번 단계의 추가 AWS 리소스 비용은 없다. 기존 EC2/IPv4/스토리지/트래픽 요금은 계속 적용된다.
헬스 요청으로 소량의 기존 서버 트래픽이 발생하며 완전 무료를 보장하지 않는다.

GitHub 표준 호스팅 runner는 공개 저장소에서 무료이며, 비공개 저장소는 플랜 포함 사용량을 공유한다.
GitHub Free는 월 2,000분과 저장 공간 500MB를 포함한다. 다른 저장소와 기존 빌드/배포도 같은 한도를 사용한다.
6시간 주기는 월 120~124회. 각 작업 1분이면 약 120~124분, timeout 3분 기준 최대 약 360~372분의 실행 시간 규모다.
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
- 승인 후 Nginx가 공개하는 최소 정보 헬스 URL을 확인하고 `HEALTHCHECK_URL`에 설정한다.
- `http://54.180.102.20/actuator/health`는 후보일 뿐이며 현재 경로/접근 가능 여부는 검증하지 않았다.
- `HEALTHCHECK_ENABLED=true` 설정 후 수동 `health`로 검사하고 스케줄을 사용한다.
- Actuator 전체를 공개하지 않는다. UP/DOWN만 노출하고 상세 DB 정보는 숨긴다. 401/404도 실패로 처리한다.
- 장애가 계속되면 최대 하루 4회 정기 알림. 영속 중복 제거/복구 알림은 이번 단계에 포함하지 않는다.
- 스케줄 지연/비활성화와 포함 사용량 소진으로 감지가 늦거나 누락될 수 있다. 운영 SLA용 설계가 아니다.
- EC2 Spring ERROR 로그의 실시간 감지는 아직 없다. 필요하면 기존 EC2의 로컬 systemd timer로
  Docker 로그를 제한량 읽고 민감 정보를 제거해 전송하는 단계를 별도 승인 후 추가한다.
  이 방식은 EC2 전체 장애를 스스로 알리지 못하므로 외부 체크를 대체하지 않는다.

## AI 실행 제한과 이후 검증

현재는 유료 호출이 없는 사전 점검만 구현했다. `AI_AUTOFIX_ENABLED`는 false 또는 미설정으로 유지한다.
승인 후 true로 바꾸어 기본 브랜치에서 수동 실행할 수 있지만, 이것만으로 AI가 연결되지는 않는다.
UTC 기준 하루 1회/월 3회, 동시 실행 1개, 재실행 차단. 실패/건너뛴 사전 점검도 보수적으로 횟수에 포함한다.
GitHub 기록 조회 실패/불완전 응답은 차단한다. 관리자가 실행 기록을 삭제하면 이 카운터는 약해질 수 있으므로
실제 API 연결 전 계정의 강제 지출 한도도 함께 설정해야 한다. 재시작/시간 경계는 UTC 기준이다.

다음 승인 단계:
1. 전용 OpenAI 프로젝트와 서비스 계정 키를 준비하고 `ai-autofix` environment의 승인자를 지정한다.
2. 공식 Codex GitHub Action을 Linux 격리 runner에서 API 키로 실행한다. ChatGPT 포함 사용량과 같다고 가정하지 않는다.
3. 위 실행 제한을 유료 호출 직전에 적용한다. 알림에서 자동 연결하지 않고 수동으로 시작한다.
4. 운영과 무관한 테스트 장애를 별도 브랜치에서 재현하고 JUnit 실패를 먼저 확인한다.
5. 최소 수정 후 Java 21 JUnit 및 `./gradlew clean build`를 별도 검증 job에서 실행한다.
6. 검증 job에는 OpenAI/Slack/AWS 키를 주지 않는다. 발행 job도 모델이 만든 코드를 실행하지 않는다.
7. 허용된 소스/테스트 파일만 발행한다. `.env`, 배포 workflow, 인증 키, 운영 설정 변경은 차단한다.
8. 발행 job만 contents:write, pull-requests:write를 갖고 `codex/*` 브랜치와 develop 대상 Draft PR만 만든다.
9. PR에 원인/수정/테스트 결과/추정 사용량을 기재한다. 자동 Merge, develop 직접 Push, 운영 배포는 금지한다.
10. 검증 후 다시 OFF. workflow timeout은 비용 상한이 아니므로 토큰 사용/계정 비용도 확인한다.

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
| AI 사전 점검 | GitHub contents:read + actions:read (자동 제공 GITHUB_TOKEN) |
| AWS | 추가 권한 없음. 기존 OIDC/SSM 역할 사용/변경 안 함 |
| 향후 AI | 전용 프로젝트 API 키. 현재 불필요 |
| 향후 Draft PR 발행 | 별도 job의 contents:write + pull-requests:write. 현재 부여 안 함 |

중단: MONITORING_ENABLED=false, HEALTHCHECK_ENABLED=false, AI_AUTOFIX_ENABLED=false.
즉시 중단이 필요하면 실행 중인 workflow도 취소한다. 변수 변경은 이미 실행 중인 job을 중단하지 않는다.
Webhook 노출 시 Slack에서 폐기/재발급한다.

## 로컬 검증

```powershell
python -B -m unittest discover -s ops/automation -p 'test_*.py' -v
```

테스트는 네트워크를 mock 처리한다. 실제 Slack 수신, AWS 접속, GitHub 실행 제한 API 및 유료 AI 품질은 검증하지 않는다.
