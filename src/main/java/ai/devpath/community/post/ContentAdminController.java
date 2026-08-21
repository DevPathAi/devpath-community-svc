package ai.devpath.community.post;

import ai.devpath.community.post.dto.RevisionView;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 관리자 콘텐츠 내리기. 권한은 경로가 가른다 —
 * SecurityConfig 가 {@code /community/admin/**} 에 hasRole("ADMIN") 을 이미 걸어 두었으므로
 * 메서드 안에서 role 을 파싱하지 않는다.
 *
 * <p>{@code AdminReportController} 는 {@code /community/admin/reports} 를 맡는다. 경로가 갈리므로
 * 매핑 충돌이 없다.
 */
@RestController
@RequestMapping("/community/admin")
public class ContentAdminController {

  private final ContentAdminService service;

  public ContentAdminController(ContentAdminService service) {
    this.service = service;
  }

  @DeleteMapping("/posts/{id}")
  public ResponseEntity<Void> hidePost(@PathVariable long id) {
    service.hidePost(id);
    return ResponseEntity.noContent().build();
  }

  @DeleteMapping("/answers/{id}")
  public ResponseEntity<Void> hideAnswer(@PathVariable long id) {
    service.hideAnswer(id);
    return ResponseEntity.noContent().build();
  }

  @DeleteMapping("/comments/{id}")
  public ResponseEntity<Void> hideComment(@PathVariable long id) {
    service.hideComment(id);
    return ResponseEntity.noContent().build();
  }

  @GetMapping("/revisions")
  public ResponseEntity<List<RevisionView>> revisions(
      @RequestParam String targetType, @RequestParam long targetId) {
    return ResponseEntity.ok(service.revisionsOf(targetType, targetId));
  }
}
