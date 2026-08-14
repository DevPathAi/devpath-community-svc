package ai.devpath.community.post;

import java.util.regex.Pattern;

/** 목록 미리보기용 본문 요약(순수 로직, DB 비의존). 마크다운 마커 제거 + 공백 collapse + 절단. */
public final class Excerpts {
  private static final Pattern LINE_MARKERS =
      Pattern.compile("(?m)^\\s{0,3}(#{1,6}\\s+|>\\s?|[-*+]\\s+|\\d+\\.\\s+)");
  // (?<!\\) : 백슬래시로 이스케이프된 마커(리터럴 문자)는 서식 마커가 아니므로 제거 대상에서 제외한다.
  private static final Pattern INLINE_MARKS = Pattern.compile("(?<!\\\\)[`*_~]");
  // 마커 제거 후 남은 "\문자" 형태의 마크다운 이스케이프를 원래 리터럴 문자로 복원한다.
  private static final Pattern MD_ESCAPE = Pattern.compile("\\\\([\\\\`*_{}\\[\\]()#+\\-.!<>~])");
  private static final Pattern WHITESPACE = Pattern.compile("\\s+");

  private Excerpts() {}

  public static String from(String bodyMd, int maxLen) {
    if (bodyMd == null || bodyMd.isBlank()) {
      return "";
    }
    // 순서 중요: 마커 제거(lookbehind로 이스케이프 보호) → 이스케이프 복원 → 공백 정리.
    // 예) "a\*b"(리터럴 별표)는 마커 제거 단계에서 보호되어 그대로 남고,
    // 복원 단계에서 "\*" → "*"가 되어 원문 "a*b"가 정확히 살아난다.
    // 복원을 먼저 하면 "a*b"가 되어 버려 마커 제거 단계가 그 "*"를 지워 "ab"로 손상시킨다.
    String plain = LINE_MARKERS.matcher(bodyMd).replaceAll("");
    plain = INLINE_MARKS.matcher(plain).replaceAll("");
    plain = MD_ESCAPE.matcher(plain).replaceAll("$1");
    plain = WHITESPACE.matcher(plain).replaceAll(" ").trim();
    return plain.length() <= maxLen ? plain : plain.substring(0, maxLen).trim() + "…";
  }
}
