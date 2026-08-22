package ai.devpath.community.post;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "community_comments")
public class CommunityComment {
  @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;
  @Column(name = "post_id", nullable = false) private Long postId;
  @Column(name = "author_id", nullable = false) private Long authorId;
  @Column(name = "body_md", nullable = false) private String bodyMd;
  @Column(name = "body_html") private String bodyHtml;
  @Column(name = "upvote_count", nullable = false) private int upvoteCount = 0;
  @Column(name = "created_at", insertable = false, updatable = false) private Instant createdAt;
  @Column(nullable = false) private String status = ContentStatus.PUBLISHED;
  @Column(name = "updated_at", insertable = false, updatable = false) private Instant updatedAt;

  public Long getId() { return id; }
  public Long getPostId() { return postId; }
  public void setPostId(Long postId) { this.postId = postId; }
  public Long getAuthorId() { return authorId; }
  public void setAuthorId(Long authorId) { this.authorId = authorId; }
  public String getBodyMd() { return bodyMd; }
  public void setBodyMd(String bodyMd) { this.bodyMd = bodyMd; }
  public String getBodyHtml() { return bodyHtml; }
  public void setBodyHtml(String bodyHtml) { this.bodyHtml = bodyHtml; }
  public int getUpvoteCount() { return upvoteCount; }
  public void setUpvoteCount(int upvoteCount) { this.upvoteCount = upvoteCount; }
  public Instant getCreatedAt() { return createdAt; }
  public Instant getUpdatedAt() { return updatedAt; }
  public String getStatus() { return status; }
  public void setStatus(String status) { this.status = status; }
}
