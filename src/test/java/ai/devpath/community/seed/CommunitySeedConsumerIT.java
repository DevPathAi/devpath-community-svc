package ai.devpath.community.seed;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import ai.devpath.community.post.CommunityAnswerRepository;
import ai.devpath.community.post.QuestionService;
import ai.devpath.community.post.dto.CreateQuestionRequest;
import ai.devpath.shared.event.CommunitySeedReadyEvent;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.test.context.ActiveProfiles;
import tools.jackson.databind.json.JsonMapper;

@SpringBootTest
@ActiveProfiles("test")
@EmbeddedKafka(partitions = 1, topics = {"community.seed.ready"}, bootstrapServersProperty = "spring.kafka.bootstrap-servers")
class CommunitySeedConsumerIT {

  @Autowired KafkaTemplate<String, String> kafka;
  @Autowired JsonMapper jsonMapper;
  @Autowired QuestionService questions;
  @Autowired CommunityAnswerRepository answers;
  @Autowired CommunityAiAnswerRepository aiAnswers;
  @Autowired JdbcTemplate jdbc;

  private long newQuestion() {
    return questions.create(70L, new CreateQuestionRequest("seed q", "body", List.of())).id();
  }

  private static List<Double> emb768() {
    return java.util.stream.DoubleStream.generate(() -> 0.01).limit(768).boxed().toList();
  }

  @Test
  void consumesDoneAndPersistsAnswerMetaEmbedding() throws Exception {
    long qid = newQuestion();
    var ev = new CommunitySeedReadyEvent(UUID.randomUUID(), Instant.now(), qid,
        "DONE", "AI 초안 답변", "mock", emb768(), null);
    kafka.send("community.seed.ready", String.valueOf(qid), jsonMapper.writeValueAsString(ev));

    await().atMost(Duration.ofSeconds(20)).untilAsserted(() ->
        assertThat(aiAnswers.findById(qid)).isPresent());

    var ai = aiAnswers.findById(qid).orElseThrow();
    assertThat(ai.getStatus()).isEqualTo("DONE");
    assertThat(ai.getContent()).isEqualTo("AI 초안 답변");
    assertThat(ai.getAnswerId()).isNotNull();
    assertThat(answers.findByQuestionIdOrderByCreatedAtAsc(qid).stream()
        .anyMatch(a -> a.isAiGenerated() && "AI 초안 답변".equals(a.getBodyMd()))).isTrue();
    Integer cnt = jdbc.queryForObject(
        "select count(*) from community_questions where post_id = ? and question_embedding is not null",
        Integer.class, qid);
    assertThat(cnt).isEqualTo(1);
  }

  @Test
  void duplicateEventIsIdempotent() throws Exception {
    long qid = newQuestion();
    var ev = new CommunitySeedReadyEvent(UUID.randomUUID(), Instant.now(), qid,
        "DONE", "중복답변", "mock", emb768(), null);
    kafka.send("community.seed.ready", String.valueOf(qid), jsonMapper.writeValueAsString(ev));
    await().atMost(Duration.ofSeconds(20)).untilAsserted(() ->
        assertThat(aiAnswers.findById(qid)).isPresent());

    var ev2 = new CommunitySeedReadyEvent(UUID.randomUUID(), Instant.now(), qid,
        "DONE", "중복답변2", "mock", emb768(), null);
    kafka.send("community.seed.ready", String.valueOf(qid), jsonMapper.writeValueAsString(ev2));
    Thread.sleep(2000); // 두 번째 처리 시도 시간 확보

    assertThat(answers.findByQuestionIdOrderByCreatedAtAsc(qid).stream()
        .filter(a -> a.isAiGenerated()).count()).isEqualTo(1L);
    assertThat(aiAnswers.findById(qid).orElseThrow().getContent()).isEqualTo("중복답변");
  }

  @Test
  void failedStatusRecordedWithoutAnswer() throws Exception {
    long qid = newQuestion();
    var ev = new CommunitySeedReadyEvent(UUID.randomUUID(), Instant.now(), qid,
        "FAILED", null, "mock", null, "LLM_FAILED");
    kafka.send("community.seed.ready", String.valueOf(qid), jsonMapper.writeValueAsString(ev));

    await().atMost(Duration.ofSeconds(20)).untilAsserted(() ->
        assertThat(aiAnswers.findById(qid)).isPresent());

    var ai = aiAnswers.findById(qid).orElseThrow();
    assertThat(ai.getStatus()).isEqualTo("FAILED");
    assertThat(ai.getErrorCode()).isEqualTo("LLM_FAILED");
    assertThat(answers.findByQuestionIdOrderByCreatedAtAsc(qid).stream()
        .anyMatch(a -> a.isAiGenerated())).isFalse();
  }
}
