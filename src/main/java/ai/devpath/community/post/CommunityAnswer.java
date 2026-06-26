package ai.devpath.community.post;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "community_answers")
public class CommunityAnswer {
  @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;
  @Column(name = "question_id", nullable = false) private Long questionId;
  @Column(name = "author_id") private Long authorId;
  @Column(name = "body_md", nullable = false) private String bodyMd;
  @Column(name = "body_html") private String bodyHtml;
  @Column(name = "is_ai_generated", nullable = false) private boolean aiGenerated = false;
  @Column(name = "is_accepted", nullable = false) private boolean accepted = false;
  @Column(name = "upvote_count", nullable = false) private int upvoteCount = 0;
  @Column(name = "created_at", insertable = false, updatable = false) private Instant createdAt;
  @Column(name = "updated_at", insertable = false, updatable = false) private Instant updatedAt;

  public Long getId() { return id; }
  public Long getQuestionId() { return questionId; }
  public void setQuestionId(Long questionId) { this.questionId = questionId; }
  public Long getAuthorId() { return authorId; }
  public void setAuthorId(Long authorId) { this.authorId = authorId; }
  public String getBodyMd() { return bodyMd; }
  public void setBodyMd(String bodyMd) { this.bodyMd = bodyMd; }
  public String getBodyHtml() { return bodyHtml; }
  public void setBodyHtml(String bodyHtml) { this.bodyHtml = bodyHtml; }
  public boolean isAiGenerated() { return aiGenerated; }
  public void setAiGenerated(boolean aiGenerated) { this.aiGenerated = aiGenerated; }
  public boolean isAccepted() { return accepted; }
  public void setAccepted(boolean accepted) { this.accepted = accepted; }
  public int getUpvoteCount() { return upvoteCount; }
  public void setUpvoteCount(int upvoteCount) { this.upvoteCount = upvoteCount; }
  public Instant getCreatedAt() { return createdAt; }
  public Instant getUpdatedAt() { return updatedAt; }
}
