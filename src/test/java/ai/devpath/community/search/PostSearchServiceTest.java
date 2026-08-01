package ai.devpath.community.search;

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
import ai.devpath.community.search.dto.SearchItemView;
import ai.devpath.community.search.dto.SearchResponse;
import co.elastic.clients.elasticsearch.ElasticsearchClient;
import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * 실제 postgres(devpath_citest) + 실제 ES(community_posts_it, nori)를 대상으로 검색을 검증한다. 색인은
 * {@link PostIndexer}(Task 2)를 재사용해 실제 색인 경로를 통과시킨다. 테스트 인덱스는 이전 실행분이 남아있을 수
 * 있으므로, 각 케이스는 {@code System.nanoTime()} 기반의 고유 키워드로 검색해 다른 테스트/이전 실행 데이터와
 * 격리한다(anyMatch 검증 제외).
 */
@SpringBootTest
@ActiveProfiles("test")
class PostSearchServiceTest {

  @Autowired PostSearchService searchService;
  @Autowired PostIndexer indexer;
  @Autowired SearchIndexProperties properties;
  @Autowired ElasticsearchClient client;
  @Autowired CommunityPostRepository posts;
  @Autowired CommunityQuestionRepository questions;
  @Autowired CommunityPostTagRepository postTags;
  @Autowired CommunityTagRepository tags;

  @Test
  void koreanKeywordMatchesReactBody() throws IOException {
    CommunityPost p = savePost("FREE", "PUBLISHED", "일반 제목", "리액트 상태관리는 Riverpod이 편합니다");
    indexAndRefresh(p.getId());

    SearchResponse resp = searchService.search("Riverpod", null, null, null, "relevance", 0, 20);

    assertTrue(resp.items().stream().anyMatch(i -> i.id() == p.getId()),
        "본문에 Riverpod이 포함된 글이 검색돼야 한다");
  }

  @Test
  void titleMatchRanksAboveBodyOnlyMatch() throws IOException {
    String kw = uniqueWord();
    CommunityPost titleHit = savePost("FREE", "PUBLISHED", kw + " 제목에 있음", "본문에는 없음");
    CommunityPost bodyHit = savePost("FREE", "PUBLISHED", "제목에는 없음", "본문에 " + kw + " 있습니다");
    indexAndRefresh(titleHit.getId());
    indexAndRefresh(bodyHit.getId());

    SearchResponse resp = searchService.search(kw, null, null, null, "relevance", 0, 20);

    List<Long> ids = ids(resp);
    assertEquals(List.of(titleHit.getId(), bodyHit.getId()), ids,
        "제목 가중치(title^2)로 제목 매칭 글이 본문 매칭 글보다 앞서야 한다");
  }

  @Test
  void boardFilterExcludesOtherBoards() throws IOException {
    String kw = uniqueWord();
    CommunityPost qna = savePost("QNA", "PUBLISHED", kw + " 질문", "내용");
    CommunityPost free = savePost("FREE", "PUBLISHED", kw + " 자유글", "내용");
    indexAndRefresh(qna.getId());
    indexAndRefresh(free.getId());

    SearchResponse resp = searchService.search(kw, "QNA", null, null, "relevance", 0, 20);

    List<Long> ids = ids(resp);
    assertTrue(ids.contains(qna.getId()));
    assertFalse(ids.contains(free.getId()));
  }

  @Test
  void tagFilterOnlyReturnsTaggedPosts() throws IOException {
    String kw = uniqueWord();
    CommunityTag tag = saveTag("tag-" + System.nanoTime());
    CommunityPost tagged = savePost("FREE", "PUBLISHED", kw + " 태그있음", "내용");
    postTags.save(new CommunityPostTag(tagged.getId(), tag.getId()));
    CommunityPost untagged = savePost("FREE", "PUBLISHED", kw + " 태그없음", "내용");
    indexAndRefresh(tagged.getId());
    indexAndRefresh(untagged.getId());

    SearchResponse resp = searchService.search(kw, null, tag.getName(), null, "relevance", 0, 20);

    List<Long> ids = ids(resp);
    assertTrue(ids.contains(tagged.getId()));
    assertFalse(ids.contains(untagged.getId()));
  }

