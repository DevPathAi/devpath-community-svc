package ai.devpath.community.post;

import ai.devpath.community.outbox.OutboxEntry;
import ai.devpath.community.outbox.OutboxRepository;
import java.time.Instant;
import java.util.Map;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

/**
 * 검색 색인 갱신(community.post.changed) Outbox 발행 공용 컴포넌트.
 *
 * <p>{@link QuestionService}(QNA 생성)와 {@link PostService}(FREE/FEEDBACK 생성) 양쪽에서 완전히 동일한
 * aggregateType·eventType·payload 형식으로 호출되던 것을 추출했다 — {@code CollusionDetector}·
 * {@code BadgeService} 처럼 서로 다른 이벤트를 각자 조립하는 경우와 달리, 이 이벤트는 두 호출자에서 문자
 * 그대로 동일한 로직이라 중복을 피하기 위해 별도 컴포넌트로 뺐다.
 *
 * <p>{@code CollusionDetector}·{@code BadgeService}와 동일하게 자체 {@code @Transactional}로 호출자의
 * 트랜잭션에 합류한다(REQUIRED 전파).
 */
@Component
public class PostIndexEventPublisher {

  private final OutboxRepository outbox;
  private final JsonMapper jsonMapper;

  public PostIndexEventPublisher(OutboxRepository outbox, JsonMapper jsonMapper) {
    this.outbox = outbox;
    this.jsonMapper = jsonMapper;
  }

  /** 검색 색인 갱신 이벤트를 Outbox 에 적재한다. 릴레이가 2초 주기로 Kafka(community.post.changed)로 발행한다. */
  @Transactional
  public void publish(long postId, boolean deleted) {
    OutboxEntry entry = new OutboxEntry();
    entry.setAggregateType("community_post");
    entry.setAggregateId(String.valueOf(postId));
    entry.setEventType("community.post.changed");
    entry.setPayload(jsonMapper.writeValueAsString(Map.of("postId", postId, "deleted", deleted)));
    entry.setCreatedAt(Instant.now());
    outbox.save(entry);
  }
}
