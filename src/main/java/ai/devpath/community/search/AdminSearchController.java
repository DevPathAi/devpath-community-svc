package ai.devpath.community.search;

import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** /admin/community/** — SecurityConfig 의 {@code /admin/**} hasRole("ADMIN") 으로 보호됨. */
@RestController
@RequestMapping("/admin/community")
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
