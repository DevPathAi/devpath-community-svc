package ai.devpath.community.seed;

import jakarta.persistence.*;
import java.time.Instant;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "community_ai_answers")
public class CommunityAiAnswer {
  @Id @Column(name = "question_id") private Long questionId;
  @Column(name = "answer_id") private Long answerId;
  @Column(name = "model_used") private String modelUsed;
  @Column(name = "prompt_version") private String promptVersion;
  @Column private String content;
  @JdbcTypeCode(SqlTypes.JSON) @Column(name = "reference_links", nullable = false)
  private String referenceLinks = "[]";
  @Column(nullable = false) private String status;
  @Column(name = "error_code") private String errorCode;
  @Column(name = "generated_at", insertable = false, updatable = false) private Instant generatedAt;

  public Long getQuestionId() { return questionId; }
  public void setQuestionId(Long v) { this.questionId = v; }
  public Long getAnswerId() { return answerId; }
  public void setAnswerId(Long v) { this.answerId = v; }
  public String getModelUsed() { return modelUsed; }
  public void setModelUsed(String v) { this.modelUsed = v; }
  public String getPromptVersion() { return promptVersion; }
  public void setPromptVersion(String v) { this.promptVersion = v; }
  public String getContent() { return content; }
  public void setContent(String v) { this.content = v; }
  public String getReferenceLinks() { return referenceLinks; }
  public void setReferenceLinks(String v) { this.referenceLinks = v; }
  public String getStatus() { return status; }
  public void setStatus(String v) { this.status = v; }
  public String getErrorCode() { return errorCode; }
  public void setErrorCode(String v) { this.errorCode = v; }
  public Instant getGeneratedAt() { return generatedAt; }
}
