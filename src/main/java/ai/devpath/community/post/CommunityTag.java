package ai.devpath.community.post;

import jakarta.persistence.*;

@Entity
@Table(name = "community_tags")
public class CommunityTag {
  @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;
  @Column(nullable = false, unique = true) private String name;
  @Column(name = "post_count", nullable = false) private int postCount = 0;

  public Long getId() { return id; }
  public String getName() { return name; }
  public void setName(String name) { this.name = name; }
  public int getPostCount() { return postCount; }
  public void setPostCount(int postCount) { this.postCount = postCount; }
}
