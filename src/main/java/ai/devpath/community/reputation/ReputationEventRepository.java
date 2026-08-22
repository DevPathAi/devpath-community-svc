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

  /** 투표자(actor)가 작성자(user)의 서로 다른 글을 upvote한 개수(실가산분만, 역산 제외). 담합 탐지용. */
  @Query("""
      select count(distinct e.sourceId) from ReputationEvent e
      where e.actorId = :actorId and e.userId = :userId
        and e.reason in ('UPVOTE_Q','UPVOTE_A') and e.delta > 0
      """)
  long countDistinctUpvotedSourcesByActorToUser(@Param("actorId") Long actorId, @Param("userId") Long userId);

  /**
   * (userId, reason) 별 델타 순합. 관리자 삭제 시 평판을 되돌리는 데 쓴다.
   *
   * <p>★이벤트를 하나씩 뒤집으면 안 된다★ — 취소된 투표는 원본(+10)과 역산(-10)이 둘 다
   * 저장돼 있어 각각 뒤집으면 순효과가 0 이 된다. 순합이 0 이 아닌 것만 되돌려야 한다.
   */
  @Query("""
      select e.userId, e.reason, coalesce(sum(e.delta), 0) from ReputationEvent e
      where e.sourceType = :sourceType and e.sourceId = :sourceId
      group by e.userId, e.reason having coalesce(sum(e.delta), 0) <> 0
      """)
  List<Object[]> netBySource(@Param("sourceType") String sourceType,
      @Param("sourceId") Long sourceId);
}
