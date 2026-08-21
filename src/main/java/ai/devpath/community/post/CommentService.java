package ai.devpath.community.post;

import ai.devpath.community.post.dto.CommentView;
import ai.devpath.community.post.dto.CreateCommentRequest;
import ai.devpath.community.post.dto.UpdateBodyRequest;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CommentService {
  private final CommunityPostRepository posts;
  private final CommunityCommentRepository comments;
  private final ContentRevisionRecorder revisions;

  public CommentService(CommunityPostRepository posts, CommunityCommentRepository comments,
      ContentRevisionRecorder revisions) {
    this.posts = posts; this.comments = comments; this.revisions = revisions;
  }

  /** 비석 여부를 한 곳에서 판단한다 — 목록과 상세 두 경로가 같은 규칙을 쓰게 하기 위해서다. */
  static CommentView toView(CommunityComment c) {
    return ContentStatus.PUBLISHED.equals(c.getStatus())
        ? new CommentView(c.getId(), c.getAuthorId(), c.getBodyMd(), c.getUpvoteCount(),
            c.getCreatedAt(), false)
        : CommentView.tombstone(c.getId(), c.getUpvoteCount(), c.getCreatedAt());
  }

  @Transactional
  public CommentView addComment(long userId, long postId, CreateCommentRequest req) {
    if (posts.findById(postId).isEmpty()) {
      throw new NotFoundException("post " + postId);
    }
    CommunityComment c = new CommunityComment();
    c.setPostId(postId); c.setAuthorId(userId); c.setBodyMd(req.bodyMd());
    c = comments.save(c);
    return toView(c);
  }

  @Transactional(readOnly = true)
  public List<CommentView> listComments(long postId) {
    // 부모가 비공개면 자식으로 우회 조회할 수 없어야 한다.
    posts.findById(postId)
        .filter(p -> ContentStatus.PUBLISHED.equals(p.getStatus()))
        .orElseThrow(() -> new NotFoundException("post " + postId));
    return comments.findByPostIdOrderByCreatedAtAsc(postId).stream()
        .map(CommentService::toView)
        .collect(Collectors.toList());
  }

  @Transactional
  public CommentView update(long userId, long commentId, UpdateBodyRequest req) {
    if (req.bodyMd() == null || req.bodyMd().isBlank()) {
      throw new IllegalArgumentException("bodyMd must not be blank");
    }
    // 잠그는 이유는 PostService.updatePost 와 같다 — 전 컬럼 flush 가 stale 상태를 되돌려 쓴다.
    CommunityComment c = comments.findByIdForUpdate(commentId)
        .filter(found -> ContentStatus.PUBLISHED.equals(found.getStatus()))
        .orElseThrow(() -> new NotFoundException("comment " + commentId));
    if (c.getAuthorId() == null || c.getAuthorId() != userId) {
      throw new ForbiddenException("작성자만 수정할 수 있습니다");
    }
    revisions.record("COMMENT", commentId, null, c.getBodyMd(), c.getBodyHtml(), userId);
    c.setBodyMd(req.bodyMd());
    comments.save(c);
    return toView(c);
  }

  @Transactional
  public void delete(long userId, long commentId) {
    CommunityComment c = comments.findByIdForUpdate(commentId)
        .filter(found -> ContentStatus.PUBLISHED.equals(found.getStatus()))
        .orElseThrow(() -> new NotFoundException("comment " + commentId));
    if (c.getAuthorId() == null || c.getAuthorId() != userId) {
      throw new ForbiddenException("작성자만 삭제할 수 있습니다");
    }
    c.setStatus(ContentStatus.DELETED);
    comments.save(c);
  }
}