  @Test
  void solvedFilterOnlyReturnsSolvedQna() throws IOException {
    String kw = uniqueWord();
    CommunityPost solvedPost = savePost("QNA", "PUBLISHED", kw + " 해결됨", "내용");
    CommunityQuestion solvedQ = new CommunityQuestion();
    solvedQ.setPostId(solvedPost.getId());
    solvedQ.setSolved(true);
    questions.save(solvedQ);

    CommunityPost unsolvedPost = savePost("QNA", "PUBLISHED", kw + " 미해결", "내용");
    CommunityQuestion unsolvedQ = new CommunityQuestion();
    unsolvedQ.setPostId(unsolvedPost.getId());
    questions.save(unsolvedQ);

    indexAndRefresh(solvedPost.getId());
    indexAndRefresh(unsolvedPost.getId());

    SearchResponse resp = searchService.search(kw, null, null, true, "relevance", 0, 20);

    List<Long> ids = ids(resp);
    assertTrue(ids.contains(solvedPost.getId()));
    assertFalse(ids.contains(unsolvedPost.getId()));
  }

  @Test
  void pagingLimitsItemsButTotalReflectsAllMatches() throws IOException {
    String kw = uniqueWord();
    CommunityPost p1 = savePost("FREE", "PUBLISHED", kw + " 1", "내용");
    CommunityPost p2 = savePost("FREE", "PUBLISHED", kw + " 2", "내용");
    CommunityPost p3 = savePost("FREE", "PUBLISHED", kw + " 3", "내용");
    indexAndRefresh(p1.getId());
    indexAndRefresh(p2.getId());
    indexAndRefresh(p3.getId());

    SearchResponse resp = searchService.search(kw, null, null, null, "relevance", 0, 2);

    assertEquals(2, resp.items().size());
    assertEquals(3, resp.total());
    assertEquals(0, resp.page());
    assertEquals(2, resp.size());
  }

  @Test
  void sortLatestOrdersByCreatedAtDescending() throws IOException, InterruptedException {
    String kw = uniqueWord();
    CommunityPost older = savePost("FREE", "PUBLISHED", kw + " 오래된글", "내용");
    Thread.sleep(10);
    CommunityPost newer = savePost("FREE", "PUBLISHED", kw + " 최신글", "내용");
    indexAndRefresh(older.getId());
    indexAndRefresh(newer.getId());

    SearchResponse resp = searchService.search(kw, null, null, null, "latest", 0, 20);

    assertEquals(List.of(newer.getId(), older.getId()), ids(resp));
  }

  @Test
  void highlightContainsEmTagsOnBodyMatch() throws IOException {
    String kw = uniqueWord();
    CommunityPost p = savePost("FREE", "PUBLISHED", "제목", "본문에 " + kw + " 포함됩니다");
    indexAndRefresh(p.getId());

    SearchResponse resp = searchService.search(kw, null, null, null, "relevance", 0, 20);

    SearchItemView item = itemFor(resp, p.getId());
    assertTrue(item.highlight().contains("<em>"), "본문 매칭 하이라이트에 <em> 태그가 포함돼야 한다: " + item.highlight());
    assertTrue(item.highlight().contains(kw));
  }

  @Test
  void highlightFallsBackToExcerptWhenNoBodyMatch() throws IOException {
    String kw = uniqueWord();
    CommunityPost p = savePost("FREE", "PUBLISHED", kw + " 제목에만 있음", "본문에는 전혀 다른 내용입니다");
    indexAndRefresh(p.getId());

    SearchResponse resp = searchService.search(kw, null, null, null, "relevance", 0, 20);

    SearchItemView item = itemFor(resp, p.getId());
    assertFalse(item.highlight().contains("<em>"), "본문 매칭이 없으면 하이라이트가 없어야 한다: " + item.highlight());
    assertEquals(item.excerpt(), item.highlight());
  }

  @Test
  void resultOrderPreservesElasticsearchRelevanceOrder() throws IOException {
    String kw = uniqueWord();
    // DB 삽입 순서(low, high, mid)와 기대 관련도 순서(high, mid, low)를 의도적으로 어긋나게 해
    // summariesByIds가 DB id 순서가 아니라 ES가 반환한 순서를 그대로 보존하는지 검증한다.
    CommunityPost low = savePost("FREE", "PUBLISHED", "낮은 관련도", kw + " 한 번 언급");
    CommunityPost high = savePost("FREE", "PUBLISHED", "높은 관련도", kw + " " + kw + " " + kw + " 세 번 언급");
    CommunityPost mid = savePost("FREE", "PUBLISHED", "중간 관련도", kw + " " + kw + " 두 번 언급");
    indexAndRefresh(low.getId());
    indexAndRefresh(high.getId());
    indexAndRefresh(mid.getId());

    SearchResponse resp = searchService.search(kw, null, null, null, "relevance", 0, 20);

    assertEquals(List.of(high.getId(), mid.getId(), low.getId()), ids(resp),
        "items 순서는 summariesByIds가 보존한 ES 관련도 순서와 같아야 한다");
  }

