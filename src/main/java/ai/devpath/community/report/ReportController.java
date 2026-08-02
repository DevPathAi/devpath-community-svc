package ai.devpath.community.report;

import ai.devpath.community.report.dto.ReportCreatedView;
import ai.devpath.community.report.dto.ReportRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 사용자 신고 접수. 관리자 조회·판정은 {@link AdminReportController}. */
@RestController
@RequestMapping("/community")
public class ReportController {

  private final ReportService service;

  public ReportController(ReportService service) {
    this.service = service;
  }

  @PostMapping("/reports")
  public ResponseEntity<ReportCreatedView> report(@AuthenticationPrincipal Jwt jwt,
      @RequestBody ReportRequest req) {
    long reporterId = Long.parseLong(jwt.getSubject());
    CommunityReport saved = service.create(
        reporterId, req.targetType(), req.targetId(), req.category(), req.reason());
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(new ReportCreatedView(saved.getId() == null ? 0L : saved.getId(), saved.getStatus()));
  }
}
