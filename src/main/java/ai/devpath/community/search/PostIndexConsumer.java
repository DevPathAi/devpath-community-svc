package ai.devpath.community.search;

import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * {@code community.post.changed} Kafka 어댑터. payload({"postId":123,"deleted":false}) 를 파싱해
 * {@link PostIndexer} 에 위임만 한다 — ES 색인 로직은 갖지 않는다(Task 2 의 분리 결정).
 *
 * <p>역직렬화 실패 시 예외를 삼키지 않고 그대로 던진다(다른 컨슈머의 poison-message skip 관행과 다름) —
 * 에러 핸들러가 재시도/스킵을 결정해야 하기 때문이다.
 */
@Component
public class PostIndexConsumer {

  public static final String TOPIC = "community.post.changed";

  private final PostIndexer indexer;
  private final JsonMapper jsonMapper;

  public PostIndexConsumer(PostIndexer indexer, JsonMapper jsonMapper) {
    this.indexer = indexer;
    this.jsonMapper = jsonMapper;
  }

  @KafkaListener(topics = TOPIC, groupId = "devpath-community")
  public void onPostChanged(String payload) {
    JsonNode node = jsonMapper.readTree(payload);
    long postId = node.get("postId").asLong();
    boolean deleted = node.get("deleted").asBoolean();
    if (deleted) {
      indexer.delete(postId);
    } else {
      indexer.index(postId);
    }
  }
}
