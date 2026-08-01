package ai.devpath.community.search;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.json.JsonMapper;

/**
 * Kafka 없이 순수 Mockito 단위 테스트. PostIndexer 를 mock 으로 교체해 payload 파싱 -> 위임 경로만 검증한다
 * (Task 2 의 분리 결정 — 컨슈머는 ES 로직을 갖지 않는다).
 */
@ExtendWith(MockitoExtension.class)
class PostIndexConsumerTest {

  @Mock PostIndexer indexer;

  private final JsonMapper jsonMapper = JsonMapper.builder().build();

  @Test
  void deletedFalsePayloadCallsIndexNotDelete() {
    PostIndexConsumer consumer = new PostIndexConsumer(indexer, jsonMapper);

    consumer.onPostChanged("{\"postId\": 123, \"deleted\": false}");

    verify(indexer).index(123L);
    verify(indexer, never()).delete(123L);
  }

  @Test
  void deletedTruePayloadCallsDeleteNotIndex() {
    PostIndexConsumer consumer = new PostIndexConsumer(indexer, jsonMapper);

    consumer.onPostChanged("{\"postId\": 456, \"deleted\": true}");

    verify(indexer).delete(456L);
    verify(indexer, never()).index(456L);
  }

  @Test
  void malformedPayloadThrows() {
    PostIndexConsumer consumer = new PostIndexConsumer(indexer, jsonMapper);

    org.junit.jupiter.api.Assertions.assertThrows(RuntimeException.class,
        () -> consumer.onPostChanged("not-json"));
  }
}
