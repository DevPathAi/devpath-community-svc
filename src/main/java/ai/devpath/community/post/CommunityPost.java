package ai.devpath.community.post;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "community_posts")
public class CommunityPost {
  @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;
  @Column(name = "author_id", nullable = false) private Long authorId;
  @Column(name = "board_type", nullable = false) private String boardType;
  @Column(nullable = false) private String title;
  @Column(name = "body_md", nullable = false) private String bodyMd;
  @Column(name = "body_html") private String bodyHtml;
  @Column(nullable = false) private String status = "PUBLISHED";
  @Column(name = "view_count", nullable = false) private int viewCount = 0;
  @Column(name = "upvote_count", nullable = false) private int upvoteCount = 0;
  @Column(name = "downvote_count", nullable = false) private int downvoteCount = 0;
  @Column(name = "created_at", insertable = false, updatable = false) private Instant createdAt;
  @Column(name = "updated_at", insertable = false, updatable = false) private Instant updatedAt;

  public Long getId() { return id; }
  public Long getAuthorId() { return authorId; }
  public void setAuthorId(Long authorId) { this.authorId = authorId; }
  public String getBoardType() { return boardType; }
  public void setBoardType(String boardType) { this.boardType = boardType; }
  public String getTitle() { return title; }
  public void setTitle(String title) { this.title = title; }
  public String getBodyMd() { return bodyMd; }
  public void setBodyMd(String bodyMd) { this.bodyMd = bodyMd; }
  public String getBodyHtml() { return bodyHtml; }
  public void setBodyHtml(String bodyHtml) { this.bodyHtml = bodyHtml; }
  public String getStatus() { return status; }
  public void setStatus(String status) { this.status = status; }
  public int getViewCount() { return viewCount; }
  public void setViewCount(int viewCount) { this.viewCount = viewCount; }
  public int getUpvoteCount() { return upvoteCount; }
  public void setUpvoteCount(int upvoteCount) { this.upvoteCount = upvoteCount; }
  public int getDownvoteCount() { return downvoteCount; }
  public void setDownvoteCount(int downvoteCount) { this.downvoteCount = downvoteCount; }
  public Instant getCreatedAt() { return createdAt; }
  public Instant getUpdatedAt() { return updatedAt; }
}
