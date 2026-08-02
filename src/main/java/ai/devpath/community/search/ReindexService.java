package ai.devpath.community.search;

import ai.devpath.community.post.CommunityPostRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

/**
 * 전체 재색인. ES 재구축·기존 글 백필·색인 이벤트 유실 복구를 한 경로로 해결한다.
 *
 * <p>색인 자체는 {@link PostIndexer#index(long)} 에 위임한다 — 색인 문서 구성 규칙이
 * 증분 색인(Kafka 컨슈머)과 전량 재색인에서 갈라지지 않게 하기 위해서다.
 *
 * <p><b>계약의 한계 — 고아 문서는 정리하지 않는다.</b> 이 작업은 DB 의 {@code PUBLISHED} 글을
 * ES 로 밀어넣는 단방향 upsert 다. 따라서 <b>ES 에는 있는데 DB 에서 사라졌거나 더 이상
 * {@code PUBLISHED} 가 아닌 문서는 재색인해도 남는다</b>(조회 대상에 애초에 포함되지 않으므로
 * {@code PostIndexer} 의 비-{@code PUBLISHED} → delete 위임이 발동할 기회가 없다). 지금은 이
 * 레포에 글 수정·삭제 기능이 없어 고아가 생길 경로가 사실상 없지만, 모더레이션·신고로 글을
 * 내리는 기능이 붙으면 실현된다. 그때는 새 인덱스에 재색인 후 alias 를 스왑하는 방식으로
 * 바꿔야 한다(고아·매핑 변경·무중단이 함께 해결된다).
 */
@Service
public class ReindexService {

  private static final Logger log = LoggerFactory.getLogger(ReindexService.class);

  /** 한 번에 조회할 글 id 수. 베타 규모에선 1회로 끝나지만 전량 로딩을 막는 구조는 유지한다. */
  private static final int CHUNK = 500;

  private final CommunityPostRepository posts;
  private final PostIndexer indexer;

  @PersistenceContext
  private EntityManager em;

  public ReindexService(CommunityPostRepository posts, PostIndexer indexer) {
    this.posts = posts;
    this.indexer = indexer;
  }

  /**
   * {@code status='PUBLISHED'} 인 글을 전부 다시 색인하고 색인한 건수를 반환한다.
   *
   * <p>ES 장애 시 예외를 삼키지 않고 그대로 전파한다 — 부분 색인 상태를 "성공"으로 보고하면
   * 운영자가 재색인이 끝났다고 오판한다. 어디까지 진행했는지는 로그로 남긴다.
   */
  public int reindexAll() {
    log.info("전체 재색인 시작");
    int indexed = 0;
    long afterId = 0L;
    while (true) {
      List<Long> ids = posts.findPublishedIdsAfter(afterId, PageRequest.of(0, CHUNK));
      if (ids.isEmpty()) {
        log.info("전체 재색인 완료 indexed={}", indexed);
        return indexed;
      }
      for (Long id : ids) {
        indexer.index(id);
        indexed++;
      }
      afterId = ids.get(ids.size() - 1);
      // OSIV(spring.jpa.open-in-view 기본 true)로 요청 범위 영속성 컨텍스트가 열려 있어, 비우지
      // 않으면 색인한 모든 엔티티가 1차 캐시에 쌓여 CHUNK 분할의 의미가 사라진다.
      em.clear();
      log.info("재색인 진행 indexed={} lastId={}", indexed, afterId);
    }
  }
}
