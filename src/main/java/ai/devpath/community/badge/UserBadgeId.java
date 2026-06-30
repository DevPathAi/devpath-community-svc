package ai.devpath.community.badge;

import java.io.Serializable;
import java.util.Objects;

public class UserBadgeId implements Serializable {
  private Long userId;
  private Long badgeId;
  public UserBadgeId() {}
  public UserBadgeId(Long userId, Long badgeId) { this.userId = userId; this.badgeId = badgeId; }
  @Override public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof UserBadgeId that)) return false;
    return Objects.equals(userId, that.userId) && Objects.equals(badgeId, that.badgeId);
  }
  @Override public int hashCode() { return Objects.hash(userId, badgeId); }
}
