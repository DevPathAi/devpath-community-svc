package ai.devpath.community.post;

import java.util.regex.Pattern;

/** 목록 미리보기용 본문 요약(순수 로직, DB 비의존). 마크다운 마커 제거 + 공백 collapse + 절단. */
public final class Excerpts {
  private static final Pattern LINE_MARKERS =
      Pattern.compile("(?m)^\\s{0,3}(#{1,6}\\s+|>\\s?|[-*+]\\s+|\\d+\\.\\s+)");
  private static final Pattern INLINE_MARKS = Pattern.compile("[`*_~]");
  private static final Pattern WHITESPACE = Pattern.compile("\\s+");

  private Excerpts() {}

  public static String from(String bodyMd, int maxLen) {
    if (bodyMd == null || bodyMd.isBlank()) {
      return "";
    }
    String plain = LINE_MARKERS.matcher(bodyMd).replaceAll("");
    plain = INLINE_MARKS.matcher(plain).replaceAll("");
    plain = WHITESPACE.matcher(plain).replaceAll(" ").trim();
    return plain.length() <= maxLen ? plain : plain.substring(0, maxLen).trim() + "…";
  }
}
