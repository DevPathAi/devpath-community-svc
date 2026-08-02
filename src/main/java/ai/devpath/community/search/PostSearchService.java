package ai.devpath.community.search;

import ai.devpath.community.post.QuestionService;
import ai.devpath.community.post.dto.PostSummaryView;
import ai.devpath.community.search.dto.SearchItemView;
import ai.devpath.community.search.dto.SearchResponse;
import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.SortOrder;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.elasticsearch.core.search.Hit;
import co.elastic.clients.elasticsearch.core.search.HighlightField;
import co.elastic.clients.util.NamedValue;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

/**
 * 커뮤니티 게시글 검색(ES 질의) 서비스.
 *
 * <p>ES에서는 매칭 id·하이라이트·총건수만 받고, 화면 표시용 {@link PostSummaryView}는 항상 DB에서 조립한다
 * ({@link QuestionService#summariesByIds(List)}) — ES 색인이 stale해도 화면 데이터는 정확해야 하기 때문이다.
 * ES 장애 시에는 빈 결과가 아니라 예외를 그대로 던져 5xx 응답이 되게 한다(검색 불가와 "결과 없음"을 구분).
 */
@Service
public class PostSearchService {

  private static final String PRE_TAG = "<em>";
  private static final String POST_TAG = "</em>";
  private static final String HIGHLIGHT_JOIN = " … ";
  /** 검색 API가 외부에서 키울 수 있는 DB N+1(summariesByIds 조립) 규모를 제한하는 상한. */
  static final int MAX_SIZE = 100;

  private final ElasticsearchClient client;
  private final SearchIndexProperties properties;
  private final QuestionService questionService;

  public PostSearchService(ElasticsearchClient client, SearchIndexProperties properties,
      QuestionService questionService) {
    this.client = client;
    this.properties = properties;
    this.questionService = questionService;
  }

  public SearchResponse search(String q, String board, String tag, Boolean solved, String sort,
      int page, int size) {
    int effectiveSize = Math.min(size, MAX_SIZE);
    List<Query> filters = buildFilters(board, tag, solved);
    try {
      co.elastic.clients.elasticsearch.core.SearchResponse<Map> resp = client.search(s -> {
        s.index(properties.getIndexName())
            .query(qq -> qq.bool(b -> b
                .must(m -> m.multiMatch(mm -> mm.query(q).fields("title^2", "bodyMd")))
                .filter(filters)))
            .highlight(h -> h
                .preTags(PRE_TAG)
                .postTags(POST_TAG)
                .fields(NamedValue.of("bodyMd", HighlightField.of(hf -> hf))))
            .from(page * effectiveSize)
            .size(effectiveSize);
        if ("latest".equals(sort)) {
          s.sort(so -> so.field(f -> f.field("createdAt").order(SortOrder.Desc)));
        }
        return s;
      }, Map.class);

      List<Hit<Map>> hits = resp.hits().hits();
      List<Long> ids = hits.stream().map(h -> Long.parseLong(h.id())).collect(Collectors.toList());
      Map<Long, String> highlights = new LinkedHashMap<>();
      for (Hit<Map> h : hits) {
        List<String> fragments = h.highlight() == null ? null : h.highlight().get("bodyMd");
        if (fragments != null && !fragments.isEmpty()) {
          highlights.put(Long.parseLong(h.id()), String.join(HIGHLIGHT_JOIN, fragments));
        }
      }
      long total = resp.hits().total() == null ? hits.size() : resp.hits().total().value();

      List<PostSummaryView> summaries = questionService.summariesByIds(ids);
      List<SearchItemView> items = summaries.stream()
          .map(sm -> new SearchItemView(sm.id(), sm.boardType(), sm.title(), sm.authorId(),
              sm.solved(), sm.upvoteCount(), sm.replyCount(), sm.excerpt(),
              highlights.getOrDefault(sm.id(), sm.excerpt())))
          .collect(Collectors.toList());

      return new SearchResponse(items, total, page, effectiveSize);
    } catch (IOException e) {
      throw new UncheckedIOException("ES 검색 실패 q=" + q, e);
    }
  }

  /**
   * {@code status=PUBLISHED} 고정 필터 + 파라미터가 있을 때만 boardType/tags/isSolved 필터를 더한다.
   * {@code board="ALL"}은 {@link QuestionService#list}와 동일하게 "필터 없음"으로 취급한다.
   */
  private List<Query> buildFilters(String board, String tag, Boolean solved) {
    List<Query> filters = new ArrayList<>();
    filters.add(Query.of(qb -> qb.term(t -> t.field("status").value("PUBLISHED"))));
    if (board != null && !board.isBlank() && !"ALL".equals(board)) {
      filters.add(Query.of(qb -> qb.term(t -> t.field("boardType").value(board))));
    }
    if (tag != null && !tag.isBlank()) {
      filters.add(Query.of(qb -> qb.term(t -> t.field("tags").value(tag))));
    }
    if (solved != null) {
      filters.add(Query.of(qb -> qb.term(t -> t.field("isSolved").value(solved))));
    }
    return filters;
  }
}
