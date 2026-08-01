package ai.devpath.community.search;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** 검색 인덱스 설정. 테스트는 별도 인덱스명을 주입해 운영 인덱스와 격리한다. */
@ConfigurationProperties(prefix = "devpath.search")
public class SearchIndexProperties {
  private String indexName = "community_posts";

  public String getIndexName() {
    return indexName;
  }

  public void setIndexName(String indexName) {
    this.indexName = indexName;
  }
}
