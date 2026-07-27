package ai.devpath.community.badge;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "user_badges")
@IdClass(UserBadgeId.class)
public class UserBadge {
  @Id @Column(name = "user_id") private Long userId;
  @Id @Column(name = "badge_id") private Long badgeId;
  @Column(name = "awarded_at", insertable = false, updatable = false) private Instant awardedAt;

  public UserBadge() {}
  public UserBadge(Long userId, Long badgeId) { this.userId = userId; this.badgeId = badgeId; }
  public Long getUserId() { return userId; }
  public Long getBadgeId() { return badgeId; }
  public Instant getAwardedAt() { return awardedAt; }
}
