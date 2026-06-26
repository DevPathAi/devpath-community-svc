package ai.devpath.community.post;

import jakarta.persistence.*;

@Entity
@Table(name = "community_votes")
public class CommunityVote {
  @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;
  @Column(name = "user_id", nullable = false) private Long userId;
  @Column(name = "target_type", nullable = false) private String targetType;
  @Column(name = "target_id", nullable = false) private Long targetId;
  @Column(nullable = false) private short value;

  public Long getId() { return id; }
  public Long getUserId() { return userId; }
  public void setUserId(Long userId) { this.userId = userId; }
  public String getTargetType() { return targetType; }
  public void setTargetType(String targetType) { this.targetType = targetType; }
  public Long getTargetId() { return targetId; }
  public void setTargetId(Long targetId) { this.targetId = targetId; }
  public short getValue() { return value; }
  public void setValue(short value) { this.value = value; }
}