  @Test
  void boardAllIsTreatedAsNoFilterSameAsQuestionServiceList() throws IOException {
    // QuestionService.list()의 기존 계약(board == null || blank || "ALL" -> 전체)과 동일해야 한다.
    String kw = uniqueWord();
    CommunityPost qna = savePost("QNA", "PUBLISHED", kw + " 질문", "내용");
    CommunityPost free = savePost("FREE", "PUBLISHED", kw + " 자유글", "내용");
    indexAndRefresh(qna.getId());
    indexAndRefresh(free.getId());

    SearchResponse resp = searchService.search(kw, "ALL", null, null, "relevance", 0, 20);

    List<Long> ids = ids(resp);
    assertTrue(ids.contains(qna.getId()), "board=ALL은 QNA도 포함해야 한다");
    assertTrue(ids.contains(free.getId()), "board=ALL은 FREE도 포함해야 한다");
  }

  @Test
  void sizeAboveMaxIsClampedTo100() throws IOException {
    String kw = uniqueWord();
    CommunityPost p = savePost("FREE", "PUBLISHED", kw + " 제목", "내용");
    indexAndRefresh(p.getId());

    SearchResponse resp = searchService.search(kw, null, null, null, "relevance", 0, 1000);

    assertTrue(resp.items().size() <= 100, "items는 상한(100)을 넘으면 안 된다");
    assertEquals(100, resp.size(), "응답의 size 필드는 실제 적용된(클램프된) 값을 반영해야 한다");
  }

  @Test
  void unpublishedPostsAreExcluded() throws IOException {
    String kw = uniqueWord();
    CommunityPost draft = savePost("FREE", "DRAFT", kw + " 초안", "내용");
    indexAndRefresh(draft.getId());

    SearchResponse resp = searchService.search(kw, null, null, null, "relevance", 0, 20);

    assertFalse(ids(resp).contains(draft.getId()), "PUBLISHED가 아닌 글은 검색 결과에 없어야 한다");
  }

  /**
   * 순수 알파벳으로만 구성된 고유 토큰을 만든다. {@code "kw" + nanoTime()} 같은 문자+숫자 혼합은
   * 표준/nori 토크나이저가 letter-digit 경계에서 분리해("kw", "12345") 공통 접두사 "kw" 하나만으로도
   * 다른 테스트가 남긴 문서와 OR 매칭돼 결과가 오염된다(letter run만 있으면 하나의 토큰으로 유지된다).
   */
  private static String uniqueWord() {
    long n = Math.abs(System.nanoTime());
    StringBuilder sb = new StringBuilder();
    for (int i = 0; i < 12; i++) {
      sb.append((char) ('a' + (int) (n % 26)));
      n /= 26;
    }
    return sb.toString();
  }

  private static List<Long> ids(SearchResponse resp) {
    return resp.items().stream().map(SearchItemView::id).collect(Collectors.toList());
  }

  private static SearchItemView itemFor(SearchResponse resp, long id) {
    return resp.items().stream().filter(i -> i.id() == id).findFirst()
        .orElseThrow(() -> new AssertionError("검색 결과에 id=" + id + " 가 없다"));
  }

  private void indexAndRefresh(long postId) throws IOException {
    indexer.index(postId);
    client.indices().refresh(r -> r.index(properties.getIndexName()));
  }

  private CommunityPost savePost(String board, String status, String title, String bodyMd) {
    CommunityPost p = new CommunityPost();
    p.setAuthorId(1L);
    p.setBoardType(board);
    p.setTitle(title);
    p.setBodyMd(bodyMd);
    p.setStatus(status);
    p = posts.save(p);
    return posts.findById(p.getId()).orElseThrow();
  }

  private CommunityTag saveTag(String name) {
    CommunityTag t = new CommunityTag();
    t.setName(name);
    return tags.save(t);
  }
}
