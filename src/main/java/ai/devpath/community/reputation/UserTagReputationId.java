package ai.devpath.community.reputation;

import java.io.Serializable;
import java.util.Objects;

public class UserTagReputationId implements Serializable {
  private Long userId;
  private Long tagId;
  public UserTagReputationId() {}
  public UserTagReputationId(Long userId, Long tagId) { this.userId = userId; this.tagId = tagId; }
  @Override public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof UserTagReputationId that)) return false;
    return Objects.equals(userId, that.userId) && Objects.equals(tagId, that.tagId);
  }
  @Override public int hashCode() { return Objects.hash(userId, tagId); }
}
