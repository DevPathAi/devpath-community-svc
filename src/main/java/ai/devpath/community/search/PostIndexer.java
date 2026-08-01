package ai.devpath.community.search;

import ai.devpath.community.post.CommunityPost;
import ai.devpath.community.post.CommunityPostRepository;
import ai.devpath.community.post.CommunityPostTag;
import ai.devpath.community.post.CommunityPostTagRepository;
import ai.devpath.community.post.CommunityQuestion;
import ai.devpath.community.post.CommunityQuestionRepository;
import ai.devpath.community.post.CommunityTag;
import ai.devpath.community.post.CommunityTagRepository;
import co.elastic.clients.elasticsearch.ElasticsearchClient;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

/**
 * 커뮤니티 게시글을 검색 인덱스(ES)에 색인(upsert)·삭제하는 순수 ES 서비스.
 *
 * <p>Kafka/Outbox를 알지 못한다 — Task 4(컨슈머)가 이 클래스를 호출해 색인을 트리거한다. ES 장애 시
 * 예외를 삼키지 않고 그대로 던진다({@link PostIndexBootstrap}과 달리 이 클래스는 앱 기동 경로가 아니라
 * 호출자(컨슈머)가 재시도 여부를 결정해야 하기 때문이다).
 */
@Service
public class PostIndexer {

  private final ElasticsearchClient client;
  private final SearchIndexProperties properties;
  private final CommunityPostRepository posts;
  private final CommunityQuestionRepository questions;
  private final CommunityPostTagRepository postTags;
  private final CommunityTagRepository tags;

  public PostIndexer(ElasticsearchClient client, SearchIndexProperties properties,
      CommunityPostRepository posts, CommunityQuestionRepository questions,
      CommunityPostTagRepository postTags, CommunityTagRepository tags) {
    this.client = client;
    this.properties = properties;
    this.posts = posts;
    this.questions = questions;
    this.postTags = postTags;
    this.tags = tags;
  }

  /**
   * DB에서 글을 읽어 ES에 upsert 한다. 글이 없거나 {@code status != PUBLISHED} 이면 비공개 전환·삭제로
   * 간주해 {@link #delete(long)} 로 위임한다.
   */
  public void index(long postId) {
    Optional<CommunityPost> found = posts.findById(postId);
    if (found.isEmpty() || !"PUBLISHED".equals(found.get().getStatus())) {
      delete(postId);
      return;
    }
    CommunityPost p = found.get();
    PostDocument doc = new PostDocument(
        p.getTitle(),
        p.getBodyMd(),
        tagNamesFor(postId),
        p.getBoardType(),
        p.getStatus(),
        String.valueOf(p.getAuthorId()),
        isSolved(p),
        p.getCreatedAt() == null ? null : p.getCreatedAt().truncatedTo(ChronoUnit.MILLIS).toString());
    try {
      String indexName = properties.getIndexName();
      String id = String.valueOf(postId);
      client.index(i -> i.index(indexName).id(id).document(doc));
    } catch (IOException e) {
      throw new UncheckedIOException("ES 색인 실패 postId=" + postId, e);
    }
  }

  /** ES 문서를 삭제한다. 문서가 없어도 예외를 던지지 않는다(멱등 — ES delete API는 404를 result=not_found로 응답). */
  public void delete(long postId) {
    try {
      String indexName = properties.getIndexName();
      String id = String.valueOf(postId);
      client.delete(d -> d.index(indexName).id(id));
    } catch (IOException e) {
      throw new UncheckedIOException("ES 삭제 실패 postId=" + postId, e);
    }
  }

  /** Q&A 게시판만 실제 해결 여부를 반영한다. 그 외 게시판은 항상 false(QuestionService.list()와 동일 규칙). */
  private boolean isSolved(CommunityPost p) {
    boolean isQna = "QNA".equals(p.getBoardType());
    return isQna
        ? questions.findById(p.getId()).map(CommunityQuestion::isSolved).orElse(false)
        : false;
  }

  private List<String> tagNamesFor(long postId) {
    List<Long> ids = postTags.findByPostId(postId).stream()
        .map(CommunityPostTag::getTagId).collect(Collectors.toList());
    if (ids.isEmpty()) return List.of();
    return tags.findAllById(ids).stream().map(CommunityTag::getName).collect(Collectors.toList());
  }

  /** ES 인덱스 매핑(Task 1)의 8개 필드에 대응하는 색인 문서. */
  static final class PostDocument {
    private final String title;
    private final String bodyMd;
    private final List<String> tags;
    private final String boardType;
    private final String status;
    private final String authorId;
    private final boolean isSolved;
    private final String createdAt;

    PostDocument(String title, String bodyMd, List<String> tags, String boardType, String status,
        String authorId, boolean isSolved, String createdAt) {
      this.title = title;
      this.bodyMd = bodyMd;
      this.tags = tags;
      this.boardType = boardType;
      this.status = status;
      this.authorId = authorId;
      this.isSolved = isSolved;
      this.createdAt = createdAt;
    }

    public String getTitle() { return title; }
    public String getBodyMd() { return bodyMd; }
    public List<String> getTags() { return tags; }
    public String getBoardType() { return boardType; }
    public String getStatus() { return status; }
    public String getAuthorId() { return authorId; }
    public boolean getIsSolved() { return isSolved; }
    public String getCreatedAt() { return createdAt; }
  }
}
