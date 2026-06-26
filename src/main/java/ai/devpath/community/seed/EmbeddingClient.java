package ai.devpath.community.seed;

import java.time.Duration;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/** ai-svc /ai/embed 호출(learning RestAiPathClient 패턴). 단일 text → 768 double. */
@Component
public class EmbeddingClient {

  private final RestClient restClient;

  public EmbeddingClient(
      @Value("${devpath.ai-svc.base-url:http://localhost:8081}") String baseUrl,
      @Value("${devpath.ai-svc.timeout:PT5S}") Duration timeout) {
    var rf = new SimpleClientHttpRequestFactory();
    rf.setConnectTimeout(timeout);
    rf.setReadTimeout(timeout);
    this.restClient = RestClient.builder().baseUrl(baseUrl).requestFactory(rf).build();
  }

  /** ai-svc /ai/embed 호출. 실패 시 EmbeddingUnavailableException(상위가 graceful 처리). */
  public List<Double> embed(String text) {
    try {
      EmbedResponse res = restClient.post()
          .uri("/ai/embed")
          .body(new EmbedRequest(List.of(text)))
          .retrieve()
          .body(EmbedResponse.class);
      if (res == null || res.embeddings() == null || res.embeddings().isEmpty()
          || res.embeddings().get(0) == null || res.embeddings().get(0).size() != 768) {
        throw new EmbeddingUnavailableException("ai-svc embed response invalid");
      }
      return res.embeddings().get(0);
    } catch (RestClientException e) {
      throw new EmbeddingUnavailableException("ai-svc embed failed", e);
    }
  }

  record EmbedRequest(List<String> texts) {}
  record EmbedResponse(List<List<Double>> embeddings) {}
}
