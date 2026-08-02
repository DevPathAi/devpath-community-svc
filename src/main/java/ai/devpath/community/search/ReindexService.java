package ai.devpath.community.search;

import ai.devpath.community.post.CommunityPostRepository;
import java.util.List;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

/**
 * 전체 재색인. 이벤트 유실·ES 재구축·기존 글 백필을 한 경로로 해결한다.
 *
 * <p>색인 자체는 {@link PostIndexer#index(long)} 에 위임한다 — 색인 문서 구성 규칙이
 * 증분 색인(Kafka 컨슈머)과 전량 재색인에서 갈라지지 않게 하기 위해서다.
 */
@Service
public class ReindexService {

  /** 한 번에 조회할 글 id 수. 베타 규모에선 1회로 끝나지만 전량 로딩을 막는 구조는 유지한다. */
  private static final int CHUNK = 500;

  private final CommunityPostRepository posts;
  private final PostIndexer indexer;

  public ReindexService(CommunityPostRepository posts, PostIndexer indexer) {
    this.posts = posts;
    this.indexer = indexer;
  }

  /**
   * {@code status='PUBLISHED'} 인 글을 전부 다시 색인하고 색인한 건수를 반환한다.
   *
   * <p>ES 장애 시 예외를 삼키지 않고 그대로 전파한다 — 부분 색인 상태를 "성공"으로 보고하면
   * 운영자가 재색인이 끝났다고 오판한다.
   */
  public int reindexAll() {
    int indexed = 0;
    long afterId = 0L;
    while (true) {
      List<Long> ids = posts.findPublishedIdsAfter(afterId, PageRequest.of(0, CHUNK));
      if (ids.isEmpty()) {
        return indexed;
      }
      for (Long id : ids) {
        indexer.index(id);
        indexed++;
      }
      afterId = ids.get(ids.size() - 1);
    }
  }
}
