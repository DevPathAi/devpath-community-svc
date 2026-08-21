package ai.devpath.community.reputation;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserTagReputationRepository
    extends JpaRepository<UserTagReputation, UserTagReputationId> {
  Optional<UserTagReputation> findByUserIdAndTagId(Long userId, Long tagId);

  /**
   * 태그별 점수 가산. {@link UserReputationRepository#addTotalAtomically} 와 같은 이유·같은 방식이다
   * — find-then-save 가 복합 PK 에 대해서도 똑같이 깨진다.
   */
  @Modifying(flushAutomatically = true, clearAutomatically = true)
  @Query(value = "INSERT INTO user_tag_reputation(user_id, tag_id, score) "
      + "VALUES (:userId, :tagId, :delta) ON CONFLICT (user_id, tag_id) "
      + "DO UPDATE SET score = user_tag_reputation.score + EXCLUDED.score",
      nativeQuery = true)
  void addScoreAtomically(@Param("userId") long userId, @Param("tagId") long tagId,
      @Param("delta") int delta);
}
