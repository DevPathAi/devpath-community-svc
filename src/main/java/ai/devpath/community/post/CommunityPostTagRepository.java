package ai.devpath.community.post;

import org.springframework.data.jpa.repository.JpaRepository;

public interface CommunityPostTagRepository extends JpaRepository<CommunityPostTag, CommunityPostTagId> {
  java.util.List<CommunityPostTag> findByPostId(Long postId);
}
