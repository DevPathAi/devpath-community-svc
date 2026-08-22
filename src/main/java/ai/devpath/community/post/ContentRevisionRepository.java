package ai.devpath.community.post;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ContentRevisionRepository extends JpaRepository<ContentRevision, Long> {
  List<ContentRevision> findByTargetTypeAndTargetIdOrderByCreatedAtDesc(
      String targetType, Long targetId);
}
