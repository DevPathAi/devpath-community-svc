package ai.devpath.community.report;

import jakarta.persistence.*;
import java.time.Instant;

/**
 * 커뮤니티 신고. 대상이 글·답변·댓글 3종이라 {@code (targetType, targetId)} 다형 참조를 쓴다
 * — FK 를 걸 수 없으므로 대상이 지워져도 신고 기록은 남는다.
 */
@Entity
@Table(name = "community_reports")
public class CommunityReport {
  @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;
  @Column(name = "reporter_id", nullable = false) private Long reporterId;
  @Column(name = "target_type", nullable = false) private String targetType;
  @Column(name = "target_id", nullable = false) private Long targetId;
  @Column(nullable = false) private String category;
  private String reason;
  @Column(nullable = false) private String status = "OPEN";
  @Column(name = "reviewed_by") private Long reviewedBy;
  @Column(name = "reviewed_at") private Instant reviewedAt;
  @Column(name = "created_at", insertable = false, updatable = false) private Instant createdAt;

  public Long getId() { return id; }
  public Long getReporterId() { return reporterId; }
  public void setReporterId(Long reporterId) { this.reporterId = reporterId; }
  public String getTargetType() { return targetType; }
  public void setTargetType(String targetType) { this.targetType = targetType; }
  public Long getTargetId() { return targetId; }
  public void setTargetId(Long targetId) { this.targetId = targetId; }
  public String getCategory() { return category; }
  public void setCategory(String category) { this.category = category; }
  public String getReason() { return reason; }
  public void setReason(String reason) { this.reason = reason; }
  public String getStatus() { return status; }
  public void setStatus(String status) { this.status = status; }
  public Long getReviewedBy() { return reviewedBy; }
  public void setReviewedBy(Long reviewedBy) { this.reviewedBy = reviewedBy; }
  public Instant getReviewedAt() { return reviewedAt; }
  public void setReviewedAt(Instant reviewedAt) { this.reviewedAt = reviewedAt; }
  public Instant getCreatedAt() { return createdAt; }
}
