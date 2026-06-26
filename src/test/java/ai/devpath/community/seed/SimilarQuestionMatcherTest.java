package ai.devpath.community.seed;

import static org.assertj.core.api.Assertions.assertThat;

import ai.devpath.community.post.QuestionService;
import ai.devpath.community.post.dto.CreateQuestionRequest;
import ai.devpath.community.seed.dto.SimilarQuestionView;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class SimilarQuestionMatcherTest {

  @Autowired QuestionService questions;
  @Autowired SimilarQuestionMatcher matcher;
  @Autowired JdbcTemplate jdbc;

  private void setEmbedding(long qid, List<Double> emb) {
    StringBuilder sb = new StringBuilder("[");
    for (int i = 0; i < emb.size(); i++) {
      if (i > 0) {
        sb.append(',');
      }
      sb.append(emb.get(i));
    }
    sb.append(']');
    jdbc.update("update community_questions set question_embedding = cast(? as vector) where post_id = ?",
        sb.toString(), qid);
  }

  /** 상수 양수 벡터: 같은 방향 → 코사인거리 ≈ 0. */
  private static List<Double> uniform(double base) {
    List<Double> v = new ArrayList<>();
    for (int i = 0; i < 768; i++) {
      v.add(base);
    }
    return v;
  }

  /** 교대 부호 벡터: uniform과 직교에 가까움 → 코사인거리 ≈ 1.0 (> 0.20). */
  private static List<Double> alternating() {
    List<Double> v = new ArrayList<>();
    for (int i = 0; i < 768; i++) {
      v.add(i % 2 == 0 ? 0.05 : -0.05);
    }
    return v;
  }

  @Test
  void returnsOnlyCloseQuestions() {
    long near = questions.create(80L,
        new CreateQuestionRequest("가까운질문 " + System.nanoTime(), "b", List.of())).id();
    long far = questions.create(80L,
        new CreateQuestionRequest("먼질문 " + System.nanoTime(), "b", List.of())).id();
    setEmbedding(near, uniform(0.05));
    setEmbedding(far, alternating());
    List<Double> query = uniform(0.05); // near와 동일 방향

    var results = matcher.match(query, 10);
    assertThat(results.stream().map(SimilarQuestionView::questionId)).contains(near);
    assertThat(results.stream().map(SimilarQuestionView::questionId)).doesNotContain(far);
  }

  @Test
  void rejectsWrongDimension() {
    // @Repository 예외 변환 AOP가 IllegalArgumentException을 InvalidDataAccessApiUsageException으로 래핑.
    // 768 검증 가드가 실제로 발동했음(cause = IllegalArgumentException)을 단언.
    try {
      matcher.match(List.of(0.1, 0.2), 10);
      org.junit.jupiter.api.Assertions.fail("768 차원 검증 기대");
    } catch (RuntimeException ex) {
      assertThat(ex).hasMessageContaining("768");
      Throwable root = ex instanceof IllegalArgumentException ? ex : ex.getCause();
      assertThat(root).isInstanceOf(IllegalArgumentException.class);
    }
  }
}
