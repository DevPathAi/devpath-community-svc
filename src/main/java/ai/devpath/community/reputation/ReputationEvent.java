package ai.devpath.community.reputation;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "reputation_events")
public class ReputationEvent {
  @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;
  @Column(name = "user_id", nullable = false) private Long userId;
  @Column(name = "actor_id") private Long actorId;
  @Column(nullable = false) private int delta;
  @Column(nullable = false, length = 32) private String reason;
  @Column(name = "source_type", nullable = false, length = 16) private String sourceType;
  @Column(name = "source_id", nullable = false) private Long sourceId;
  @Column(name = "created_at", insertable = false, updatable = false) private Instant createdAt;

  public ReputationEvent() {}
  public ReputationEvent(Long userId, Long actorId, int delta, ReputationReason reason,
      String sourceType, Long sourceId) {
    this.userId = userId; this.actorId = actorId; this.delta = delta;
    this.reason = reason.name(); this.sourceType = sourceType; this.sourceId = sourceId;
  }
  public Long getId() { return id; }
  public Long getUserId() { return userId; }
  public Long getActorId() { return actorId; }
  public int getDelta() { return delta; }
  public String getReason() { return reason; }
  public String getSourceType() { return sourceType; }
  public Long getSourceId() { return sourceId; }
  public Instant getCreatedAt() { return createdAt; }
}
