package ai.devpath.community.post;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * 콘텐츠 수정 이력. 현재 행이 최신 본문을 들고 이 테이블이 과거를 쌓는다.
 * 최초 작성분은 여기 없고, N 번 수정하면 N 개가 생긴다.
 *
 * <p>community_reports 와 같은 다형 대상 패턴이다 — target_type 은 POST/ANSWER/COMMENT 이고
 * 질문은 board_type='QNA' 인 게시글이므로 POST 로 기록된다.
 */
@Entity
@Table(name = "community_content_revisions")
public class ContentRevision {
  @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;
  @Column(name = "target_type", nullable = false) private String targetType;
  @Column(name = "target_id", nullable = false) private Long targetId;
  private String title;
  @Column(name = "body_md", nullable = false) private String bodyMd;
  @Column(name = "body_html") private String bodyHtml;
  @Column(name = "edited_by", nullable = false) private Long editedBy;
  @Column(name = "created_at", insertable = false, updatable = false) private Instant createdAt;

  public Long getId() { return id; }
  public String getTargetType() { return targetType; }
  public void setTargetType(String targetType) { this.targetType = targetType; }
  public Long getTargetId() { return targetId; }
  public void setTargetId(Long targetId) { this.targetId = targetId; }
  public String getTitle() { return title; }
  public void setTitle(String title) { this.title = title; }
  public String getBodyMd() { return bodyMd; }
  public void setBodyMd(String bodyMd) { this.bodyMd = bodyMd; }
  public String getBodyHtml() { return bodyHtml; }
  public void setBodyHtml(String bodyHtml) { this.bodyHtml = bodyHtml; }
  public Long getEditedBy() { return editedBy; }
  public void setEditedBy(Long editedBy) { this.editedBy = editedBy; }
  public Instant getCreatedAt() { return createdAt; }
}
