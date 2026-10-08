package com.renewmate.global.incident;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 운영 장애를 GitHub Actions로 전달하기 위한 요약 정보.
 * 요청 본문·헤더·쿼리 스트링은 담지 않고, 예외 메시지는 민감 정보를 마스킹한 뒤 길이를 제한한다.
 */
public record IncidentReport(
        String fingerprint,
        String exceptionType,
        String message,
        String location,
        String method,
        String path,
        String stackTrace,
        String occurredAt
) {

    private static final String APP_PACKAGE = "com.renewmate.";
    private static final int MAX_MESSAGE_LENGTH = 300;
    private static final int MAX_STACK_FRAMES = 15;

    private static final Pattern EMAIL = Pattern.compile("[\\w.+-]+@[\\w-]+(\\.[\\w-]+)+");
    private static final Pattern SECRET_ASSIGNMENT = Pattern.compile(
            "(?i)(password|secret|token|authorization|api[_-]?key)\\s*[=:]\\s*\\S+");
    private static final Pattern LONG_TOKEN = Pattern.compile("[A-Za-z0-9_\\-.]{32,}");
    private static final Pattern NUMERIC_SEGMENT = Pattern.compile("/\\d+(?=/|$)");

    public static IncidentReport from(Throwable exception, String method, String requestUri) {
        Throwable root = rootCause(exception);
        String location = firstAppFrame(root);
        String exceptionType = root.getClass().getName();

        return new IncidentReport(
                fingerprint(exceptionType, location),
                exceptionType,
                sanitize(root.getMessage()),
                location,
                method,
                normalizePath(requestUri),
                stackTrace(root),
                OffsetDateTime.now().toString()
        );
    }

    /** GitHub repository_dispatch의 client_payload는 최상위 속성을 10개까지만 허용한다. */
    public Map<String, Object> toPayload() {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("fingerprint", fingerprint);
        payload.put("exceptionType", exceptionType);
        payload.put("message", message);
        payload.put("location", location);
        payload.put("method", method);
        payload.put("path", path);
        payload.put("stackTrace", stackTrace);
        payload.put("occurredAt", occurredAt);
        return payload;
    }

    static String sanitize(String message) {
        if (message == null || message.isBlank()) {
            return "";
        }
        String masked = EMAIL.matcher(message).replaceAll("<email>");
        masked = SECRET_ASSIGNMENT.matcher(masked).replaceAll("$1=<masked>");
        masked = LONG_TOKEN.matcher(masked).replaceAll("<masked>");
        masked = masked.replaceAll("\\s+", " ").trim();
        return masked.length() > MAX_MESSAGE_LENGTH
                ? masked.substring(0, MAX_MESSAGE_LENGTH) + "..."
                : masked;
    }

    /** 같은 예외가 리소스 ID만 다르게 반복될 때 하나의 경로로 묶는다. */
    static String normalizePath(String requestUri) {
        if (requestUri == null) {
            return "";
        }
        return NUMERIC_SEGMENT.matcher(requestUri).replaceAll("/{id}");
    }

    private static Throwable rootCause(Throwable exception) {
        Throwable current = exception;
        while (current.getCause() != null && current.getCause() != current) {
            current = current.getCause();
        }
        return current;
    }

    private static String firstAppFrame(Throwable exception) {
        return Arrays.stream(exception.getStackTrace())
                .filter(frame -> frame.getClassName().startsWith(APP_PACKAGE))
                .findFirst()
                .or(() -> Arrays.stream(exception.getStackTrace()).findFirst())
                .map(IncidentReport::formatFrame)
                .orElse("unknown");
    }

    private static String stackTrace(Throwable exception) {
        return Arrays.stream(exception.getStackTrace())
                .limit(MAX_STACK_FRAMES)
                .map(frame -> "at " + formatFrame(frame))
                .collect(Collectors.joining("\n"));
    }

    private static String formatFrame(StackTraceElement frame) {
        return frame.getClassName() + "." + frame.getMethodName()
                + "(" + frame.getFileName() + ":" + frame.getLineNumber() + ")";
    }

    private static String fingerprint(String exceptionType, String location) {
        // 줄 번호는 코드 수정마다 바뀌므로 지문에서 제외한다.
        String stableLocation = location.replaceAll(":\\d+\\)$", ")");
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest((exceptionType + "|" + stableLocation).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash).substring(0, 12);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available.", exception);
        }
    }
}
