package ai.devpath.community.reputation;

import jakarta.persistence.*;

@Entity
@Table(name = "user_tag_reputation")
@IdClass(UserTagReputationId.class)
public class UserTagReputation {
  @Id @Column(name = "user_id") private Long userId;
  @Id @Column(name = "tag_id") private Long tagId;
  @Column(nullable = false) private int score = 0;

  public UserTagReputation() {}
  public UserTagReputation(Long userId, Long tagId) { this.userId = userId; this.tagId = tagId; }
  public Long getUserId() { return userId; }
  public Long getTagId() { return tagId; }
  public int getScore() { return score; }
  public void add(int delta) { this.score += delta; }
}
