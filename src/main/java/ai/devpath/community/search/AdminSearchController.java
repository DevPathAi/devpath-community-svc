package ai.devpath.community.search;

import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 검색 인덱스 운영용 관리자 API. SecurityConfig 의 {@code /community/admin/**} hasRole("ADMIN") 으로 보호된다.
 *
 * <p>경로가 {@code /admin/community} 가 아니라 <b>{@code /community/admin}</b> 인 이유: 게이트웨이의
 * {@code platform-auth} 라우트가 {@code /admin/**} 를 선점해 platform-svc(8081)로 보낸다
 * (devpath-gateway {@code application.yml}). {@code /admin/community/reindex} 로 두면 이 서비스에
 * 도달하지 못하고 404 가 된다. {@code /community/**} 는 이미 이 서비스로 라우팅되므로 게이트웨이를
 * 건드리지 않고 라우트 선언 순서에도 의존하지 않는다.
 */
@RestController
@RequestMapping("/community/admin")
public class AdminSearchController {

  private final ReindexService reindexService;

  public AdminSearchController(ReindexService reindexService) {
    this.reindexService = reindexService;
  }

  /** 전체 재색인을 동기 실행하고 색인 건수를 반환한다. 실패는 예외로 전파돼 5xx 가 된다. */
  @PostMapping("/reindex")
  public ResponseEntity<Map<String, Integer>> reindex() {
    return ResponseEntity.ok(Map.of("indexed", reindexService.reindexAll()));
  }
}
