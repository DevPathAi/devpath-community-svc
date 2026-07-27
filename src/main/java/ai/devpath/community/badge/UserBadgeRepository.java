package ai.devpath.community.badge;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserBadgeRepository extends JpaRepository<UserBadge, UserBadgeId> {
  boolean existsByUserIdAndBadgeId(Long userId, Long badgeId);
  List<UserBadge> findByUserIdOrderByAwardedAtDesc(Long userId);
}
