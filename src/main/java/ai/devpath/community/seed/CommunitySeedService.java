package ai.devpath.community.seed;

import ai.devpath.community.post.CommunityAnswer;
import ai.devpath.community.post.CommunityAnswerRepository;
import ai.devpath.shared.event.CommunitySeedReadyEvent;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CommunitySeedService {

  private static final Logger log = LoggerFactory.getLogger(CommunitySeedService.class);

  private final CommunityAnswerRepository answers;
  private final CommunityAiAnswerRepository aiAnswers;
  private final JdbcTemplate jdbc;

  public CommunitySeedService(CommunityAnswerRepository answers,
      CommunityAiAnswerRepository aiAnswers, JdbcTemplate jdbc) {
    this.answers = answers;
    this.aiAnswers = aiAnswers;
    this.jdbc = jdbc;
  }

  /** community.seed.ready 영속. 멱등: community_ai_answers PK(question_id) insert-if-absent(설계 D-6). */
  @Transactional
  public void apply(CommunitySeedReadyEvent event) {
    long qid = event.questionId();
    if (aiAnswers.existsById(qid)) {
      log.info("community.seed.ready 중복 — no-op questionId={}", qid);
      return;
    }
    CommunityAiAnswer meta = new CommunityAiAnswer();
    meta.setQuestionId(qid);
    meta.setModelUsed(event.provider());
    meta.setStatus(event.status());

    if ("DONE".equals(event.status()) && event.content() != null) {
      CommunityAnswer answer = new CommunityAnswer();
      answer.setQuestionId(qid);
      answer.setAuthorId(null); // AI 답변
      answer.setBodyMd(event.content());
      answer.setAiGenerated(true);
      answer = answers.save(answer);
      meta.setAnswerId(answer.getId());
      meta.setContent(event.content());
    } else {
      meta.setErrorCode(event.errorCode());
    }
    // 임베딩은 DONE/FAILED 무관 best-effort 동봉 가능(설계 D-4) — 있으면 항상 저장.
    updateEmbedding(qid, event.questionEmbedding());
    aiAnswers.save(meta);
  }

  private void updateEmbedding(long questionId, List<Double> embedding) {
    if (embedding == null) {
      return;
    }
    if (embedding.size() != 768) {
      log.warn("질문 임베딩 차원 불일치({}) — 스킵 questionId={}", embedding.size(), questionId);
      return;
    }
    String literal = toVectorLiteral(embedding);
    jdbc.update(
        "update community_questions set question_embedding = cast(? as vector) where post_id = ?",
        literal, questionId);
  }

  private String toVectorLiteral(List<Double> embedding) {
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
