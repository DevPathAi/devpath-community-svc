package ai.devpath.community.seed;

import ai.devpath.community.seed.dto.SimilarQuestionView;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** pgvector 코사인거리 유사질문 탐지(learning ContentEmbeddingMatcher 패턴). 거리<0.20 = 유사도>=0.80. */
@Repository
public class SimilarQuestionMatcher {

  private static final double MAX_DISTANCE = 0.20;

  private final JdbcTemplate jdbc;

  public SimilarQuestionMatcher(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  public List<SimilarQuestionView> match(List<Double> queryEmbedding, int limit) {
    String vector = toVectorLiteral(queryEmbedding);
    String sql = """
        select q.post_id as question_id, p.title as title,
               q.question_embedding <=> cast(? as vector) as distance
        from community_questions q
        join community_posts p on p.id = q.post_id
        where q.question_embedding is not null
          and p.status = 'PUBLISHED'
          and (q.question_embedding <=> cast(? as vector)) < ?
        order by q.question_embedding <=> cast(? as vector), q.post_id desc
        limit ?
        """;
    return jdbc.query(sql,
        (rs, rowNum) -> new SimilarQuestionView(rs.getLong("question_id"), rs.getString("title")),
        vector, vector, MAX_DISTANCE, vector, limit);
  }

  private String toVectorLiteral(List<Double> embedding) {
    if (embedding == null || embedding.size() != 768) {
      throw new IllegalArgumentException("embedding must be 768 dimensions");
    }
    StringBuilder sb = new StringBuilder("[");
    for (int i = 0; i < embedding.size(); i++) {
      if (i > 0) {
        sb.append(',');
      }
      Double v = embedding.get(i);
      if (v == null || v.isNaN() || v.isInfinite()) {
        throw new IllegalArgumentException("embedding contains invalid value");
      }
      sb.append(v);
    }
    return sb.append(']').toString();
  }
}
