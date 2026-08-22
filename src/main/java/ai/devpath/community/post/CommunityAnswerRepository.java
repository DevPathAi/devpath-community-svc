package ai.devpath.community.post;

import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.data.repository.query.Param;

public interface CommunityAnswerRepository extends JpaRepository<CommunityAnswer, Long> {
  java.util.List<CommunityAnswer> findByQuestionIdOrderByCreatedAtAsc(Long questionId);
  int countByQuestionId(Long questionId);
  long countByAuthorIdAndAiGeneratedFalse(Long authorId);
  long countByAuthorIdAndAiGeneratedFalseAndStatus(Long authorId, String status);

  /**
   * 변경 경로 전용 조회. 행을 잠가 같은 답변에 대한 다른 변경과 직렬화한다.
   *
   * <p>READ COMMITTED 에서 {@code FOR UPDATE} 는 락을 얻은 뒤 행을 <b>다시 읽는다</b>(측정함 —
   * 같은 조건에서 평범한 SELECT 는 옛 값을 본다). 그래서 이 조회 뒤의 status 판정이 비로소
   * 신뢰할 수 있게 된다.
   */
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "3000"))
  @Query("select a from CommunityAnswer a where a.id = :id")
  Optional<CommunityAnswer> findByIdForUpdate(@Param("id") long id);
}
