package ai.devpath.community.post;

import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.data.repository.query.Param;

public interface CommunityQuestionRepository extends JpaRepository<CommunityQuestion, Long> {

  /**
   * 변경 경로 전용 조회 — ★질문 행이 "서로 다른 답변" 들의 공유 상태다★. 답변 행 락만으로는
   * 동시 채택이 직렬화되지 않아, 질문(채택·solved)을 바꾸는 경로는 이것으로 잠근다.
   * 전역 락 순서: answer → question → post → (맨 마지막) 평판.
   */
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "3000"))
  @Query("select q from CommunityQuestion q where q.postId = :postId")
  Optional<CommunityQuestion> findByIdForUpdate(@Param("postId") long postId);
}
