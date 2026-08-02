package ai.devpath.community.report;

import org.springframework.data.jpa.repository.JpaRepository;

public interface CommunityReportRepository extends JpaRepository<CommunityReport, Long> {
  boolean existsByReporterIdAndTargetTypeAndTargetId(Long reporterId, String targetType, Long targetId);

  /** 같은 대상의 총 신고 수. status 와 무관하다 — "이 글이 그동안 몇 번 신고됐는가"를 본다. */
  long countByTargetTypeAndTargetId(String targetType, Long targetId);
}
