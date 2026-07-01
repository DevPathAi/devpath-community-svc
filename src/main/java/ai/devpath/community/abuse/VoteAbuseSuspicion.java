package ai.devpath.community.abuse;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "vote_abuse_suspicions")
public class VoteAbuseSuspicion {
  @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;
  @Column(name = "actor_id", nullable = false) private Long actorId;
  @Column(name = "target_user_id", nullable = false) private Long targetUserId;
  @Column(nullable = false, length = 32) private String reason;
  @Column(name = "evidence_count", nullable = false) private int evidenceCount;
  @Column(name = "detected_at", insertable = false, updatable = false) private Instant detectedAt;

  public VoteAbuseSuspicion() {}
  public VoteAbuseSuspicion(Long actorId, Long targetUserId, String reason, int evidenceCount) {
    this.actorId = actorId; this.targetUserId = targetUserId;
    this.reason = reason; this.evidenceCount = evidenceCount;
  }
  public Long getId() { return id; }
  public Long getActorId() { return actorId; }
  public Long getTargetUserId() { return targetUserId; }
  public String getReason() { return reason; }
  public int getEvidenceCount() { return evidenceCount; }
  public Instant getDetectedAt() { return detectedAt; }
}
