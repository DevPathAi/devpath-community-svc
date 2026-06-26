package ai.devpath.community.post;

import org.springframework.data.jpa.repository.JpaRepository;

public interface CommunityPostRepository extends JpaRepository<CommunityPost, Long> {
  @org.springframework.data.jpa.repository.Query(
    "select p from CommunityPost p where p.boardType = :board and p.status = 'PUBLISHED' "
    + "order by p.id desc")
  java.util.List<CommunityPost> findBoardNewest(String board);
}
