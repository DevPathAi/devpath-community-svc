package ai.devpath.community.post;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CommunityCommentRepository extends JpaRepository<CommunityComment, Long> {
  List<CommunityComment> findByPostIdOrderByCreatedAtAsc(long postId);
  long countByPostId(long postId);
}
