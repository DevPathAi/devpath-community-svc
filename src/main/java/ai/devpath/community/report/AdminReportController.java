package ai.devpath.community.report;

import ai.devpath.community.report.dto.AdminReportResponse;
import ai.devpath.community.report.dto.ReportCreatedView;
import ai.devpath.community.report.dto.ResolveRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 신고 운영용 관리자 API. SecurityConfig 의 {@code /community/admin/**} hasRole("ADMIN") 으로 보호된다.
 *
 * <p>경로가 {@code /admin/reports} 가 아니라 <b>{@code /community/admin/reports}</b> 인 이유:
 * 게이트웨이의 {@code platform-auth} 라우트가 {@code /admin/**} 를 선점해 platform-svc(8081)로
 * 보낸다. 전자로 두면 이 서비스에 도달하지 못하고 404 가 된다.
 */
@RestController
@RequestMapping("/community/admin")
public class AdminReportController {

  private final ReportService service;

  public AdminReportController(ReportService service) {
    this.service = service;
  }

  @GetMapping("/reports")
  public ResponseEntity<AdminReportResponse> list(
      @RequestParam(required = false) String status,
      @RequestParam(required = false, defaultValue = "0") int page,
      @RequestParam(required = false, defaultValue = "20") int size) {
    return ResponseEntity.ok(service.list(status, page, size));
  }

  @PostMapping("/reports/{id}/resolve")
  public ResponseEntity<ReportCreatedView> resolve(@AuthenticationPrincipal Jwt jwt,
      @PathVariable long id, @RequestBody ResolveRequest req) {
    long reviewerId = Long.parseLong(jwt.getSubject());
    CommunityReport r = service.resolve(id, reviewerId, req.action());
    return ResponseEntity.ok(
        new ReportCreatedView(r.getId() == null ? id : r.getId(), r.getStatus()));
  }
}
