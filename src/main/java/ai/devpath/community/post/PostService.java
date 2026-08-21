package ai.devpath.community.post;

import ai.devpath.community.post.dto.CommentView;
import ai.devpath.community.post.dto.CreatePostRequest;
import ai.devpath.community.post.dto.PostDetailView;
import ai.devpath.community.post.dto.UpdatePostRequest;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PostService {
  private static final Set<String> GENERAL_BOARDS = Set.of("FREE", "FEEDBACK");

  private final CommunityPostRepository posts;
  private final CommunityTagRepository tags;
  private final CommunityPostTagRepository postTags;
  private final CommunityCommentRepository comments;
  private final PostIndexEventPublisher postIndexEvents;
  private final ContentRevisionRecorder revisions;

  public PostService(CommunityPostRepository posts, CommunityTagRepository tags,
      CommunityPostTagRepository postTags, CommunityCommentRepository comments,
      PostIndexEventPublisher postIndexEvents, ContentRevisionRecorder revisions) {
    this.posts = posts; this.tags = tags; this.postTags = postTags; this.comments = comments;
    this.postIndexEvents = postIndexEvents; this.revisions = revisions;
  }

  @Transactional
  public PostDetailView createPost(long userId, CreatePostRequest req) {
    String board = req.boardType();
    if (board == null || !GENERAL_BOARDS.contains(board)) {
      throw new IllegalArgumentException("boardType must be FREE or FEEDBACK: " + board);
    }
    CommunityPost p = new CommunityPost();
    p.setAuthorId(userId); p.setBoardType(board);
    p.setTitle(req.title()); p.setBodyMd(req.bodyMd()); p.setStatus("PUBLISHED");
    p = posts.save(p);
    List<String> tagNames = req.tags() == null ? List.of() : req.tags();
    for (String name : tagNames) {
      CommunityTag tag = tags.findByName(name).orElseGet(() -> {
        CommunityTag t = new CommunityTag(); t.setName(name); return tags.save(t);
      });
      postTags.save(new CommunityPostTag(p.getId(), tag.getId()));
    }
    postIndexEvents.publish(p.getId(), false);
    return postDetail(p.getId());
  }

  @Transactional(readOnly = true)
  public PostDetailView postDetail(long postId) {
    CommunityPost p = posts.findById(postId)
        .filter(found -> ContentStatus.PUBLISHED.equals(found.getStatus()))
        .orElseThrow(() -> new NotFoundException("post " + postId));
    List<String> tagNames = tagNamesFor(postId);
    List<CommentView> commentViews = comments.findByPostIdOrderByCreatedAtAsc(postId).stream()
        .map(c -> new CommentView(c.getId(), c.getAuthorId(), c.getBodyMd(),
            c.getUpvoteCount(), c.getCreatedAt()))
        .collect(Collectors.toList());
    return new PostDetailView(p.getId(), p.getBoardType(), p.getTitle(), p.getBodyMd(),
        p.getAuthorId(), p.getUpvoteCount(), p.getDownvoteCount(), tagNames, commentViews);
  }

  /**
   * 글·질문 본문 수정. 질문(QNA)도 같은 community_posts 행이므로 여기서 함께 처리한다.
   *
   * <p>시간 제한을 두지 않는다 — "N분 안에만" 은 이력이 없을 때 왜곡을 막는 방어책이고,
   * 우리는 리비전을 남기므로 그 방어가 다른 방식으로 성립한다.
   */
  @Transactional
  public PostDetailView updatePost(long userId, long postId, UpdatePostRequest req) {
    if (req.bodyMd() == null || req.bodyMd().isBlank()) {
      throw new IllegalArgumentException("bodyMd must not be blank");
    }
    if (req.title() == null || req.title().isBlank()) {
      throw new IllegalArgumentException("title must not be blank");
    }
    CommunityPost p = posts.findById(postId)
        .filter(found -> ContentStatus.PUBLISHED.equals(found.getStatus()))
        .orElseThrow(() -> new NotFoundException("post " + postId));
    if (p.getAuthorId() == null || p.getAuthorId() != userId) {
      throw new ForbiddenException("작성자만 수정할 수 있습니다");
    }
    revisions.record("POST", postId, p.getTitle(), p.getBodyMd(), p.getBodyHtml(), userId);
    p.setTitle(req.title());
    p.setBodyMd(req.bodyMd());
    posts.save(p);
    postIndexEvents.publish(postId, false);
    return postDetail(postId);
  }

  /**
   * 작성자 삭제. 상태만 바꾸고 자식(댓글)은 건드리지 않는다.
   *
   * <p>자식 상태를 전파하지 않는 이유는 되돌릴 수 있어야 하기 때문이다 — 전파하면 복구할 때
   * 자식이 스스로 삭제된 것인지 부모 때문인지 구분이 사라진다. 도달 경로는 이미 막혀 있다
   * (부모가 404 이고 자식 목록도 부모 상태를 확인한다).
   *
   * <p>이미 삭제된 것을 다시 지우면 404 다. 프론트가 resourceNotFound 를
   * "이미 삭제된 콘텐츠예요" 로 렌더하므로 그 UI 와 맞물린다.
   */
  @Transactional
  public void deletePost(long userId, long postId) {
    CommunityPost p = posts.findById(postId)
        .filter(found -> ContentStatus.PUBLISHED.equals(found.getStatus()))
        .orElseThrow(() -> new NotFoundException("post " + postId));
    if (p.getAuthorId() == null || p.getAuthorId() != userId) {
      throw new ForbiddenException("작성자만 삭제할 수 있습니다");
    }
    p.setStatus(ContentStatus.DELETED);
    posts.save(p);
    postIndexEvents.publish(postId, true);
  }

  private List<String> tagNamesFor(long postId) {
    List<Long> ids = postTags.findByPostId(postId).stream()
        .map(CommunityPostTag::getTagId).collect(Collectors.toList());
    if (ids.isEmpty()) return List.of();
    return tags.findAllById(ids).stream().map(CommunityTag::getName).collect(Collectors.toList());
  }
}
