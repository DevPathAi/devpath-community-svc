package ai.devpath.community.reputation;

import java.time.Instant;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ReputationEventRepository extends JpaRepository<ReputationEvent, Long> {

  /** (투표자, 대상)에 대한 이 사용자의 기존 이벤트 — 투표 변경 역산용. */
  List<ReputationEvent> findByActorIdAndSourceTypeAndSourceId(Long actorId, String sourceType, Long sourceId);

  /** 오늘(>=since) 작성자가 upvote로 얻은 합 — 일일상한 산출. */
  @Query("""
      select coalesce(sum(e.delta), 0) from ReputationEvent e
      where e.userId = :userId and e.reason in ('UPVOTE_Q','UPVOTE_A') and e.createdAt >= :since
      """)
  int sumUpvoteGainSince(@Param("userId") Long userId, @Param("since") Instant since);
}
