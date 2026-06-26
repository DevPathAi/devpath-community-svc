package ai.devpath.community.post;

import org.springframework.data.jpa.repository.JpaRepository;

public interface CommunityAnswerRepository extends JpaRepository<CommunityAnswer, Long> {
  java.util.List<CommunityAnswer> findByQuestionIdOrderByCreatedAtAsc(Long questionId);
  int countByQuestionId(Long questionId);
}
