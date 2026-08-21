package ai.devpath.community.reputation;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserReputationRepository extends JpaRepository<UserReputation, Long> {
  Optional<UserReputation> findByUserId(Long userId);

  /**
   * ★총점 가산을 DB 안에서 원자적으로 끝낸다★
   *
   * <p>find-then-save 는 두 가지로 깨진다. 행이 없을 때 두 요청이 각자 INSERT 해
   * {@code user_reputation_pkey} 를 위반하고(실측), 행이 있을 때는 둘 다 옛 총점을 읽어
   * 나중 것이 앞의 가산을 덮는다. 콘텐츠 행 락으로는 못 막는다 — 같은 작성자의 <b>서로 다른</b>
   * 콘텐츠에서 오는 요청은 서로 다른 행을 잠그기 때문이다.
   *
   * <p>{@code DataIntegrityViolationException} 을 잡는 방법은 쓸 수 없다. 호출자가
   * {@code @Transactional} 이라 그 예외를 잡으면 rollback-only 로 마킹돼 커밋 때 다시 터진다
   * (이 레포가 광고 기능에서 겪었고 {@code ReportService} 주석에 남아 있다).
   *
   * <p>{@code clearAutomatically} 가 필요하다 — 이 갱신은 JPA 를 우회하므로, 같은 트랜잭션에서
   * 이미 로드한 {@link UserReputation} 이 낡는다. 배지 판정이 그 값을 읽는다.
   */
  @Modifying(flushAutomatically = true, clearAutomatically = true)
  @Query(value = "INSERT INTO user_reputation(user_id, total) VALUES (:userId, :delta) "
      + "ON CONFLICT (user_id) DO UPDATE SET total = user_reputation.total + EXCLUDED.total",
      nativeQuery = true)
  void addTotalAtomically(@Param("userId") long userId, @Param("delta") int delta);
}
