package ai.devpath.community.post;

import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.data.repository.query.Param;

public interface CommunityCommentRepository extends JpaRepository<CommunityComment, Long> {
  List<CommunityComment> findByPostIdOrderByCreatedAtAsc(long postId);
  long countByPostId(long postId);

  /**
   * 변경 경로 전용 조회. {@link CommunityAnswerRepository#findByIdForUpdate} 와 같은 계약이다.
   * 엔티티에 {@code @DynamicUpdate} 가 없어 flush 는 전 컬럼 UPDATE 라, 잠그지 않은 수정이
   * 내리기와 겹치면 stale PUBLISHED 가 HIDDEN 을 되돌려 쓴다(실측).
   */
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "3000"))
  @Query("select c from CommunityComment c where c.id = :id")
  Optional<CommunityComment> findByIdForUpdate(@Param("id") long id);
}
