package ai.devpath.community.reputation;

import jakarta.persistence.*;

@Entity
@Table(name = "user_reputation")
public class UserReputation {
  @Id @Column(name = "user_id") private Long userId;
  @Column(nullable = false) private int total = 0;

  public UserReputation() {}
  public UserReputation(Long userId) { this.userId = userId; }
  public Long getUserId() { return userId; }
  public int getTotal() { return total; }
  public void setTotal(int total) { this.total = total; }
  public void add(int delta) { this.total += delta; }
}
