package ai.devpath.community.badge;

import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BadgeQueryService {
  private final UserBadgeRepository userBadges;
  private final BadgeRepository badges;

  public BadgeQueryService(UserBadgeRepository userBadges, BadgeRepository badges) {
    this.userBadges = userBadges; this.badges = badges;
  }

  @Transactional(readOnly = true)
  public List<BadgeView> badgesOf(long userId) {
    return userBadges.findByUserIdOrderByAwardedAtDesc(userId).stream()
        .map(ub -> {
          Badge b = badges.findById(ub.getBadgeId()).orElseThrow();
          return new BadgeView(b.getCode(), b.getName(), b.getTier(), ub.getAwardedAt());
        })
        .toList();
  }
}
