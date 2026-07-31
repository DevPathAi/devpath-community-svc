package ai.devpath.community.post;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ExcerptsTest {
  @Test
  void stripsMarkdownMarkersAndCollapsesWhitespace() {
    String body = "# 제목\n\n> 인용\n- 항목 **굵게** `코드`\n여러   공백";
    assertThat(Excerpts.from(body, 140)).isEqualTo("제목 인용 항목 굵게 코드 여러 공백");
  }

  @Test
  void truncatesWithEllipsisWhenOverMax() {
    String out = Excerpts.from("가".repeat(200), 140);
    assertThat(out).hasSize(141); // 140자 + …
    assertThat(out).endsWith("…");
  }

  @Test
  void blankOrNullBodyReturnsEmpty() {
    assertThat(Excerpts.from("   ", 140)).isEmpty();
    assertThat(Excerpts.from(null, 140)).isEmpty();
  }
}
