package ai.devpath.community.search;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 커뮤니티 게시글 검색 인덱스 부트스트랩. 기동 시 인덱스가 없으면 nori 매핑으로 생성한다(멱등).
 *
 * <p>ES 가 죽어 있어도 애플리케이션 기동은 막지 않는다 — 검색만 불가할 뿐, 목록·글쓰기 등 나머지 기능은
 * ES 와 무관하게 정상 동작해야 한다(Global Constraints). 따라서 부트스트랩 실패는 {@code log.warn} 으로만
 * 남기고 삼킨다.
 */
@Component
@EnableConfigurationProperties(SearchIndexProperties.class)
public class PostIndexBootstrap implements ApplicationRunner {

  private static final Logger log = LoggerFactory.getLogger(PostIndexBootstrap.class);
  private static final String NORI_ANALYZER = "nori_analyzer";

  private final ElasticsearchClient client;
  private final SearchIndexProperties properties;

  public PostIndexBootstrap(ElasticsearchClient client, SearchIndexProperties properties) {
    this.client = client;
    this.properties = properties;
  }

  @Override
  public void run(ApplicationArguments args) {
    try {
      ensureIndex();
    } catch (Exception e) {
      log.warn("검색 인덱스 부트스트랩 실패 — 검색 기능만 불가, 나머지 기능은 정상 동작. index={}",
          properties.getIndexName(), e);
    }
  }

  /** 인덱스가 없으면 nori 매핑으로 생성한다. 이미 있으면 아무 것도 하지 않는다(멱등). */
  public void ensureIndex() throws IOException {
    String indexName = properties.getIndexName();
    boolean exists = client.indices().exists(e -> e.index(indexName)).value();
    if (exists) {
      return;
    }
    client.indices().create(c -> c
        .index(indexName)
        .settings(s -> s
            .analysis(a -> a
                .analyzer(NORI_ANALYZER, an -> an.nori(n -> n))))
        .mappings(m -> m
            .properties("title", p -> p.text(t -> t.analyzer(NORI_ANALYZER)))
            .properties("bodyMd", p -> p.text(t -> t.analyzer(NORI_ANALYZER)))
            .properties("tags", p -> p.keyword(k -> k))
            .properties("boardType", p -> p.keyword(k -> k))
            .properties("status", p -> p.keyword(k -> k))
            .properties("authorId", p -> p.keyword(k -> k))
            .properties("isSolved", p -> p.boolean_(b -> b))
            .properties("createdAt", p -> p.date(d -> d))));
  }
}
