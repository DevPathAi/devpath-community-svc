package ai.devpath.community.abuse;

import org.springframework.data.jpa.repository.JpaRepository;

public interface VoteAbuseSuspicionRepository extends JpaRepository<VoteAbuseSuspicion, Long> {
  boolean existsByActorIdAndTargetUserIdAndReason(Long actorId, Long targetUserId, String reason);
}
