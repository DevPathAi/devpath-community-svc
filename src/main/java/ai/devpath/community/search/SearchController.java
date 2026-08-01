package ai.devpath.community.search;

import ai.devpath.community.search.dto.SearchResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 커뮤니티 게시글 검색 엔드포인트. {@link CommunityController}와 별도 클래스로 둔다(검색은 독립 관심사).
 * ES 장애 시 {@link PostSearchService}가 던지는 예외는 그대로 전파해 공용 {@code ApiExceptionHandler}가
 * 5xx envelope로 렌더하게 한다(빈 결과로 감추지 않는다).
 */
@RestController
@RequestMapping("/community")
public class SearchController {

  private final PostSearchService searchService;

  public SearchController(PostSearchService searchService) {
    this.searchService = searchService;
  }

  @GetMapping("/search")
  public ResponseEntity<SearchResponse> search(
      @RequestParam String q,
      @RequestParam(required = false) String board,
      @RequestParam(required = false) String tag,
      @RequestParam(required = false) Boolean solved,
      @RequestParam(required = false, defaultValue = "relevance") String sort,
      @RequestParam(required = false, defaultValue = "0") int page,
      @RequestParam(required = false, defaultValue = "20") int size) {
    if (q == null || q.isBlank()) {
      throw new IllegalArgumentException("검색어(q)는 필수입니다.");
    }
    return ResponseEntity.ok(searchService.search(q, board, tag, solved, sort, page, size));
  }
}
