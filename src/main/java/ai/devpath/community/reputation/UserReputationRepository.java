package ai.devpath.community.reputation;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserReputationRepository extends JpaRepository<UserReputation, Long> {
  Optional<UserReputation> findByUserId(Long userId);
}
