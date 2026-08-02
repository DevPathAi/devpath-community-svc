package ai.devpath.community.search;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ai.devpath.community.post.CommunityPost;
import ai.devpath.community.post.CommunityPostRepository;
import ai.devpath.community.post.CommunityPostTag;
import ai.devpath.community.post.CommunityPostTagRepository;
import ai.devpath.community.post.CommunityQuestion;
import ai.devpath.community.post.CommunityQuestionRepository;
import ai.devpath.community.post.CommunityTag;
import ai.devpath.community.post.CommunityTagRepository;
import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.core.GetResponse;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/** 실제 postgres(devpath_citest) + 실제 ES(community_posts_it, nori)를 대상으로 색인 upsert/delete를 검증한다. */
@SpringBootTest
@ActiveProfiles("test")
class PostIndexerTest {

  @Autowired PostIndexer indexer;
  @Autowired SearchIndexProperties properties;
  @Autowired ElasticsearchClient client;
  @Autowired CommunityPostRepository posts;
  @Autowired CommunityQuestionRepository questions;
  @Autowired CommunityPostTagRepository postTags;
  @Autowired CommunityTagRepository tags;

  @Test
  void indexUpsertsPublishedPostFields() throws IOException {
    CommunityPost p = savePost("FREE", "PUBLISHED", "색인 제목", "색인 본문");
    CommunityTag tag = saveTag("tag-" + System.nanoTime());
    postTags.save(new CommunityPostTag(p.getId(), tag.getId()));

    indexer.index(p.getId());
    refresh();

    GetResponse<Map> got = client.get(
        g -> g.index(properties.getIndexName()).id(String.valueOf(p.getId())), Map.class);
    assertTrue(got.found(), "색인된 문서가 조회돼야 한다");
    Map<?, ?> source = got.source();
    assertEquals("색인 제목", source.get("title"));
    assertEquals("색인 본문", source.get("bodyMd"));
    assertEquals("FREE", source.get("boardType"));
    assertEquals(List.of(tag.getName()), source.get("tags"));
  }

  @Test
  void koreanBodySearchMatchesViaNori() throws IOException {
    CommunityPost p = savePost("QNA", "PUBLISHED", "리액트 질문",
        "리액트에서 useEffect가 두 번 실행됩니다");

    indexer.index(p.getId());
    refresh();

    SearchResponse<Map> resp = client.search(s -> s
        .index(properties.getIndexName())
        .query(q -> q.match(m -> m.field("bodyMd").query("useEffect"))), Map.class);

    boolean hit = resp.hits().hits().stream()
        .anyMatch(h -> String.valueOf(p.getId()).equals(h.id()));
    assertTrue(hit, "nori 분석기가 한국어 본문에서 'useEffect' 를 매칭해야 한다");
  }

  @Test
  void deleteRemovesDocument() throws IOException {
    CommunityPost p = savePost("FREE", "PUBLISHED", "삭제될 글", "본문");
    indexer.index(p.getId());
    refresh();

    indexer.delete(p.getId());
    refresh();

    GetResponse<Map> got = client.get(
        g -> g.index(properties.getIndexName()).id(String.valueOf(p.getId())), Map.class);
    assertFalse(got.found(), "삭제 후 문서가 없어야 한다");
  }

  @Test
  void deleteNonExistentDocumentDoesNotThrow() {
    long neverIndexedId = 9_999_000_000L;
    assertDoesNotThrow(() -> indexer.delete(neverIndexedId));
  }

  @Test
  void indexNonPublishedPostIsNotLeftInEs() throws IOException {
    CommunityPost p = savePost("FREE", "DRAFT", "임시글", "본문");

    indexer.index(p.getId());
    refresh();

    GetResponse<Map> got = client.get(
        g -> g.index(properties.getIndexName()).id(String.valueOf(p.getId())), Map.class);
    assertFalse(got.found(), "PUBLISHED 가 아닌 글은 ES 에 남으면 안 된다");
  }

  @Test
  void indexSolvedQnaSetsIsSolvedKeyToTrue() throws IOException {
    CommunityPost p = savePost("QNA", "PUBLISHED", "해결된 질문", "본문");
    CommunityQuestion q = new CommunityQuestion();
    q.setPostId(p.getId());
    q.setSolved(true);
    questions.save(q);

    indexer.index(p.getId());
    refresh();

    GetResponse<Map> got = client.get(
        g -> g.index(properties.getIndexName()).id(String.valueOf(p.getId())), Map.class);
    assertTrue(got.found(), "색인된 문서가 조회돼야 한다");
    Map<?, ?> source = got.source();
    assertTrue(source.containsKey("isSolved"), "source에 'isSolved' 키가 있어야 한다(getter가 solved로 벗겨지지 않아야 함)");
    assertFalse(source.containsKey("solved"), "'solved' 라는 키로 잘못 직렬화되면 안 된다");
    assertEquals(true, source.get("isSolved"), "해결된 QNA 글은 isSolved=true 여야 한다");
  }

  @Test
  void indexNonQnaLeavesIsSolvedKeyFalse() throws IOException {
    CommunityPost p = savePost("FREE", "PUBLISHED", "자유글", "본문");

    indexer.index(p.getId());
    refresh();

    GetResponse<Map> got = client.get(
        g -> g.index(properties.getIndexName()).id(String.valueOf(p.getId())), Map.class);
    assertTrue(got.found(), "색인된 문서가 조회돼야 한다");
    Map<?, ?> source = got.source();
    assertTrue(source.containsKey("isSolved"), "source에 'isSolved' 키가 있어야 한다");
    assertFalse(source.containsKey("solved"), "'solved' 라는 키로 잘못 직렬화되면 안 된다");
    assertEquals(false, source.get("isSolved"), "비-QNA 글은 항상 isSolved=false 여야 한다");
  }

  @Test
  void indexRemovesDocumentWhenPublishedPostBecomesDraft() throws IOException {
    CommunityPost p = savePost("FREE", "PUBLISHED", "발행 후 비공개 전환", "본문");

    indexer.index(p.getId());
    refresh();
    GetResponse<Map> publishedGet = client.get(
        g -> g.index(properties.getIndexName()).id(String.valueOf(p.getId())), Map.class);
    assertTrue(publishedGet.found(), "PUBLISHED 상태로 색인된 직후에는 문서가 있어야 한다");

    p.setStatus("DRAFT");
    posts.save(p);

    indexer.index(p.getId());
    refresh();
    GetResponse<Map> draftGet = client.get(
        g -> g.index(properties.getIndexName()).id(String.valueOf(p.getId())), Map.class);
    assertFalse(draftGet.found(), "PUBLISHED -> DRAFT 로 바뀐 뒤 재색인하면 ES 문서가 지워져야 한다");
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

  private CommunityTag saveTag(String name) {
    CommunityTag t = new CommunityTag();
    t.setName(name);
    return tags.save(t);
  }

  private void refresh() throws IOException {
    client.indices().refresh(r -> r.index(properties.getIndexName()));
  }
}
