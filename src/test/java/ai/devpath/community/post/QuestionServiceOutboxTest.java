package ai.devpath.community.post;

import static org.junit.jupiter.api.Assertions.assertEquals;

import ai.devpath.community.outbox.OutboxEntry;
import ai.devpath.community.outbox.OutboxRepository;
import ai.devpath.community.post.dto.CreateQuestionRequest;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * 글 생성 시 검색 색인 갱신용 Outbox 엔트리(community.post.changed)가 같은 트랜잭션에 적재되는지 검증한다.
 *
 * <p>스케줄러({@code OutboxRelayScheduler})는 {@code @Profile("!test")}라 이 테스트에서는 Kafka 로 발행되지
 * 않는다 — outbox 테이블에 엔트리가 쌓이는 것까지만 검증한다.
 */
@SpringBootTest
@ActiveProfiles("test")
class QuestionServiceOutboxTest {

  @Autowired QuestionService questions;
  @Autowired OutboxRepository outbox;
  @Autowired JsonMapper jsonMapper;

  @Test
  void createEnqueuesPostChangedIndexEventWithDeletedFalse() throws Exception {
    var view = questions.create(5151L,
        new CreateQuestionRequest("색인이벤트질문", "본문", List.of()));

    List<OutboxEntry> entries = outbox.findAll().stream()
        .filter(e -> "community.post.changed".equals(e.getEventType()))
        .filter(e -> e.getAggregateId().equals(String.valueOf(view.id())))
        .toList();
    assertEquals(1, entries.size(), "community.post.changed 엔트리가 정확히 1건 쌓여야 한다");

    OutboxEntry entry = entries.get(0);
    assertEquals("community_post", entry.getAggregateType());
    JsonNode payload = jsonMapper.readTree(entry.getPayload());
    assertEquals(view.id(), payload.get("postId").asLong());
    assertEquals(false, payload.get("deleted").asBoolean());
  }
}
