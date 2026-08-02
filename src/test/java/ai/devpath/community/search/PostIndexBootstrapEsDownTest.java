package ai.devpath.community.search;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

/**
 * Global Constraints: ES 가 죽어 있어도 애플리케이션 기동은 정상이어야 한다(검색만 불가).
 *
 * <p>도달 불가능한 ES URI를 주입해 실제로 컨텍스트가 정상 로딩되는지 검증한다. {@code
 * PostIndexBootstrap.run()} 이 예외를 삼키지 못하면(예: catch 절이 지나치게 좁아지면)
 * {@code ApplicationRunner} 실행 실패로 컨텍스트 기동 자체가 실패하며 이 테스트가 실패한다.
 */
@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(properties = {
    // 이 레포 기존 테스트 관례(application-test.yml ai-svc 목)와 동일하게 포트 9(미사용)로
    // 즉시 connection-refused 를 유도해 테스트를 빠르게 유지한다.
    "spring.elasticsearch.uris=http://127.0.0.1:9",
    "spring.elasticsearch.connection-timeout=1s",
    "spring.elasticsearch.socket-timeout=1s"
})
class PostIndexBootstrapEsDownTest {

  @Test
  void contextLoadsEvenWhenElasticsearchUnreachable() {
    // 컨텍스트가 여기까지 로딩됐다는 사실 자체가 검증이다.
    // PostIndexBootstrap.run() 이 예외를 삼키지 못하면 @SpringBootTest 준비 단계에서
    // ApplicationContextException 이 발생해 이 테스트 클래스 자체가 실패로 보고된다.
  }
}
