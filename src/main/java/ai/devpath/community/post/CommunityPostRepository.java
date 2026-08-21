package ai.devpath.community.post;

import org.springframework.data.jpa.repository.JpaRepository;

public interface CommunityPostRepository extends JpaRepository<CommunityPost, Long> {
  @org.springframework.data.jpa.repository.Query(
    "select p from CommunityPost p where p.boardType = :board and p.status = 'PUBLISHED' "
    + "order by p.id desc")
  java.util.List<CommunityPost> findBoardNewest(String board);

  @org.springframework.data.jpa.repository.Query(
    "select p from CommunityPost p where p.status = 'PUBLISHED' order by p.id desc")
  java.util.List<CommunityPost> findAllBoardsNewest();

  long countByAuthorId(Long authorId);

  long countByAuthorIdAndStatus(Long authorId, String status);

  /**
   * 재색인용 keyset 페이징. id 오름차순으로 {@code afterId} 다음 구간을 청크만큼 반환한다.
   * offset 페이징과 달리 순회 중 글이 추가·삭제돼도 건너뛰거나 중복 조회하지 않는다.
   */
  @org.springframework.data.jpa.repository.Query(
    "select p.id from CommunityPost p where p.status = 'PUBLISHED' and p.id > :afterId "
    + "order by p.id")
  java.util.List<Long> findPublishedIdsAfter(long afterId,
      org.springframework.data.domain.Pageable pageable);
}
