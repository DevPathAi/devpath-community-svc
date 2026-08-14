package ai.devpath.community.post;

import static org.junit.jupiter.api.Assertions.assertEquals;

import ai.devpath.community.outbox.OutboxEntry;
import ai.devpath.community.outbox.OutboxRepository;
import ai.devpath.community.post.dto.CreatePostRequest;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * 일반글(FREE/FEEDBACK) 생성 시에도 검색 색인 갱신용 Outbox 엔트리(community.post.changed)가
 * 같은 트랜잭션에 적재되는지 검증한다. QuestionServiceOutboxTest 와 동일한 방식.
 *
 * <p>스케줄러는 {@code @Profile("!test")}라 이 테스트에서는 Kafka 로 발행되지 않는다 — outbox 테이블에
 * 엔트리가 쌓이는 것까지만 검증한다.
 */
@SpringBootTest
@ActiveProfiles("test")
class PostServiceOutboxTest {

  @Autowired PostService posts;
  @Autowired OutboxRepository outbox;
  @Autowired JsonMapper jsonMapper;

  @Test
  void createPostEnqueuesPostChangedIndexEventWithDeletedFalse() throws Exception {
    var view = posts.createPost(6161L,
        new CreatePostRequest("FREE", "색인이벤트자유글", "본문", List.of()));

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

  @Test
  void createFeedbackPostAlsoEnqueuesIndexEvent() throws Exception {
    var view = posts.createPost(6162L,
        new CreatePostRequest("FEEDBACK", "색인이벤트피드백글", "본문", List.of()));

    List<OutboxEntry> entries = outbox.findAll().stream()
        .filter(e -> "community.post.changed".equals(e.getEventType()))
        .filter(e -> e.getAggregateId().equals(String.valueOf(view.id())))
        .toList();
    assertEquals(1, entries.size(), "FEEDBACK 게시판 글도 community.post.changed 엔트리가 1건 쌓여야 한다");
  }
}
