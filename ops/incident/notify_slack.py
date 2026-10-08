"""Send incident notifications to Slack through an Incoming Webhook.

Usage: notify_slack.py detected|result
Everything is sent as plain_text so incident content cannot trigger mentions or links.
"""

import json
import os
import sys
from pathlib import Path
from urllib.error import URLError
from urllib.parse import urlsplit
from urllib.request import HTTPRedirectHandler, Request, build_opener

MAX_ANALYSIS_CHARS = 1500

RESULT_LABELS = {
    "pr_created": "✅ AI 수정 Draft PR 생성 완료 — 리뷰 후 머지해주세요",
    "no_change": "ℹ️ AI가 코드 수정이 필요 없다고 판단 (환경·일시적 오류 가능성)",
    "rejected": "⛔ AI 변경이 허용 범위(src/**/*.java) 밖이라 폐기",
    "build_failed": "❌ AI 수정본이 빌드·테스트를 통과하지 못해 PR 미생성",
    "ai_failed": "❌ AI 실행 실패 (CLAUDE_CODE_OAUTH_TOKEN 만료·구독 사용량 한도 확인)",
    "publish_failed": "❌ 수정본은 검증됐지만 브랜치 push 또는 PR 생성 실패",
}


class NoRedirect(HTTPRedirectHandler):
    def redirect_request(self, req, fp, code, msg, headers, newurl):
        return None


def send(blocks, fallback):
    webhook = os.environ.get("SLACK_WEBHOOK_URL", "")
    url = urlsplit(webhook)
    if url.scheme != "https" or url.netloc != "hooks.slack.com" or not url.path.startswith("/services/"):
        raise ValueError("SLACK_WEBHOOK_URL secret is missing or invalid")

    body = json.dumps({"text": fallback, "blocks": blocks,
                       "unfurl_links": False, "unfurl_media": False}).encode("utf-8")
    request = Request(webhook, data=body, headers={"Content-Type": "application/json"})
    try:
        with build_opener(NoRedirect()).open(request, timeout=10) as response:
            if response.status != 200:
                raise ValueError("Slack rejected the notification")
    except (URLError, OSError):
        # 예외 메시지에 Webhook URL이 포함될 수 있으므로 출력하지 않는다.
        raise ValueError("Slack delivery failed") from None


def section(text):
    return {"type": "section", "text": {"type": "plain_text", "text": text[:2900], "emoji": True}}


def detected():
    kind = os.environ.get("INCIDENT_KIND", "")
    header = {"production": "🚨 운영 서버 에러 감지",
              "ci": "🚨 Backend CI 실패",
              "test": "🧪 알림 테스트"}.get(kind, "🚨 이슈 감지")
    if os.environ.get("SHOULD_FIX") == "true":
        next_step = "🤖 Claude가 코드를 분석하고 수정 PR을 준비합니다."
    else:
        next_step = f"⏸️ AI 자동 수정 생략: {os.environ.get('SKIP_REASON') or '-'}"
    lines = [
        f"{header} | RenewMate backend",
        os.environ.get("INCIDENT_SUMMARY", ""),
        next_step,
        f"Run: {os.environ.get('RUN_URL', '')}",
    ]
    send([section("\n".join(lines))], header)


def result():
    outcome = os.environ.get("RESULT", "")
    label = RESULT_LABELS.get(outcome, f"AI 수정 결과 확인 필요 ({outcome or 'unknown'})")
    lines = [f"{label}", f"이슈: {os.environ.get('INCIDENT_TITLE', '')}"]
    if os.environ.get("PR_URL"):
        lines.append(f"PR: {os.environ['PR_URL']}")
    lines.append(f"Run: {os.environ.get('RUN_URL', '')}")
    blocks = [section("\n".join(lines))]

    summary_file = Path(os.environ.get("FIX_SUMMARY_FILE", ""))
    if summary_file.is_file():
        analysis = summary_file.read_text(encoding="utf-8").strip()
        if analysis:
            if len(analysis) > MAX_ANALYSIS_CHARS:
                analysis = analysis[:MAX_ANALYSIS_CHARS] + "\n...(PR 본문 참고)"
            blocks.append(section("🧠 AI 분석 요약\n" + analysis))
    send(blocks, label)


if __name__ == "__main__":
    try:
        {"detected": detected, "result": result}[sys.argv[1]]()
        print("Slack notification delivered")
    except (IndexError, KeyError, ValueError, OSError) as error:
        print(f"Slack notification failed: {error}", file=sys.stderr)
        sys.exit(1)
