package ai.devpath.community.post;

import org.springframework.data.jpa.repository.JpaRepository;

public interface CommunityVoteRepository extends JpaRepository<CommunityVote, Long> {
  java.util.Optional<CommunityVote> findByUserIdAndTargetTypeAndTargetId(Long userId, String targetType, Long targetId);
  int countByTargetTypeAndTargetIdAndValue(String targetType, Long targetId, short value);
}
