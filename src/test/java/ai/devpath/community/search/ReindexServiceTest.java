package ai.devpath.community.search;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ai.devpath.community.post.CommunityPost;
import ai.devpath.community.post.CommunityPostRepository;
import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.core.GetResponse;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * 전체 재색인을 실제 postgres(devpath_citest) + 실제 ES(community_posts_it) 대상으로 검증한다.
 *
 * <p>이 레포의 테스트는 트랜잭션 롤백 없이 실 데이터를 적재하므로(다른 테스트가 남긴 글이 DB 에 남아
 * 있다) "3건이면 3 반환" 같은 절대 건수 단언은 성립하지 않는다. 그렇다고 "반환 건수 == 현재
 * PUBLISHED 총수"로 대조하면 검증 대상과 오라클이 같은 술어({@code status='PUBLISHED'})에서 파생돼
 * 건수 계약이 헐거워진다. 그래서 <b>생성 전 스냅샷을 찍고 델타를 단언</b>한다({@code before + 3}) —
 * 누적 데이터에 안전하면서 "정확히 N 건" 계약을 그대로 지킨다. 롤백 없는 이 레포의 테스트에서는
 * 이 방식을 관례로 삼는다.
 */
@SpringBootTest
@ActiveProfiles("test")
class ReindexServiceTest {

  @Autowired ReindexService reindexService;
  @Autowired PostIndexer indexer;
  @Autowired SearchIndexProperties properties;
  @Autowired ElasticsearchClient client;
  @Autowired CommunityPostRepository posts;

  @Test
  void reindexAllIndexesEveryPublishedPost() throws IOException {
    int before = publishedCount();
    List<CommunityPost> created = List.of(
        savePost("QNA", "PUBLISHED", "재색인 대상 질문", "재색인 본문 1"),
        savePost("FREE", "PUBLISHED", "재색인 대상 자유글", "재색인 본문 2"),
        savePost("FREE", "PUBLISHED", "재색인 대상 자유글 2", "재색인 본문 3"));

    int indexed = reindexService.reindexAll();
    refresh();

    assertEquals(before + 3, indexed, "새로 만든 3건이 정확히 더 색인돼야 한다");
    for (CommunityPost p : created) {
      assertTrue(existsInEs(p.getId()), "재색인 후 ES 에 문서가 있어야 한다 postId=" + p.getId());
    }
  }

  @Test
  void reindexAllSkipsNonPublishedPosts() throws IOException {
    int before = publishedCount();
    CommunityPost draft = savePost("FREE", "DRAFT", "재색인 제외 비공개 글", "본문");

    int indexed = reindexService.reindexAll();
    refresh();

    // 실질 검증은 이 델타 단언이다 — 조회 쿼리가 DRAFT 를 포함하도록 망가지면 여기서 잡힌다.
    assertEquals(before, indexed, "비공개 글은 색인 건수를 늘리지 않아야 한다");
    // 아래 단언은 이중으로 보호돼 있어 약하다(새 id 라 ES 에 애초에 없고, 설령 쿼리가 DRAFT 를
    // 반환해도 PostIndexer.index() 가 비-PUBLISHED 를 delete 로 위임한다). 계약을 문서화하는
    // 의미로 남긴다.
    assertFalse(existsInEs(draft.getId()), "비공개 글은 색인되면 안 된다");
  }

  @Test
  void reindexAllRestoresIndexAfterItIsWipedOut() throws IOException {
    CommunityPost p = savePost("QNA", "PUBLISHED", "유실 복구 대상", "본문");
    indexer.index(p.getId());
    refresh();
    assertTrue(existsInEs(p.getId()), "사전 조건: 색인돼 있어야 한다");

    // 색인 유실(ES 재구축·볼륨 손실) 시나리오를 인덱스 전량 삭제로 재현한다.
    client.deleteByQuery(d -> d.index(properties.getIndexName()).query(q -> q.matchAll(m -> m)));
    refresh();
    assertFalse(existsInEs(p.getId()), "인덱스를 비운 뒤에는 문서가 없어야 한다");

    reindexService.reindexAll();
    refresh();

    assertTrue(existsInEs(p.getId()), "재색인으로 유실된 문서가 복구돼야 한다");
  }

  /** status='PUBLISHED' 인 글 전체 건수. `findAllBoardsNewest()` 가 정확히 그 집합을 반환한다. */
  private int publishedCount() {
    return posts.findAllBoardsNewest().size();
  }

  private boolean existsInEs(long postId) throws IOException {
    GetResponse<Map> got = client.get(
        g -> g.index(properties.getIndexName()).id(String.valueOf(postId)), Map.class);
    return got.found();
  }

  private CommunityPost savePost(String board, String status, String title, String bodyMd) {
    CommunityPost p = new CommunityPost();
    p.setAuthorId(1L);
    p.setBoardType(board);
    p.setTitle(title);
    p.setBodyMd(bodyMd);
    p.setStatus(status);
    p = posts.save(p);
    // insertable=false/updatable=false 컬럼(createdAt)은 DB 기본값이라 save() 직후에는 비어 있다 — 재조회로 확보.
    return posts.findById(p.getId()).orElseThrow();
  }

  private void refresh() throws IOException {
    client.indices().refresh(r -> r.index(properties.getIndexName()));
  }
}
