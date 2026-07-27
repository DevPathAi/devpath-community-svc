package ai.devpath.community.badge;

import jakarta.persistence.*;

@Entity
@Table(name = "badges")
public class Badge {
  @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;
  @Column(nullable = false, unique = true, length = 32) private String code;
  @Column(nullable = false, length = 64) private String name;
  @Column(nullable = false, length = 8) private String tier;
  @Column(nullable = false, length = 255) private String criteria;

  public Badge() {}
  public Long getId() { return id; }
  public String getCode() { return code; }
  public String getName() { return name; }
  public String getTier() { return tier; }
  public String getCriteria() { return criteria; }
}
