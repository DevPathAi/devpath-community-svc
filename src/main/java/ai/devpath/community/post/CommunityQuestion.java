package ai.devpath.community.post;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "community_questions")
public class CommunityQuestion {
  @Id @Column(name = "post_id") private Long postId;
  @Column(name = "is_solved", nullable = false) private boolean solved = false;
  @Column(name = "accepted_answer_id") private Long acceptedAnswerId;
  @JdbcTypeCode(SqlTypes.JSON) @Column(name = "learning_context", nullable = false) private String learningContext = "{}";

  public Long getPostId() { return postId; }
  public void setPostId(Long postId) { this.postId = postId; }
  public boolean isSolved() { return solved; }
  public void setSolved(boolean solved) { this.solved = solved; }
  public Long getAcceptedAnswerId() { return acceptedAnswerId; }
  public void setAcceptedAnswerId(Long acceptedAnswerId) { this.acceptedAnswerId = acceptedAnswerId; }
  public String getLearningContext() { return learningContext; }
  public void setLearningContext(String learningContext) { this.learningContext = learningContext; }
}
