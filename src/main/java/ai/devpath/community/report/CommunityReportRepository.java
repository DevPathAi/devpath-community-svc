package ai.devpath.community.report;

import org.springframework.data.jpa.repository.JpaRepository;

public interface CommunityReportRepository extends JpaRepository<CommunityReport, Long> {
  boolean existsByReporterIdAndTargetTypeAndTargetId(Long reporterId, String targetType, Long targetId);

  /** 같은 대상의 총 신고 수. status 와 무관하다 — "이 글이 그동안 몇 번 신고됐는가"를 본다. */
  long countByTargetTypeAndTargetId(String targetType, Long targetId);

  /**
   * 관리자 목록 한 페이지. {@code status} 가 null 이면 전체를 본다.
   * {@code createdAt} 동률에서 순서가 흔들리지 않도록 id 를 2차 정렬로 둔다.
   */
  @org.springframework.data.jpa.repository.Query(
    "select r from CommunityReport r where (:status is null or r.status = :status) "
    + "order by r.createdAt desc, r.id desc")
  java.util.List<CommunityReport> findPage(String status,
      org.springframework.data.domain.Pageable pageable);

  @org.springframework.data.jpa.repository.Query(
    "select count(r) from CommunityReport r where (:status is null or r.status = :status)")
  long countFiltered(String status);
}
