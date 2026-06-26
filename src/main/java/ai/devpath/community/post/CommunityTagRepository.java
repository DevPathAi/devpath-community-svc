package ai.devpath.community.post;

import org.springframework.data.jpa.repository.JpaRepository;

public interface CommunityTagRepository extends JpaRepository<CommunityTag, Long> {
  java.util.Optional<CommunityTag> findByName(String name);
}
