package ai.devpath.community.search;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.mapping.Property;
import java.io.IOException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/** 실제 ES(localhost:9200, docker/elasticsearch 이미지)를 대상으로 인덱스 부트스트랩을 검증한다. */
@SpringBootTest
@ActiveProfiles("test")
class PostIndexBootstrapTest {

  @Autowired PostIndexBootstrap bootstrap;
  @Autowired SearchIndexProperties properties;
  @Autowired ElasticsearchClient client;

  @Test
  void ensureIndexCreatesIndexWithNoriMapping() throws IOException {
    bootstrap.ensureIndex();

    String indexName = properties.getIndexName();
    boolean exists = client.indices().exists(e -> e.index(indexName)).value();
    assertTrue(exists, "인덱스가 생성돼야 한다: " + indexName);

    var mappingResponse = client.indices().getMapping(g -> g.index(indexName));
    var mappingProperties = mappingResponse.get(indexName).mappings().properties();

    Property title = mappingProperties.get("title");
    assertTrue(title.isText(), "title 은 text 타입이어야 한다");
    assertEquals("nori_analyzer", title.text().analyzer());

    Property bodyMd = mappingProperties.get("bodyMd");
    assertTrue(bodyMd.isText(), "bodyMd 는 text 타입이어야 한다");
    assertEquals("nori_analyzer", bodyMd.text().analyzer());
  }

  @Test
  void ensureIndexIsIdempotent() {
    assertDoesNotThrow(() -> {
      bootstrap.ensureIndex();
      bootstrap.ensureIndex();
    });
  }
}
