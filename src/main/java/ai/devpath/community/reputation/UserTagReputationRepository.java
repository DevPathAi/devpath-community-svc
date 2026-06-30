package ai.devpath.community.reputation;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserTagReputationRepository extends JpaRepository<UserTagReputation, UserTagReputationId> {
  Optional<UserTagReputation> findByUserIdAndTagId(Long userId, Long tagId);
}
