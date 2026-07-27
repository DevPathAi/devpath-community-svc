package ai.devpath.community.badge;

import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/community/users")
public class BadgeController {
  private final BadgeQueryService badgeQuery;
  public BadgeController(BadgeQueryService badgeQuery) { this.badgeQuery = badgeQuery; }

  @GetMapping("/{userId}/badges")
  public List<BadgeView> badges(@PathVariable long userId) {
    return badgeQuery.badgesOf(userId);
  }
}
